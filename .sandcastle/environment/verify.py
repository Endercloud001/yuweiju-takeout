"""Independent no-model environment verification; fresh tracked source snapshot and containers."""
import argparse,json,os,signal,subprocess,tarfile,time,io
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
EVIDENCE=Path(os.environ.get('SANDCASTLE_EVIDENCE','/home/endercloud/projects/yuweiju-sandcastle-env-evidence'))
p=argparse.ArgumentParser();p.add_argument('--commit',required=True);p.add_argument('--image',default='sandcastle:yuweiju-dev');p.add_argument('--runtime-only',type=Path);p.add_argument('--training',action='store_true');p.add_argument('--network',default='yuweiju-sandcastle-env_isolated');args=p.parse_args()
commit=subprocess.check_output(['git','-C',str(ROOT),'rev-parse','--verify','--end-of-options',args.commit+'^{commit}'],text=True).strip()
run=EVIDENCE/('environment-review-'+str(time.time_ns()));run.mkdir(parents=True)
snapshot=args.runtime_only.resolve() if args.runtime_only else run/'snapshot'
if not args.runtime_only:
 snapshot.mkdir();archive=subprocess.check_output(['git','-C',str(ROOT),'archive',commit]);tarfile.open(fileobj=io.BytesIO(archive)).extractall(snapshot,filter='data')
else:
 if not snapshot.is_relative_to(EVIDENCE) or snapshot==EVIDENCE: p.error('runtime snapshot must be an evidence child')
runtime=run/'runtime';runtime.mkdir()
for name in ['analysis-models','order-risk-models','uploads','algorithms']:(runtime/name).mkdir()
environment=snapshot/'.sandcastle/environment'
environment_commit=subprocess.check_output(['git','-C',str(ROOT),'rev-parse','HEAD'],text=True).strip()
environment_dirty=bool(subprocess.check_output(['git','-C',str(ROOT),'status','--porcelain','--','.sandcastle'],text=True).strip())
record={'verifierCommit':environment_commit,'environmentCommit':commit if not args.runtime_only else None,'declaredCandidateCommit':commit,'environmentDirectory':str(environment),'locale':{'LANG':'C.UTF-8','LC_ALL':'C.UTF-8'},'verifierUncommitted':environment_dirty,'commit':commit,'source':'fresh tracked source snapshot' if not args.runtime_only else 'explicit previously built snapshot','snapshot':str(snapshot),'image':args.image,'authMounted':False,'modelCalled':False,'containers':[],'steps':[]}
def interrupt(signum,frame):raise KeyboardInterrupt()
signal.signal(signal.SIGTERM,interrupt)
try:
 for step,network,script in ([('training',args.network,'training-check.sh')] if args.training else ([('build','bridge','build-check.sh')] if not args.runtime_only else [])+[('runtime',args.network,'runtime-check.sh')]):
  name='sandcastle-env-'+step+'-'+str(time.time_ns());record['containers'].append(name)
  command=['docker','run','--rm','--name',name,'--network',network,'--user','1000:1000','-e','LANG=C.UTF-8','-e','LC_ALL=C.UTF-8','--entrypoint','bash','--mount',f'type=bind,src={snapshot},dst=/workspace','--mount',f'type=bind,src={environment},dst=/environment,readonly','--mount',f'type=bind,src={run},dst=/evidence','--mount',f'type=bind,src={runtime},dst=/runtime',args.image,'/environment/'+script]
  with (run/(step+'-runner.log')).open('w') as out:
   proc=subprocess.Popen(command,stdout=out,stderr=subprocess.STDOUT)
   try: code=proc.wait(timeout=1800)
   except subprocess.TimeoutExpired: code=124
  record['steps'].append({'step':step,'exitCode':code,'network':network,'command':command})
  if code: break
except KeyboardInterrupt:record['failure']='interrupted';record['steps'].append({'step':'interrupted','exitCode':130})
finally:
 record['stopped']=True
 for name in record['containers']:
  inspect=subprocess.run(['docker','inspect',name],capture_output=True)
  if inspect.returncode==0:
   subprocess.run(['docker','stop','--time','10',name],stdout=subprocess.DEVNULL)
   subprocess.run(['docker','rm',name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  observed=subprocess.run(['docker','inspect',name],capture_output=True)
  record['stopped'] &= observed.returncode!=0 and b'no such' in observed.stderr.lower()
 (run/'resources.json').write_text(json.dumps(record,indent=2))
print(json.dumps(record))
raise SystemExit(0 if record['stopped'] and record['steps'] and all(x['exitCode']==0 for x in record['steps']) else 1)
