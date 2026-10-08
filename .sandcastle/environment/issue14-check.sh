#!/bin/bash
# Runs in the host-owned internal Compose network, after package/classpath extraction.
set -euo pipefail
WORKSPACE=${1:-/workspace}
ENVIRONMENT=${2:-/environment}
cd "$WORKSPACE/yuweiju-backend"
# Standalone config replaces personal config; Java additionally fixes MySQL/Redis to internal names.
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
PROBE_DIR=$(mktemp -d)
trap 'rm -rf "$PROBE_DIR"' EXIT
javac -cp "$CP" -d "$PROBE_DIR" "$WORKSPACE/.sandcastle/environment/Issue14Probe.java"
# No EnvironmentProbe training, original endpoints, or model/auth external calls.
java -Duser.timezone=Asia/Shanghai -cp "$PROBE_DIR:$CP" Issue14Probe "$ENVIRONMENT/application-afk.yml"
