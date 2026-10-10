#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
export PATH="/opt/java/openjdk/bin:/usr/share/maven/bin:$PATH"
run="$root/.scratch/issue10"
mkdir -p "$run/libs"
# Extract only libraries from the candidate's built jar, without Maven plugin downloads.
python3 - "$root" <<'PY'
import sys, zipfile, shutil
from datetime import datetime, timezone
from pathlib import Path
root=Path(sys.argv[1]); run=root/'.scratch/issue10'
# Retain prior evidence and remove stale success flags before a new probe, including startup failure.
previous=[run/name for name in ('scenario-results.json','check-result.json','owned-resources.json','probe.log') if (run/name).exists()]
if previous:
    archive=run/'history'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    archive.mkdir(parents=True)
    for source in previous:
        shutil.copy2(source,archive/source.name)
        source.unlink()
with zipfile.ZipFile(root/'yuweiju-backend/target/proj-boot-1.0-SNAPSHOT.jar') as jar:
    names=[]
    for entry in jar.namelist():
        if entry.startswith('BOOT-INF/lib/') and entry.endswith('.jar'):
            target=run/'libs'/Path(entry).name
            target.write_bytes(jar.read(entry)); names.append(str(target))
if not names:
    raise SystemExit('Issue10 requires the completed Spring Boot package with BOOT-INF/lib; wait for mvn package to finish.')
(run/'classpath.txt').write_text(':'.join(names))
PY
cp="$root/yuweiju-backend/target/classes:$(cat "$run/classpath.txt")"
javac -proc:none -cp "$cp" -d "$run" "$root/.sandcastle/environment/Issue10Probe.java"
# Output only sanitized assertion evidence, never login tokens or raw HTTP bodies.
started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
printf 'ISSUE10 CHECK_STARTED %s cwd=%s\n' "$started" "$root"
status=0
java -cp "$run:$cp" Issue10Probe "$root" > "$run/probe.log" 2>&1 || status=$?
sed -n '/^ISSUE10 /p' "$run/probe.log"
python3 - "$run" "$status" "$started" <<'PY2'
import json,sys
from pathlib import Path
from datetime import datetime, timezone
run=Path(sys.argv[1]); status=int(sys.argv[2])
result_path=run/'scenario-results.json'
result=json.loads(result_path.read_text()) if result_path.exists() else {}
passed=status==0 and result.get('businessPassed') is True and result.get('cleanupPassed') is True
(run/'check-result.json').write_text(json.dumps({'started':sys.argv[3], 'finished':datetime.now(timezone.utc).isoformat(), 'exit':status, 'businessPassed':result.get('businessPassed',False), 'cleanupPassed':result.get('cleanupPassed',False)},indent=2))
print('ISSUE10 CHECK_RESULT businessPassed=%s cleanupPassed=%s exit=%s' % (result.get('businessPassed',False),result.get('cleanupPassed',False),status))
if not passed: sys.exit(status or 1)
PY2
