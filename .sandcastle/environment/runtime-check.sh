#!/bin/bash
set -u
record() { local name=$1; shift; "$@" >"/evidence/$name.log" 2>&1; local status=$?; printf '%s\t%s\n' "$name" "$status" >> /evidence/exit-status.tsv; return "$status"; }
cd /workspace/yuweiju-backend
CP="/workspace:target/classes:$(cat /workspace/backend-classpath.txt)"
record probe-runtime-compile javac -cp "$CP" -d /workspace /environment/EnvironmentProbe.java /environment/StorageProbe.java || exit $?
record local-http-fixtures env FIXTURE_JAVA_CLASSPATH="$CP" python3 /environment/fixture-check.py || exit $?
java -cp "$CP" EnvironmentProbe --serve > /evidence/spring-and-algorithms.log 2>&1 & backend_pid=$!
vite_pid=''
cleanup() { [ -z "$vite_pid" ] || kill "$vite_pid" 2>/dev/null; kill "$backend_pid" 2>/dev/null; wait "$backend_pid" 2>/dev/null; }
trap cleanup EXIT
for i in $(seq 1 120); do
 if grep -q READY_FOR_BROWSER /evidence/spring-and-algorithms.log; then break; fi
 if ! kill -0 "$backend_pid" 2>/dev/null; then wait "$backend_pid"; status=$?; printf 'spring-and-algorithms\t%s\n' "$status" >> /evidence/exit-status.tsv; exit "$status"; fi
 sleep 1
done
grep -q READY_FOR_BROWSER /evidence/spring-and-algorithms.log || { printf 'spring-and-algorithms\t124\n' >> /evidence/exit-status.tsv; exit 124; }
printf 'spring-and-algorithms\t0\n' >> /evidence/exit-status.tsv
cd /workspace/yuweiju-web-vue/yuweiju-admin
record admin-runtime-build env VITE_API_BASE=/api VITE_PROXY_TARGET=http://127.0.0.1:8080 npm run build || exit $?
npm run preview -- --host 127.0.0.1 --port 5173 > /evidence/admin-preview.log 2>&1 & vite_pid=$!
for i in $(seq 1 30); do curl -fsS http://127.0.0.1:5173/ >/dev/null && break; sleep 1; done
record admin-browser node /environment/browser-check.cjs
