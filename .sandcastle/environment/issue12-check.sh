#!/bin/bash
# Task-only internal acceptance after packaging and extraction from the actual jar.
set -euo pipefail
WORKSPACE=${1:-/workspace}
ENVIRONMENT=${2:-/workspace/.sandcastle/environment}
cd "$WORKSPACE/yuweiju-backend"
printf 'Issue12 check cwd=%s\n' "$PWD"
python3 - "$ENVIRONMENT/application-afk.yml" <<'PY'
import sys
from pathlib import Path
s = Path(sys.argv[1]).read_text()
assert 'jdbc:mysql://mysql:3306/sandcastle_fixture?' in s, 'internal MySQL config required'
assert 'host: redis' in s, 'internal Redis config required'
assert 'import:' not in s and '${' not in s, 'standalone literal isolated config required'
assert 'scoring-enabled: false' in s and 'enabled: false' in s, 'background work must be disabled'
PY
CP="$WORKSPACE:target/classes:$(cat "$WORKSPACE/backend-classpath.txt")"
mkdir -p "$WORKSPACE/.scratch/issue12"
PROBE_DIR=$(mktemp -d "$WORKSPACE/.scratch/issue12/probe-XXXXXX")
trap 'rm -rf "$PROBE_DIR"' EXIT
javac -cp "$CP" -d "$PROBE_DIR" "$WORKSPACE/.sandcastle/environment/Issue12Probe.java"
java -Duser.timezone=Asia/Shanghai -cp "$PROBE_DIR:$CP" Issue12Probe "$ENVIRONMENT/application-afk.yml"
