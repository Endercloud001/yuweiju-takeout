#!/bin/bash
set -u
record() { local name=$1; shift; "$@" >"/evidence/$name.log" 2>&1; local status=$?; printf '%s\t%s\n' "$name" "$status" >> /evidence/exit-status.tsv; return "$status"; }
cd /workspace/yuweiju-backend
CP="/workspace:target/classes:$(cat /workspace/backend-classpath.txt)"
record training-probe-compile javac -cp "$CP" -d /workspace /environment/EnvironmentProbe.java /environment/TrainingProbe.java || exit $?
record synthetic-business-training env OMP_NUM_THREADS=2 java -XX:ActiveProcessorCount=2 -cp "$CP" TrainingProbe
