"""No-model independent review; fresh commit worktree or an explicit isolated snapshot."""
import argparse
import datetime
import json
import os
import signal
from pathlib import Path
import subprocess
import time

REPO = Path(__file__).resolve().parent.parent
EVIDENCE = Path(os.environ.get('SANDCASTLE_EVIDENCE', '/home/endercloud/projects/yuweiju-sandcastle-env-evidence')).resolve()
IMAGE = os.environ.get('SANDCASTLE_IMAGE', 'sandcastle:yuweiju-dev')
parser = argparse.ArgumentParser(description=__doc__)
source = parser.add_mutually_exclusive_group(required=True)
source.add_argument('--commit', help='Exact local commit to review in a new detached worktree')
source.add_argument('--snapshot', type=Path, help='Existing isolated Linux snapshot beneath evidence; uncommitted content is explicitly labelled')
args = parser.parse_args()
subprocess.run(['docker', 'info'], check=True, stdout=subprocess.DEVNULL)
run_id = f'admin-review-{time.time_ns()}'
logs = EVIDENCE / (run_id + '-logs')
logs.mkdir()
if args.commit:
    commit = subprocess.check_output(['git', '-C', str(REPO), 'rev-parse', '--verify', '--end-of-options', args.commit + '^{commit}'], text=True).strip()
    snapshot = EVIDENCE / run_id
    subprocess.run(['git', '-C', str(REPO), 'worktree', 'add', '--detach', str(snapshot), commit], check=True, stdout=subprocess.DEVNULL)
    origin = {'commit': commit, 'source': 'committed content'}
else:
    snapshot = args.snapshot.resolve()
    if not snapshot.is_relative_to(EVIDENCE) or snapshot == EVIDENCE:
        parser.error('snapshot must be an isolated child directory of the existing evidence directory')
    if not (snapshot / 'yuweiju-web-vue/yuweiju-admin/package.json').is_file():
        parser.error('snapshot is missing the admin package')
    origin = {'source': 'explicit snapshot; may contain uncommitted files', 'commit': None}
cidfile = logs / 'container.id'
record = {**origin, 'started': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'snapshot': str(snapshot), 'logs': str(logs), 'image': IMAGE,
          'hostPid': os.getpid(), 'authMounted': False, 'modelCalled': False,
          'independentVerification': 'failed', 'humanAcceptance': 'not-performed'}
process = None
def request_stop(signum, _frame):
    raise KeyboardInterrupt(f'Independent review interrupted by signal {signum}')

signal.signal(signal.SIGTERM, request_stop)
try:
    with (logs / 'runner.log').open('w') as out:
        process = subprocess.Popen(['docker', 'run', '--rm', '--cidfile', str(cidfile),
            '--user', '1000:1000', '--entrypoint', 'sh',
            '--mount', f'type=bind,src={snapshot},dst=/home/agent/workspace',
            '--mount', f'type=bind,src={logs},dst=/review-evidence',
            '--mount', f'type=bind,src={REPO / ".sandcastle/verify-admin.sh"},dst=/verify-admin.sh,readonly',
            IMAGE, '/verify-admin.sh'], stdout=out, stderr=subprocess.STDOUT)
        record['dockerClientPid'] = process.pid
        try:
            record['exitCode'] = process.wait(timeout=1800)
        except subprocess.TimeoutExpired:
            record.update(exitCode=124, failure='Independent review exceeded 30 minutes')
        if record.get('exitCode') == 0:
            record['independentVerification'] = 'passed'
except KeyboardInterrupt as error:
    record.update(exitCode=130, failure=str(error) or 'Independent review interrupted')
except Exception as error:
    record.update(exitCode=1, failure=str(error))
finally:
    record['stopped'] = False
    try:
        if cidfile.exists():
            cid = cidfile.read_text().strip()
            record['containerId'] = cid
            observed = subprocess.run(['docker', 'inspect', cid], capture_output=True, text=True, timeout=30)
            if observed.returncode == 0:
                container = json.loads(observed.stdout)[0]
                owned = any(m['Source'] == str(snapshot) and m['Destination'] == '/home/agent/workspace' for m in container['Mounts'])
                if not owned:
                    raise RuntimeError('Container ownership mismatch; no cleanup attempted')
                subprocess.run(['docker', 'stop', '--time', '10', cid], check=True, capture_output=True, timeout=30)
                subprocess.run(['docker', 'rm', cid], capture_output=True, timeout=30)
                observed = subprocess.run(['docker', 'inspect', cid], capture_output=True, text=True, timeout=30)
            record['stopped'] = observed.returncode != 0 and 'no such object' in observed.stderr.lower()
        else:
            # No container was created; retain the Docker error as the failure source.
            record['stopped'] = process is not None and process.poll() is not None
        if process is not None:
            process.wait(timeout=30)
    except Exception as error:
        record['cleanupFailure'] = str(error)
    if not record['stopped'] or record.get('cleanupFailure'):
        record['exitCode'] = 1
        record['independentVerification'] = 'failed'
    record['ended'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    (logs / 'resources.json').write_text(json.dumps(record, indent=2))
statuses = logs / 'exit-status.tsv'
if statuses.exists():
    record['checks'] = [line.split('\t')[:2] for line in statuses.read_text().splitlines()]
    record['failedStep'] = next((step for step, code in record['checks'] if code != '0'), None)
(logs / 'resources.json').write_text(json.dumps(record, indent=2))
print(json.dumps(record))
raise SystemExit(record.get('exitCode', 1))
