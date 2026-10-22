#!/usr/bin/env bash
# Fires N requests at /api/hello with concurrency C using Apache Bench.
#
# `ab` does not persist cookies across requests, so it never sends back the
# JSESSIONID the server issued on the previous request. That means Tomcat
# creates a brand-new HttpSession for every single request -- which is
# exactly the failure mode being reproduced: thousands of short-lived
# sessions, each holding a SecurityContext full of GrantedAuthority objects,
# pile up in the Tomcat session manager until they expire (server.servlet.
# session.timeout), driving heap usage up independently of request rate.
#
# Usage: ./loadtest.sh [num_requests] [concurrency] [host]
set -euo pipefail

N="${1:-5000}"
C="${2:-50}"
HOST="${3:-http://localhost:8080}"

echo "Sending $N requests ($C concurrent) to $HOST/api/hello ..."
ab -n "$N" -c "$C" -H "X-User-Id: loadtest" "$HOST/api/hello"
