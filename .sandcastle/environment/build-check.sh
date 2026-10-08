#!/bin/bash
set -u
cd /workspace/yuweiju-backend
record() { local name=$1; shift; "$@" >"/evidence/$name.log" 2>&1; local status=$?; printf '%s\t%s\n' "$name" "$status" >> /evidence/exit-status.tsv; return "$status"; }
record locale locale
record java-properties java -XshowSettings:properties -version
record java-version java -version
record maven-version mvn -version
record maven-package mvn -B package || exit $?
record dependency-classpath python3 /environment/extract-classpath.py || exit $?
CP="target/classes:$(cat /workspace/backend-classpath.txt)"
record probe-compile javac -cp "$CP" -d /workspace /environment/EnvironmentProbe.java || exit $?
cd /workspace/yuweiju-web-vue/yuweiju-admin
record npm-ci npm ci || exit $?
for name in lint typecheck test test:refimg; do record "admin-$name" npm run "$name" || exit $?; done
record admin-build env VITE_API_BASE=/api VITE_PROXY_TARGET=http://127.0.0.1:8080 npm run build || exit $?
