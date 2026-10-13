#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
command -v java
command -v mvn
# Non-login prepared PATH; use only the completed candidate jar, no personal profile imports.
run="$root/.scratch/issue16"
mkdir -p "$run/libs" "$run/classes"
started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
probe_pid=''
stop_probe() {
    if [ -n "$probe_pid" ] && kill -0 "$probe_pid" 2>/dev/null; then
        kill -TERM "$probe_pid"
        wait "$probe_pid" || true
    fi
}
finish_check() {
    status=$?
    trap - EXIT
    stop_probe
    python3 - "$run" "$status" "$started" <<'PY'
import json,sys
from pathlib import Path
from datetime import datetime, timezone
run=Path(sys.argv[1]); status=int(sys.argv[2])
path=run/'scenario-results.json'
result=json.loads(path.read_text()) if path.exists() else {}
passed=status==0 and result.get('businessPassed') is True and result.get('cleanupPassed') is True
exit_code=0 if passed else (status or 1)
(run/'check-result.json').write_text(json.dumps({'started':sys.argv[3], 'finished':datetime.now(timezone.utc).isoformat(), 'exit':exit_code, 'businessPassed':result.get('businessPassed',False), 'cleanupPassed':result.get('cleanupPassed',False)},indent=2))
print('ISSUE16 CHECK_RESULT businessPassed=%s cleanupPassed=%s exit=%s' % (result.get('businessPassed',False),result.get('cleanupPassed',False),exit_code))
sys.exit(exit_code)
PY
}
trap finish_check EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
printf 'ISSUE16 CHECK_STARTED %s cwd=%s\n' "$started" "$root"
python3 - "$root" <<'PY'
import sys,zipfile,shutil
from datetime import datetime,timezone
from pathlib import Path
root=Path(sys.argv[1]); run=root/'.scratch/issue16'
previous=[run/name for name in ('scenario-results.json','check-result.json','owned-resources.json','probe.log') if (run/name).exists()]
if previous:
    archive=run/'history'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    archive.mkdir(parents=True)
    for source in previous:
        shutil.copy2(source,archive/source.name)
        source.unlink()
(run/'scenario-results.json').write_text('{"businessPassed":false,"cleanupPassed":false,"failureClass":"ProbeNotCompleted","scenarios":[]}')
(run/'owned-resources.json').write_text('{"probeStarted":false,"externalServicesUsed":false}')
with zipfile.ZipFile(root/'yuweiju-backend/target/proj-boot-1.0-SNAPSHOT.jar') as jar:
    names=[]
    for entry in jar.namelist():
        if entry.startswith('BOOT-INF/lib/') and entry.endswith('.jar'):
            target=run/'libs'/Path(entry).name
            target.write_bytes(jar.read(entry)); names.append(str(target))
        elif entry.startswith('BOOT-INF/classes/') and not entry.endswith('/'):
            target=run/'classes'/entry.removeprefix('BOOT-INF/classes/')
            target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(jar.read(entry))
if not names:
    raise SystemExit('Issue16 requires the completed Spring Boot package with BOOT-INF/lib; run only after mvn package finishes.')
(run/'classpath.txt').write_text(':'.join(names))
PY
cp="$run/classes:$(cat "$run/classpath.txt")"
javac -proc:none -cp "$cp" -d "$run" "$root/.sandcastle/environment/Issue16Probe.java"
status=0
java -cp "$run:$cp" Issue16Probe "$root" > "$run/probe.log" 2>&1 &
probe_pid=$!
wait "$probe_pid" || status=$?
probe_pid=''
# Never print raw Spring/JDBC/HTTP exception output, tokens or conversation bodies.
sed -n '/^ISSUE16 /p' "$run/probe.log"
exit "$status"
