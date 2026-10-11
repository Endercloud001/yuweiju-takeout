#!/bin/bash
# Isolated acceptance after actual Maven packaging/classpath extraction.
set -euo pipefail
WORKSPACE=${1:-/workspace}
ENVIRONMENT=${2:-/workspace/.sandcastle/environment}
cd "$WORKSPACE/yuweiju-backend"
printf 'Issue11 check cwd=%s\n' "$PWD"
command -v java
command -v mvn
python3 - "$ENVIRONMENT/application-afk.yml" <<'PY'
import sys
from pathlib import Path
s=Path(sys.argv[1]).read_text()
assert 'jdbc:mysql://mysql:3306/sandcastle_fixture?' in s
assert 'host: redis' in s and 'import:' not in s and '${' not in s
assert 'scoring-enabled: false' in s and 'enabled: false' in s
PY
CP="target/classes:$(cat "$WORKSPACE/backend-classpath.txt")"
mkdir -p "$WORKSPACE/.scratch/issue11"
PROBE_DIR=$(mktemp -d "$WORKSPACE/.scratch/issue11/probe-XXXXXX")
trap 'rm -rf "$PROBE_DIR"' EXIT
javac -cp "$CP" -d "$PROBE_DIR" "$ENVIRONMENT/Issue11Probe.java"
java -Duser.timezone=Asia/Shanghai -cp "$PROBE_DIR:$CP" Issue11Probe "$ENVIRONMENT/application-afk.yml"
