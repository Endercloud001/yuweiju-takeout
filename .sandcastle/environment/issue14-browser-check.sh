#!/bin/bash
set -euo pipefail
cd /workspace/yuweiju-backend
CP="/workspace:target/classes:$(cat /workspace/backend-classpath.txt)"
mkdir -p /evidence/browser-classes
javac -cp "$CP" -d /evidence/browser-classes /environment/Issue14BrowserServer.java
java -Duser.timezone=Asia/Shanghai -cp "/evidence/browser-classes:$CP" Issue14BrowserServer > /evidence/browser-backend.log 2>&1 &
backend_pid=$!
vite_pid=''
cleanup() {
    [ -z "$vite_pid" ] || kill "$vite_pid" 2>/dev/null || true
    kill "$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
}
trap cleanup EXIT
for i in $(seq 1 60); do
    grep -q 'ISSUE14 READY_FOR_BROWSER' /evidence/browser-backend.log && break
    kill -0 "$backend_pid" 2>/dev/null || { cat /evidence/browser-backend.log; exit 1; }
    sleep 1
done
grep -q 'ISSUE14 READY_FOR_BROWSER' /evidence/browser-backend.log
cd /workspace/yuweiju-web-vue/yuweiju-admin
VITE_API_BASE=/api VITE_PROXY_TARGET=http://127.0.0.1:8080 npm run build > /evidence/browser-admin-build.log 2>&1
npm run preview -- --host 127.0.0.1 --port 5173 > /evidence/browser-preview.log 2>&1 &
vite_pid=$!
for i in $(seq 1 30); do curl -fsS http://127.0.0.1:5173/ >/dev/null && break; sleep 1; done
node /environment/issue14-browser.cjs
