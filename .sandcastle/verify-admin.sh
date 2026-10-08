#!/bin/sh
set -u
cd /home/agent/workspace/yuweiju-web-vue/yuweiju-admin
for step in install lint typecheck test build behavior; do
  case "$step" in
    install) command='timeout --signal=TERM --kill-after=10s 300 npm ci';;
    lint) command='npm run lint';;
    typecheck) command='npm run typecheck';;
    test) command='npm run test';;
    build) command='npm run build';;
    behavior) command='node --test scripts/refimg-scripts.check.cjs';;
  esac
  sh -c "$command" > "/review-evidence/$step.log" 2>&1
  status=$?
  printf '%s\t%s\t%s\n' "$step" "$status" "$command" >> /review-evidence/exit-status.tsv
  [ "$status" -eq 0 ] || exit "$status"
done
