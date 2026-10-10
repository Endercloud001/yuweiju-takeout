"""No-model preflight regression using only newly owned networks and Redis."""
import json
from pathlib import Path
import subprocess
import time
import os
import signal
import sys
from preflight import ROOT, run

evidence = ROOT / '.scratch/preflight-verification' / str(time.time_ns())
evidence.mkdir(parents=True)
suffix = str(time.time_ns())
internal = 'afk-preflight-data-' + suffix
egress = 'afk-preflight-egress-' + suffix
service = 'afk-preflight-redis-' + suffix
owned_networks = []
owned_container = False
cases = []
try:
    for network, options in [(internal, ['--internal']), (egress, [])]:
        subprocess.run(['docker', 'network', 'create', *options, network], check=True, stdout=subprocess.DEVNULL)
        owned_networks.append(network)
    subprocess.run(['docker', 'run', '-d', '--name', service, '--network', internal, 'redis:7.4'], check=True, stdout=subprocess.DEVNULL)
    owned_container = True
    base = {'image': 'sandcastle:yuweiju-dev-git-safe', 'preflightUrls': ['https://chatgpt.com/']}
    for scenario, networks, expected in [('missing-egress', [internal], False),
                                          ('persistent-failure', [internal], False),
                                          ('actual-two-networks', [internal, egress], True)]:
        config = {**base, 'networks': networks, 'preflightServices': [{'host': service, 'port': 6379}]}
        result = run(config, evidence / scenario)
        assert result['passed'] is expected, (scenario, result)
        assert result['stopped']
        assert result['classification'] == ('ready' if expected else 'network-or-service')
        assert b'\r' not in (evidence / scenario / 'preflight.sh').read_bytes()
        assert subprocess.run(['docker', 'inspect', service], capture_output=True).returncode == 0
        cases.append({'scenario': scenario, 'passed': True, 'result': result})
    auth = evidence / 'empty-auth'
    auth.mkdir()
    result = run({**base, 'networks': [egress], 'authDirectory': str(auth)}, evidence / 'authentication-failure', auth=True)
    assert not result['passed'] and result['classification'] == 'authentication' and result['stopped'], result
    cases.append({'scenario': 'authentication-failure', 'passed': True, 'result': result})
    cancel_config = evidence / 'cancel-config.json'
    cancel_config.write_text(json.dumps({**base, 'networks': [egress], 'preflightUrls': ['https://192.0.2.1/']}))
    cancel_evidence = evidence / 'cancel'
    with (evidence / 'cancel-runner.log').open('w') as output:
        child = subprocess.Popen([sys.executable, str(ROOT / '.sandcastle/preflight.py'), '--config', str(cancel_config)],
                                 env={**os.environ, 'SANDCASTLE_EVIDENCE': str(cancel_evidence)}, stdout=output, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline and child.poll() is None:
                if list(cancel_evidence.glob('preflight-*/network.json')):
                    child.send_signal(signal.SIGTERM)
                    break
                time.sleep(0.05)
            exit_code = child.wait(timeout=30)
            assert exit_code == 130, exit_code
            result = json.loads(next(cancel_evidence.glob('preflight-*/preflight.json')).read_text())
            assert result['stopped'] and result['classification'] == 'cancelled', result
            cases.append({'scenario': 'cancel-during-network-probe', 'passed': True, 'result': result})
        finally:
            if child.poll() is None:
                child.send_signal(signal.SIGTERM)
                child.wait(timeout=30)
finally:
    if owned_container:
        subprocess.run(['docker', 'rm', '-f', service], check=True, stdout=subprocess.DEVNULL)
    for network in reversed(owned_networks):
        subprocess.run(['docker', 'network', 'rm', network], check=True, stdout=subprocess.DEVNULL)
    (evidence / 'verdict.json').write_text(json.dumps({'cases': cases, 'modelCalled': False,
                                                    'ownedResourcesRemoved': True}, indent=2), encoding='utf-8', newline='\n')
print(json.dumps({'passed': len(cases) == 5, 'cases': len(cases), 'evidence': str(evidence), 'modelCalled': False}))
