"""Inspect the actual image/network combination without a model or business writes."""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import time
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent.parent


def run(config, evidence, auth=False):
    evidence.mkdir(parents=True, exist_ok=True)
    name = 'sandcastle-preflight-' + str(time.time_ns())
    record = {'container': name, 'image': config['image'], 'networks': config.get('networks', []),
              'modelCalled': False, 'authMounted': auth, 'checks': []}
    urls = config.get('preflightUrls', ['https://chatgpt.com/'])
    for url in urls:
        parts = urlsplit(url)
        if parts.scheme != 'https' or not parts.hostname or parts.username or parts.password or parts.query or parts.fragment:
            raise ValueError('preflightUrls must be HTTPS URLs without credentials, query or fragment')
    services = config.get('preflightServices', [])
    for service in services:
        if not isinstance(service.get('host'), str) or not isinstance(service.get('port'), int) or not 1 <= service['port'] <= 65535:
            raise ValueError('preflightServices require host and port')
    # Only network status/type is returned. HTTP bodies and auth output stay discarded.
    probe = '''import json,socket,urllib.request,urllib.error,sys
checks=[]
for url in json.loads(sys.argv[1]):
 try:
  host=urllib.parse.urlsplit(url).hostname;socket.getaddrinfo(host,443)
  try:
   with urllib.request.urlopen(urllib.request.Request(url,method='HEAD'),timeout=12) as response: status=response.status
  except urllib.error.HTTPError as error: status=error.code
  checks.append({'kind':'https','target':url,'passed':status<500,'httpStatus':status})
 except Exception as error: checks.append({'kind':'https','target':url,'passed':False,'errorType':type(error).__name__})
for service in json.loads(sys.argv[2]):
 try:
  with socket.create_connection((service['host'],service['port']),timeout=5): pass
  checks.append({'kind':'service','target':service,'passed':True})
 except Exception as error: checks.append({'kind':'service','target':service,'passed':False,'errorType':type(error).__name__})
print(json.dumps(checks))
sys.exit(0 if all(check['passed'] for check in checks) else 1)
'''
    import shlex
    script = 'set -u\ncd /workspace\n'
    for tool in ['git', 'java', 'mvn', 'node', 'python3', 'codex']:
        script += f'command -v {tool} >/dev/null || exit 10\n'
    script += '/usr/local/bin/node /opt/sandcastle/git-guard/guard-ready.mjs >/dev/null || exit 11\n'
    # Container Git mount is supplied explicitly, independent of host worktree syntax.
    script += 'git rev-parse HEAD > /evidence/head.txt || exit 12\n'
    script += f'python3 -c {shlex.quote(probe)} {shlex.quote(json.dumps(urls))} {shlex.quote(json.dumps(services))} > /evidence/network.json\nstatus=$?\n[ "$status" = 0 ] || exit 13\n'
    if auth:
        script += 'codex login status >/dev/null 2>&1 || exit 14\n'
    script_file = evidence / 'preflight.sh'
    script_file.write_text(script, encoding='utf-8', newline='\n')
    metadata = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', '--absolute-git-dir'], text=True).strip()
    common = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', '--path-format=absolute', '--git-common-dir'], text=True).strip()
    command = ['docker', 'run', '--rm', '--name', name,
               *sum((['--network', network] for network in config.get('networks', [])), []),
               '--user', '1000:1000', '-e', 'LANG=C.UTF-8', '-e', 'LC_ALL=C.UTF-8',
               '-e', f'GIT_DIR={metadata}', '-e', 'GIT_WORK_TREE=/workspace', '--entrypoint', 'bash',
               '--mount', f'type=bind,src={ROOT},dst=/workspace,readonly',
               '--mount', f'type=bind,src={common},dst={common},readonly',
               '--mount', f'type=bind,src={evidence},dst=/evidence']
    if metadata != common:
        command += ['--mount', f'type=bind,src={metadata},dst={metadata},readonly']
    if auth:
        command += ['--mount', f'type=bind,src={Path(config["authDirectory"]).resolve()},dst=/home/agent/.codex,readonly']
    command += [config['image'], '/evidence/preflight.sh']
    process = None
    try:
        with (evidence / 'runner.log').open('w') as output:
            process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT)
            try:
                record['exitCode'] = process.wait(timeout=90)
            except subprocess.TimeoutExpired:
                record['exitCode'] = 124
    except KeyboardInterrupt:
        record['exitCode'] = 130
    finally:
        observed = subprocess.run(['docker', 'inspect', name], capture_output=True)
        if observed.returncode == 0:
            subprocess.run(['docker', 'stop', '--time', '10', name], capture_output=True)
        if process is not None and process.poll() is None:
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.wait(timeout=15)
        observed = subprocess.run(['docker', 'inspect', name], capture_output=True)
        record['stopped'] = observed.returncode != 0 and b'no such' in observed.stderr.lower()
        if (evidence / 'network.json').exists():
            try:
                record['checks'] = json.loads((evidence / 'network.json').read_text())
            except (json.JSONDecodeError, OSError):
                record['checks'] = [{'kind': 'network-probe', 'passed': False, 'status': 'interrupted-or-unreadable'}]
        record['passed'] = record.get('exitCode') == 0 and record['stopped']
        record['classification'] = {10: 'missing-tool', 11: 'git-guard', 12: 'git-metadata',
                                    13: 'network-or-service', 14: 'authentication', 124: 'persistent-timeout',
                                    130: 'cancelled'}.get(record.get('exitCode'), 'ready' if record['passed'] else 'runtime-failure')
        (evidence / 'preflight.json').write_text(json.dumps(record, indent=2), encoding='utf-8', newline='\n')
    return record


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', type=Path, required=True)
    parser.add_argument('--auth', action='store_true', help='Check dedicated login status read-only; no model')
    args = parser.parse_args()
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(KeyboardInterrupt()))
    config = json.loads(args.config.read_text(encoding='utf-8-sig'))
    evidence = Path(os.environ.get('SANDCASTLE_EVIDENCE', ROOT / '.scratch/sandcastle-evidence')).resolve() / ('preflight-' + str(time.time_ns()))
    record = run(config, evidence, args.auth)
    print(json.dumps({key: record[key] for key in ['passed', 'exitCode', 'classification', 'stopped']} | {'evidence': str(evidence)}))
    raise SystemExit(0 if record['passed'] else record['exitCode'] or 1)
