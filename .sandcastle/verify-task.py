"""Run explicitly configured independent checks, never mounting Codex authentication."""
import argparse,json,os,signal,subprocess,time,re
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--config',type=Path,required=True);p.add_argument('--commit',required=True);args=p.parse_args()
r=Path(__file__).resolve().parent.parent;c=json.loads(args.config.read_text(encoding='utf-8-sig'));e=Path(os.environ.get('SANDCASTLE_EVIDENCE',str(r/'.scratch/sandcastle-evidence'))).resolve()
marker=r/'.git'
if marker.is_dir():metadata=marker.resolve()
else:
 target=marker.read_text(encoding='utf-8').strip().removeprefix('gitdir: ')
 if os.name!='nt' and re.match(r'^[A-Za-z]:[/\\]',target):target='/mnt/'+target[0].lower()+'/'+target[3:].replace('\\','/')
 metadata=(r/target).resolve()
git_env=dict(os.environ,GIT_DIR=str(metadata),GIT_WORK_TREE=str(r))
commit=subprocess.check_output(['git','rev-parse','--verify','--end-of-options',args.commit+'^{commit}'],env=git_env,text=True).strip()
run=e/('task-review-'+str(time.time_ns()));run.mkdir(parents=True);snapshot=run/'snapshot'
subprocess.run(['git','worktree','add','--detach',str(snapshot),commit],env=git_env,check=True)
commands=c['checkCommands'];assert isinstance(commands,list) and commands and all(isinstance(x,str) for x in commands)
script=run/'check.sh';script.write_text('set -u\ncd /workspace\n(locale; command -v java; java -XshowSettings:properties -version 2>&1 | sed -n \"/java.home =/p;/java.version =/p;/native.encoding =/p;/sun.jnu.encoding =/p\") > /evidence/environment.log 2>&1\n'+''.join(f"bash -c {__import__('shlex').quote(cmd)} > /evidence/check-{i}.log 2>&1\nstatus=$?\nprintf '{i}\\t%s\\n' \"$status\" >> /evidence/exit-status.tsv\n[ \"$status\" = 0 ] || exit \"$status\"\n" for i,cmd in enumerate(commands)))
name='sandcastle-task-review-'+str(time.time_ns());record={'repoRoot':str(r),'gitDirectory':str(metadata),'commit':commit,'distribution':os.environ.get('WSL_DISTRO_NAME'),'container':name,'snapshot':str(snapshot),'authMounted':False,'modelCalled':False,'commands':commands,'image':c['image'],'locale':{'LANG':'C.UTF-8','LC_ALL':'C.UTF-8'},'environmentLog':str(run/'environment.log')}
def stop(sig,frame):raise KeyboardInterrupt()
signal.signal(signal.SIGTERM,stop)
try:
 with (run/'runner.log').open('w') as out:
  process=subprocess.Popen(['docker','run','--rm','--name',name,*sum((['--network',n] for n in c.get('networks',[])),[]),'--user','1000:1000','-e','LANG=C.UTF-8','-e','LC_ALL=C.UTF-8','--entrypoint','bash','--mount',f'type=bind,src={snapshot},dst=/workspace','--mount',f'type=bind,src={run},dst=/evidence',c['image'],'/evidence/check.sh'],stdout=out,stderr=subprocess.STDOUT)
  try:record['exitCode']=process.wait(timeout=c.get('totalMs',1800000)/1000)
  except subprocess.TimeoutExpired:record['exitCode']=124
except KeyboardInterrupt:record['exitCode']=130
finally:
 observed=subprocess.run(['docker','inspect',name],capture_output=True)
 if observed.returncode==0:subprocess.run(['docker','stop','--time','10',name],capture_output=True)
 observed=subprocess.run(['docker','inspect',name],capture_output=True);record['stopped']=observed.returncode!=0 and b'no such' in observed.stderr.lower()
 (run/'resources.json').write_text(json.dumps(record,indent=2))
print(json.dumps(record));raise SystemExit(record['exitCode'] if record['stopped'] else 1)
