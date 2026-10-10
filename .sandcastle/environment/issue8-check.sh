#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
export PATH="/opt/java/openjdk/bin:/usr/share/maven/bin:$PATH"
run="$root/.scratch/issue8"
mkdir -p "$run/libs"
# Extract only libraries from the candidate's built jar, without Maven plugin downloads.
python3 - "$root" <<'PY'
import sys, zipfile
from pathlib import Path
root=Path(sys.argv[1]); run=root/'.scratch/issue8'
with zipfile.ZipFile(root/'yuweiju-backend/target/proj-boot-1.0-SNAPSHOT.jar') as jar:
    names=[]
    for entry in jar.namelist():
        if entry.startswith('BOOT-INF/lib/') and entry.endswith('.jar'):
            target=run/'libs'/Path(entry).name
            target.write_bytes(jar.read(entry)); names.append(str(target))
(run/'classpath.txt').write_text(':'.join(names))
PY
cp="$root/yuweiju-backend/target/classes:$(cat "$run/classpath.txt")"
javac -cp "$cp" -d "$run" "$root/.sandcastle/environment/Issue8Probe.java"
if [[ "${1:-}" != "--browser" ]]; then
  java -cp "$run:$cp" Issue8Probe "$root" > "$run/probe.log" 2>&1
  sed -n '/^ISSUE8 /p' "$run/probe.log"
  exit 0
fi
# Local loopback only, no Docker/host publication or original credentials.
rm -f "$run/browser-done"
java -cp "$run:$cp" Issue8Probe "$root" --serve > "$run/probe.log" 2>&1 & probe_pid=$!
vite_pid=''
cleanup() {
  touch "$run/browser-done"
  [[ -z "$vite_pid" ]] || kill "$vite_pid" 2>/dev/null || true
  if kill -0 "$probe_pid" 2>/dev/null; then
    for attempt in $(seq 1 60); do kill -0 "$probe_pid" 2>/dev/null || break; sleep 0.5; done
    kill "$probe_pid" 2>/dev/null || true
  fi
  wait "$probe_pid" 2>/dev/null || true
}
trap cleanup EXIT
for attempt in $(seq 1 120); do
  if rg -q 'ISSUE8 READY_FOR_BROWSER' "$run/probe.log"; then break; fi
  kill -0 "$probe_pid" 2>/dev/null || { wait "$probe_pid"; exit 1; }
  sleep 1
done
rg -q 'ISSUE8 READY_FOR_BROWSER' "$run/probe.log"
cd "$root/yuweiju-web-vue/yuweiju-admin"
VITE_API_BASE=/api VITE_PROXY_TARGET=http://127.0.0.1:18087 npm run dev -- --host 127.0.0.1 --port 18088 --strictPort > "$run/vite.log" 2>&1 & vite_pid=$!
for attempt in $(seq 1 30); do curl -fsS http://127.0.0.1:18088/ >/dev/null 2>&1 && break; sleep 1; done
node "$root/.sandcastle/environment/issue8-browser.cjs" > "$run/browser.log" 2>&1
touch "$run/browser-done"
wait "$probe_pid"
sed -n '/^ISSUE8 /p' "$run/probe.log" "$run/browser.log"
