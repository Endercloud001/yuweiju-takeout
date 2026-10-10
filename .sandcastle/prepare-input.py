"""Read current GitHub ticket and local material status into a task-specific input folder."""
import argparse,datetime,json,re,shutil,subprocess,tempfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--issue',type=int,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--source-root',type=Path);p.add_argument('--material',action='append',default=[]);args=p.parse_args()
r=args.source_root or Path(__file__).resolve().parent.parent;args.output.mkdir(parents=True,exist_ok=True)
command=['gh','issue','view',str(args.issue),'--repo','Endercloud001/yuweiju-takeout','--json','number,state,title,body,comments,updatedAt']
fetches=args.output/'fetches';fetches.mkdir(exist_ok=True)
attempt=Path(tempfile.mkdtemp(prefix='issue-',dir=fetches))
record={'command':command,'cwd':str(Path.cwd()),'startedAt':datetime.datetime.now(datetime.timezone.utc).isoformat()}
try:
 result=subprocess.run(command,capture_output=True)
except OSError as error:
 record.update(finishedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),exitCode=None,error=str(error))
 (attempt/'fetch.json').write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
 raise
record.update(finishedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),exitCode=result.returncode,stdout='stdout.raw',stderr='stderr.log')
# Preserve the first response before JSON parsing or any later material preparation.
(attempt/'stdout.raw').write_bytes(result.stdout)
(attempt/'stderr.log').write_bytes(result.stderr)
(attempt/'fetch.json').write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
if result.returncode:raise subprocess.CalledProcessError(result.returncode,command)
issue=json.loads(result.stdout.decode('utf-8'))
(args.output/'issue.json').write_text(json.dumps(issue,ensure_ascii=False,indent=2),encoding='utf-8')
refs=sorted(set(re.findall(r'docs/verification-evidence/2026-10-05/[A-Za-z0-9_.-]+',issue['body'])))
(args.output/'material-status.json').write_text(json.dumps([{'path':f,'exists':(r/f).is_file(),'note':'inspect side effects before reuse' if (r/f).is_file() else 'cleaned historical evidence; do not restore or rerun original writes'} for f in refs],indent=2))
for f in ['AGENTS.md','docs/agents/workflow.md','docs/standards/backend.md','docs/standards/admin.md','docs/standards/miniapp.md','docs/sandcastle-afk-complement-report.md','GLOSSARY.md','docs/agents/domain.md','docs/adr/0001-retain-conventional-backend-layering.md','docs/adr/0002-preserve-historical-order-images.md','yuweiju-backend/AGENTS.md','yuweiju-web-vue/AGENTS.md','yuweiju-weixin-miniapp/AGENTS.md']+args.material:
 source=(r/f).resolve()
 if not source.is_relative_to(r.resolve()):raise ValueError('Material must stay within the selected source repository')
 if source.is_file():destination=args.output/source.relative_to(r.resolve());destination.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,destination)
print(json.dumps({'issue':issue['number'],'state':issue['state'],'updatedAt':issue['updatedAt'],'output':str(args.output),'missingHistoricalEvidence':sum(not(r/f).is_file() for f in refs)}))
