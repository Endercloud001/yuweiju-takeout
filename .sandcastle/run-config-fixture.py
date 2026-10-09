import json,os,subprocess,time
from pathlib import Path
from fixture_repo import create
runtime=Path(__file__).resolve().parent.parent;e=Path(os.environ.get('SANDCASTLE_EVIDENCE',str(runtime/'.scratch/config-fixture-evidence')));i=e/'config-fixture-input';i.mkdir(parents=True,exist_ok=True)
r=create(runtime,e/'fixtures')
(i/'probe.txt').write_text('Configured readonly input fixture.');prompt=e/'config-fixture-prompt.md';prompt.write_text('Configured no-model prompt fixture.')
candidate=subprocess.check_output(['git','-C',str(r),'rev-parse','HEAD'],text=True).strip()
c={'branch':'codex/afk-config-fixture-'+str(time.time_ns()),'startCommit':candidate,'promptFile':str(prompt),'inputDirectory':str(i),'image':'sandcastle:yuweiju-dev','authDirectory':'/home/endercloud/projects/yuweiju-afk-auth','maxIterations':2,'installCommands':[],'checkCommands':['test "$LANG" = C.UTF-8 && test "$LC_ALL" = C.UTF-8 && java -XshowSettings:properties -version 2>&1 | grep "native.encoding = UTF-8"','java -version','mvn -version','node --version','test -f docs/standards/backend.md'],'progressFile':'.sandcastle/config-fixture-progress.md'}
f=e/'config-fixture.json';f.write_text(json.dumps(c,indent=2));env=dict(os.environ,SANDCASTLE_TASK_CONFIG=str(f),TMPDIR=str(e/'tmp'))
(e/'tmp').mkdir(exist_ok=True)
c.update(image='sandcastle:yuweiju-dev-git-safe',evidencePaths=['.sandcastle/config-fixture-progress.md','.scratch/check-evidence'])
f.write_text(json.dumps(c,indent=2),encoding='utf-8',newline='\n')
with (e/'config-fixture.log').open('w') as out:result=subprocess.run(['node','--import',str(r/'node_modules/tsx/dist/loader.mjs'),str(r/'.sandcastle/config-fixture.mts')],env=env,cwd=r,stdout=out,stderr=subprocess.STDOUT)
print('configured-no-model-fixture exit:',result.returncode)
if result.returncode:raise SystemExit(result.returncode)
result=subprocess.run(['python3',str(r/'.sandcastle/verify-task.py'),'--config',str(f),'--commit',candidate],cwd=r)
if result.returncode:raise SystemExit(result.returncode)
# Synthetic snapshot only checks the mount/locale runner; it does not claim business acceptance.
snapshot=e/('environment-mount-fixture-'+str(time.time_ns()));environment=snapshot/'.sandcastle/environment';environment.mkdir(parents=True)
(environment/'fixture-marker.txt').write_text('candidate environment fixture')
(environment/'runtime-check.sh').write_text('set -eu\ntest "$(cat /environment/fixture-marker.txt)" = "candidate environment fixture"\ntest "$LANG" = C.UTF-8\ntest "$LC_ALL" = C.UTF-8\njava -XshowSettings:properties -version 2>&1 | grep "native.encoding = UTF-8"\nprintf "candidate environment mount fixture passed\\n"\n')
result=subprocess.run(['python3',str(r/'.sandcastle/environment/verify.py'),'--commit',candidate,'--runtime-only',str(snapshot),'--network','none'],cwd=r)
raise SystemExit(result.returncode)
