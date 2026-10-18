#!/usr/bin/env bash
# Self-contained exact-candidate check; task-owned internal MySQL/Redis only.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
mkdir -p "$root/.scratch/issue55"
run=$(mktemp -d "$root/.scratch/issue55/sql-XXXXXXXX")
mkdir -p "$run/libs" "$run/classes" "$run/tmp"
export TMPDIR="$run/tmp"
# Preserve every batch in a fresh project-local directory; no stale success flags.
python3 - "$root" "$run" <<'PY'
import json,sys
from pathlib import Path
from datetime import datetime,timezone
root,run=map(Path,sys.argv[1:])
s=(root/'.sandcastle/environment/application-afk.yml').read_text()
assert 'jdbc:mysql://mysql:3306/sandcastle_fixture?' in s and 'host: redis' in s
assert 'import:' not in s and '${' not in s
assert 'scoring-enabled: false' in s and 'snapshot-cron: "-"' in s and 'training-cron: "-"' in s
(run/'commands.jsonl').write_text(json.dumps({'started':datetime.now(timezone.utc).isoformat(),'cwd':str(root)})+'\n')
PY
record() {
  local label=$1
  shift
  local status=0
  "$@" > "$run/$label.log" 2>&1 || status=$?
  python3 - "$run" "$label" "$status" "$@" <<'PY'
import json,sys
from pathlib import Path
from datetime import datetime,timezone
run=Path(sys.argv[1]); label=sys.argv[2]; status=int(sys.argv[3])
with (run/'commands.jsonl').open('a') as f:
 f.write(json.dumps({'finished':datetime.now(timezone.utc).isoformat(),'cwd':str(Path.cwd()),'command':sys.argv[4:],'exit':status,'log':label+'.log'})+'\n')
print('ISSUE55 CHECK '+label+' exit='+str(status))
PY
  return "$status"
}
command -v java
command -v mvn
# Wait for package success before reading the jar or constructing a classpath.
record package mvn -B -f yuweiju-backend/pom.xml package
python3 - "$root" "$run" <<'PY'
import sys,zipfile
from pathlib import Path
root,run=map(Path,sys.argv[1:]); names=[]
with zipfile.ZipFile(root/'yuweiju-backend/target/proj-boot-1.0-SNAPSHOT.jar') as jar:
 for entry in jar.namelist():
  if entry.startswith('BOOT-INF/lib/') and entry.endswith('.jar'):
   target=run/'libs'/Path(entry).name; target.write_bytes(jar.read(entry)); names.append(str(target))
assert names, 'Completed Boot package with dependencies required'
(run/'classpath.txt').write_text(':'.join(names))
print('ISSUE55 Boot libraries extracted count='+str(len(names)))
PY
cp="$root/yuweiju-backend/target/classes:$(cat "$run/classpath.txt")"
record javac javac -proc:none -cp "$cp" -d "$run/classes" "$root/.sandcastle/environment/Issue55Probe.java"
status=0
record probe java -Duser.timezone=Asia/Shanghai -Djava.io.tmpdir="$run/tmp" -cp "$run/classes:$cp" Issue55Probe "$root" "$run" || status=$?
sed -n '/^ISSUE55 /p' "$run/probe.log"
python3 - "$run" "$status" <<'PY'
import json,sys
from pathlib import Path
run=Path(sys.argv[1]); status=int(sys.argv[2]); p=run/'scenario-results.json'
r=json.loads(p.read_text()) if p.exists() else {}
passed=status==0 and all(r.get(k) is True for k in ('businessPassed','cleanupPassed','springClosed','httpClientClosed'))
(run/'check-result.json').write_text(json.dumps({'exit':status,'passed':passed,'run':str(run)},indent=2)+'\n')
print('ISSUE55 CHECK_RESULT passed='+str(passed)+' evidence='+str(run))
if not passed: sys.exit(status or 1)
PY
