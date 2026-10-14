#!/usr/bin/env bash
set -euo pipefail
root=$(cd "${1:-$(dirname "$0")/../..}" && pwd)
environment=$(cd "${2:-$root/.sandcastle/environment}" && pwd)
cd "$root"
command -v java
command -v mvn
export OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=2 MKL_NUM_THREADS=2
export MAVEN_OPTS="${MAVEN_OPTS:-} -XX:ActiveProcessorCount=2 -DargLine=-XX:ActiveProcessorCount=2"
# Every run owns a fresh scratch/model directory and a fresh empty internal Redis DB.
mkdir -p "$root/.scratch/issue18"
run=$(mktemp -d "$root/.scratch/issue18/run-XXXXXXXX")
mkdir -p "$run/libs" "$run/classes" "$run/tmp" "$run/native-cache"

started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
printf 'ISSUE18 CHECK_BATCH started=%s cwd=%s command=bash_issue18-check root=%s\n' "$started" "$PWD" "$root"
finish() {
    status=$?
    trap - EXIT
    python3 - "$run" "$status" "$started" <<'PY'
import json,sys
from pathlib import Path
from datetime import datetime,timezone
run=Path(sys.argv[1]);status=int(sys.argv[2]);path=run/'scenario-results.json'
result=json.loads(path.read_text()) if path.exists() else {}
passed=status==0 and result.get('businessPassed') is True and result.get('cleanupPassed') is True
exit_code=0 if passed else status or 1
(run/'check-result.json').write_text(json.dumps({'started':sys.argv[3],'finished':datetime.now(timezone.utc).isoformat(),'exit':exit_code,'businessPassed':result.get('businessPassed',False),'cleanupPassed':result.get('cleanupPassed',False),'isolatedSchema':result.get('isolatedSchema'),'training':result.get('training'),'syntheticEffectivenessOnly':True},indent=2))
print('ISSUE18 CHECK_RESULT businessPassed=%s cleanupPassed=%s exit=%s evidence=%s' % (result.get('businessPassed',False),result.get('cleanupPassed',False),exit_code,run))
sys.exit(exit_code)
PY
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
# Issue10 technique: libraries come exclusively from the successfully packaged candidate jar.
# Repackage here as well so this standalone script cannot accidentally test an older failed build.
status=0
mvn -B -f "$root/yuweiju-backend/pom.xml" compile > "$run/compile.log" 2>&1 || status=$?
if [ "$status" -ne 0 ]; then tail -40 "$run/compile.log"; exit "$status"; fi
mvn -B "-DargLine=-XX:ActiveProcessorCount=2 -Dorg.bytedeco.javacpp.cachedir=$run/native-cache" -f "$root/yuweiju-backend/pom.xml" package > "$run/package.log" 2>&1 || status=$?
printf 'ISSUE18 CHECK_BATCH package command=mvn_-B_package cwd=%s exit=%s\n' "$root" "$status"
if [ "$status" -ne 0 ]; then sed -n '/^\[ERROR\]/p' "$run/package.log"; exit "$status"; fi
python3 - "$root" "$run" <<'PY'
import sys,zipfile
from pathlib import Path
root=Path(sys.argv[1]);run=Path(sys.argv[2]);names=[]
with zipfile.ZipFile(root/'yuweiju-backend/target/proj-boot-1.0-SNAPSHOT.jar') as jar:
    for entry in jar.namelist():
        if entry.startswith('BOOT-INF/lib/') and entry.endswith('.jar'):
            target=run/'libs'/Path(entry).name;target.write_bytes(jar.read(entry));names.append(str(target))
        elif entry.startswith('BOOT-INF/classes/') and not entry.endswith('/'):
            target=run/'classes'/entry.removeprefix('BOOT-INF/classes/')
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(jar.read(entry))
if not names: raise SystemExit('Issue18 requires a successful Spring Boot package with BOOT-INF/lib')
(run/'classpath.txt').write_text(':'.join(names))
PY
cp="$run/classes:$(cat "$run/classpath.txt")"
javac -J-XX:ActiveProcessorCount=2 -J-Djava.io.tmpdir="$run/tmp" -proc:none -cp "$cp" -d "$run" "$environment/Issue18Probe.java" > "$run/probe-compile.log" 2>&1 || { cat "$run/probe-compile.log"; exit 1; }
export OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=2 MKL_NUM_THREADS=2
# Filter native/Spring output at capture time. Never retain raw dependency messages or tokens.
status=0
java -Dorg.bytedeco.javacpp.cachedir="$run/native-cache" -Duser.timezone=Asia/Shanghai -Djava.io.tmpdir="$run/tmp" -XX:ActiveProcessorCount=2 -cp "$run:$cp" Issue18Probe "$root" "$environment" "$run" 2>&1 | sed -n '/^ISSUE18 /p' > "$run/probe.log" || status=$?
cat "$run/probe.log"
exit "$status"
