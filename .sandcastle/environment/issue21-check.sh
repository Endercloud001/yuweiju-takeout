#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
command -v java
command -v mvn
mkdir -p "$root/.scratch/issue21"
run=$(mktemp -d "$root/.scratch/issue21/joint-XXXXXXXX")
mkdir -p "$run/libs" "$run/classes" "$run/tmp"
started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
version=${SANDCASTLE_CANDIDATE_COMMIT:-}
version_source=host-provided
dirty=unknown
observed_git_commit=''
# Independent snapshots can have a .git pointer to unmounted host metadata.
# A host-supplied candidate is provenance metadata; unavailable Git never means clean.
if observed_git_commit=$(git rev-parse HEAD 2>/dev/null); then
    if [[ -z "$version" ]]; then version=$observed_git_commit; version_source=worktree-git; fi
    if status=$(git status --porcelain -- .sandcastle/environment yuweiju-backend/src yuweiju-web-vue/yuweiju-admin/src yuweiju-weixin-miniapp/pages yuweiju-weixin-miniapp/checkout-source/src yuweiju-weixin-miniapp/history-source/src 2>/dev/null); then
        dirty=clean
        [[ -z "$status" ]] || dirty=modified
    fi
fi
if [[ -z "$version" ]]; then
    printf 'ISSUE21 candidate version unavailable; host must supply SANDCASTLE_CANDIDATE_COMMIT\n' >&2
    exit 1
fi
printf 'ISSUE21 CHECK_BATCH started=%s cwd=%s version=%s\n' "$started" "$PWD" "$version"
finish() {
    status=$?
    trap - EXIT
    python3 - "$run" "$status" "$started" "$version" "$root" "$dirty" "$version_source" "$observed_git_commit" <<'PY'
import json,sys
from pathlib import Path
from datetime import datetime,timezone
run=Path(sys.argv[1]); status=int(sys.argv[2]); path=run/'scenario-results.json'
result=json.loads(path.read_text()) if path.exists() else {}
passed=status==0 and result.get('businessPassed') is True and result.get('cleanupPassed') is True and bool(result.get('scenarios')) and all(s.get('passed') is True for s in result['scenarios'])
exit_code=0 if passed else status or 1
record={'started':sys.argv[3],'finished':datetime.now(timezone.utc).isoformat(),'exit':exit_code,'cwd':sys.argv[5],'sourceCommit':sys.argv[4],'businessPassed':result.get('businessPassed',False),'cleanupPassed':result.get('cleanupPassed',False),'scenarios':result.get('scenarios',[]),'resources':result.get('resources',{}),'sourceDirty':{'unknown':None,'clean':False,'modified':True}[sys.argv[6]],'versionSource':sys.argv[7],'observedGitCommit':sys.argv[8] or None,'versionMeaning':'host candidate metadata or worktree HEAD plus reported dirtiness; exact-commit verification bound to host resources'}
(run/'check-result.json').write_text(json.dumps(record,indent=2,ensure_ascii=False))
print('ISSUE21 CHECK_RESULT businessPassed=%s cleanupPassed=%s exit=%s evidence=%s' % (record['businessPassed'],record['cleanupPassed'],exit_code,run))
sys.exit(exit_code)
PY
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
# Extract both code and libraries from the completed candidate package, using the current root.
python3 - "$root" "$run" <<'PY'
import sys,zipfile
from pathlib import Path
root=Path(sys.argv[1]);run=Path(sys.argv[2]);libs=[]
with zipfile.ZipFile(root/'yuweiju-backend/target/proj-boot-1.0-SNAPSHOT.jar') as jar:
    for entry in jar.namelist():
        if entry.startswith('BOOT-INF/lib/') and entry.endswith('.jar'):
            target=run/'libs'/Path(entry).name;target.write_bytes(jar.read(entry));libs.append(str(target))
        elif entry.startswith('BOOT-INF/classes/') and not entry.endswith('/'):
            target=run/'classes'/entry.removeprefix('BOOT-INF/classes/')
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(jar.read(entry))
if not libs: raise SystemExit('A successfully completed mvn package is required')
(run/'classpath.txt').write_text(':'.join(libs))
PY
cp="$run/classes:$(cat "$run/classpath.txt")"
javac -J-XX:ActiveProcessorCount=2 -proc:none -cp "$cp" -d "$run" "$root/.sandcastle/environment/Issue21Probe.java" > "$run/probe-compile.log" 2>&1
# Capture assertion labels only: no passwords, tokens, raw response bodies or dependency messages.
java -XX:ActiveProcessorCount=2 -Duser.timezone=Asia/Shanghai -Djava.io.tmpdir="$run/tmp" -cp "$run:$cp" Issue21Probe "$root" "$run" 2>&1 | sed -n '/^ISSUE21 /p' > "$run/probe.log"
cat "$run/probe.log"
