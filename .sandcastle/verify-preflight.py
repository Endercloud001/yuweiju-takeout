"""No-model preflight regression using only newly owned networks and Redis."""
import json
from pathlib import Path
import subprocess
import time
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
finally:
    if owned_container:
        subprocess.run(['docker', 'rm', '-f', service], check=True, stdout=subprocess.DEVNULL)
    for network in reversed(owned_networks):
        subprocess.run(['docker', 'network', 'rm', network], check=True, stdout=subprocess.DEVNULL)
    (evidence / 'verdict.json').write_text(json.dumps({'cases': cases, 'modelCalled': False,
                                                    'ownedResourcesRemoved': True}, indent=2), encoding='utf-8', newline='\n')
print(json.dumps({'passed': len(cases) == 4, 'cases': len(cases), 'evidence': str(evidence), 'modelCalled': False}))
