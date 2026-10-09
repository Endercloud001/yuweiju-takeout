#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT"
EVIDENCE="$ROOT/.scratch/issue13-check"
mkdir -p "$EVIDENCE/libs" "$EVIDENCE/classes" "$EVIDENCE/models" "$EVIDENCE/uploads"
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys,zipfile
root=Path(sys.argv[1]); out=root/'.scratch/issue13-check/libs'
with zipfile.ZipFile(root/'yuweiju-backend/target/proj-boot-1.0-SNAPSHOT.jar') as jar:
    for name in jar.namelist():
        if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
            (out/Path(name).name).write_bytes(jar.read(name))
PY
CP="$ROOT/yuweiju-backend/target/classes:$EVIDENCE/libs/*:$EVIDENCE/classes"
javac -cp "$CP" -d "$EVIDENCE/classes" "$ROOT/.sandcastle/environment/Issue13Probe.java"
java -cp "$CP" Issue13Probe "$ROOT"
