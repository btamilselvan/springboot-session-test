# Testing guide: IF_REQUIRED vs STATELESS

Step-by-step instructions to reproduce the session leak under
`SessionCreationPolicy.IF_REQUIRED`, confirm it's gone under `STATELESS`,
and inspect the heap directly to prove what's actually retained in each
case.

All commands assume you're in the project root and the app isn't already
running (`pkill -f "spring-boot:run"` if unsure).

## 0. One-time setup

Requires `ab` (Apache Bench, preinstalled on macOS) and `jcmd`/`jps` (part
of any JDK install, already on your `PATH` alongside `java`).

```
mvn -q compile
```

## 1. Test IF_REQUIRED (the leak)

1. Set the policy in `src/main/resources/application.yml`:

   ```yaml
   loadtest:
     session-creation-policy: IF_REQUIRED
   ```

2. Start the app:

   ```
   mvn -q spring-boot:run > /tmp/app.log 2>&1 &
   ```

3. Wait until it's up:

   ```
   until curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health | grep -q 200; do sleep 1; done
   ```

4. Sanity-check a single request — look for `Set-Cookie: JSESSIONID=...`
   and a non-null `sessionId` in the body:

   ```
   curl -s -i http://localhost:8080/api/hello -H "X-User-Id: tamil"
   ```

5. Capture baseline metrics (see [actuator metrics](#actuator-metrics-cheat-sheet) below).

6. Run the load test:

   ```
   ./loadtest/loadtest.sh 3000 50
   ```

7. Re-check metrics — `tomcat.sessions.active.current` should now be
   ~3000 and `jvm.memory.used` should have grown by roughly
   `requests × authority-count × authority-payload-bytes`
   (default: 3000 × 5 × 10KB ≈ 150MB).

8. Inspect the heap (see [heap inspection](#heap-inspection) below) and
   confirm `FatAuthority` / `UsernamePasswordAuthenticationToken` /
   `StandardSession` counts are all ≈ number of requests sent (or
   `requests × authority-count` for `FatAuthority` specifically).

9. Stop the app:

   ```
   pkill -f "spring-boot:run"
   ```

## 2. Test STATELESS (the fix)

1. Change the policy:

   ```yaml
   loadtest:
     session-creation-policy: STATELESS
   ```

2. Repeat steps 2–7 above unchanged.

3. Sanity-check a single request — there should be **no** `Set-Cookie`
   header and `sessionId` in the body should be `null`.

4. After the load test, `tomcat.sessions.active.current` /
   `tomcat.sessions.created` should stay at **0**, and `jvm.memory.used`
   should barely move (normal GC noise only, no multi-hundred-MB jump).

5. Heap histogram should show **zero** live `FatAuthority`,
   `UsernamePasswordAuthenticationToken`, and `StandardSession`
   instances after a forced GC.

6. Stop the app:

   ```
   pkill -f "spring-boot:run"
   ```

## Actuator metrics cheat sheet

```
# active / created Tomcat sessions
curl -s http://localhost:8080/actuator/metrics/tomcat.sessions.active.current
curl -s http://localhost:8080/actuator/metrics/tomcat.sessions.created

# heap used (filter to the heap area only)
curl -s "http://localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap"
```

Run these once before the load test and once after — the delta is what
to compare between the two policies.

## Heap inspection

### Quick: live object histogram (no file written)

Forces a GC first so only *reachable* objects are counted — garbage
that hasn't been collected yet won't inflate the numbers.

```
PID=$(jps -l | grep SessionLeakDemoApplication | awk '{print $1}')
jcmd $PID GC.run
jcmd $PID GC.class_histogram | grep -E "FatAuthority|UsernamePasswordAuthenticationToken|StandardSession"
```

No matching lines in the output means zero live instances of that class.

### Full heap dump (for MAT / VisualVM)

Use this if you want to see the retention path (what's holding a
reference to what), not just counts:

```
PID=$(jps -l | grep SessionLeakDemoApplication | awk '{print $1}')
jcmd $PID GC.heap_dump /tmp/heap.hprof
```

Open `/tmp/heap.hprof` in Eclipse MAT or VisualVM, search for
`FatAuthority`, and check "Path to GC Roots" — under `IF_REQUIRED`
it resolves through `org.apache.catalina.session.StandardSession`'s
session map; under `STATELESS` you shouldn't find any live instances to
trace at all.

## Expected results summary

| Metric | IF_REQUIRED | STATELESS |
|---|---|---|
| `Set-Cookie` / session on response | yes, every request | no |
| `tomcat.sessions.created` after 3000 reqs | ~3000 | 0 |
| `tomcat.sessions.active.current` after load | ~3000 | 0 |
| `FatAuthority` live instances | ~3000 × `authority-count` | 0 |
| Heap growth | ~`requests × authority-count × authority-payload-bytes` | negligible |
