# springboot-session-test

Minimal Spring Boot app for reproducing a specific Tomcat/Spring Security
memory leak pattern under load:

> `SecurityContextHolder.clearContext()` runs at the end of every request
> (Spring Security does this for you), but the `SecurityContext` —
> including every `GrantedAuthority` on it — was already written into the
> `HttpSession` earlier in the request by `HttpSessionSecurityContextRepository`.
> If the client doesn't send the session cookie back on the next request
> (a new client, a load-test tool with no cookie jar, a broken proxy, etc.),
> Tomcat creates a *new* session instead of reusing one. Each of those
> sessions — and every authority object hanging off it — sits in the
> Tomcat session manager's map until it times out. Clearing the
> thread-local context does nothing to free it.

## What's here

- `LoadTestAuthFilter` — authenticates every request unconditionally (no
  credential check, this isn't the point) and attaches `N` `FatAuthority`
  objects to the `Authentication`.
- `FatAuthority` — a `GrantedAuthority` with a configurable byte-array
  payload, standing in for a real app's bulkier permission/claim objects.
  Padding makes the leak visible in minutes instead of requiring millions
  of real requests.
- `SecurityConfig` — standard `authenticated()` on everything, default
  `SessionCreationPolicy.IF_REQUIRED`. Nothing exotic; this is Spring
  Security's out-of-the-box session behaviour.
- `HelloController` — `GET /api/hello`, echoes principal/session info.

## Running it

```
./mvnw spring-boot:run   # or: mvn spring-boot:run
```

## Reproducing the leak

```
./loadtest/loadtest.sh 5000 50     # 5000 requests, 50 concurrent
```

`ab` (Apache Bench) doesn't carry cookies across requests, so every
request looks like a brand-new client to Tomcat → a brand-new session →
a brand-new `SecurityContext` with its own `FatAuthority` instances
persisted into that session. None of it is reachable from
`SecurityContextHolder` after the request completes, but all of it is
reachable from the session manager until `server.servlet.session.timeout`
elapses.

### Watching it happen

```
# active/created/expired Tomcat sessions (needs server.tomcat.mbeanregistry.enabled=true, already set)
curl -s localhost:8080/actuator/metrics/tomcat.sessions.active.current
curl -s localhost:8080/actuator/metrics/tomcat.sessions.created

# heap in use
curl -s localhost:8080/actuator/metrics/jvm.memory.used
```

Run `loadtest.sh` again and watch `tomcat.sessions.active.current` and
`jvm.memory.used` climb together, and *stay* up until the session timeout
passes — even though no requests are in flight and
`SecurityContextHolder` is empty between requests.

For a heap dump to confirm what's retaining the memory:

```
jcmd <pid> GC.heap_dump /tmp/heap.hprof
```

Open it in VisualVM/Eclipse MAT and look at retained instances of
`FatAuthority` / `org.apache.catalina.session.StandardSession` — you'll
find them reachable from the Tomcat `Manager`'s session map, not from any
live thread.

## Knobs

All in `application.yml`:

- `loadtest.session-creation-policy` — `IF_REQUIRED` (default, realistic)
  vs `ALWAYS` (forces a session every request even without the cookie
  trick above).
- `loadtest.authority-count` / `loadtest.authority-payload-bytes` —
  control how much memory each session's `SecurityContext` costs, i.e.
  how fast the leak becomes visible.
- `server.servlet.session.timeout` — how long an abandoned session (and
  its authorities) survives before Tomcat reaps it.

## The actual fix(es), for when you're done reproducing it

- For stateless REST APIs, use `SessionCreationPolicy.STATELESS` and skip
  `HttpSessionSecurityContextRepository` entirely (e.g. JWT-based auth) —
  there's then nothing for the session manager to accumulate.
- If sessions are required, keep `GrantedAuthority` objects small/flyweight
  rather than carrying full claim payloads.
- Shorten `server.servlet.session.timeout` so abandoned sessions are
  reaped quickly.
- Make sure any client reusing the service actually honors the session
  cookie it's given.
