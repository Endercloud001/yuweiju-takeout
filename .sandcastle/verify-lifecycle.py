#!/usr/bin/env python3
"""Run original no-model lifecycle scenarios in a small, project-local repo.

The scenarios exercise orchestration, not application assets. A minimal real Git
repo avoids making the deadline fixture depend on full DrvFS checkout.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

runtime = Path(__file__).resolve().parent.parent
parent = runtime / '.scratch/lifecycle-verification'
parent.mkdir(parents=True, exist_ok=True)
fixture = Path(tempfile.mkdtemp(prefix='run-', dir=parent))
(fixture / '.sandcastle').mkdir()
for source in (runtime / '.sandcastle').iterdir():
    if source.is_file():
        shutil.copyfile(source, fixture / '.sandcastle' / source.name)
shutil.copyfile(runtime / 'package.json', fixture / 'package.json')
def git(*args):
    subprocess.run(['git', '-C', str(fixture), *args], check=True, stdout=subprocess.DEVNULL)
git('init', '--initial-branch=codex/lifecycle-fixture')
git('config', 'user.name', 'Lifecycle fixture')
git('config', 'user.email', 'lifecycle@localhost')
git('add', '--', '.sandcastle', 'package.json')
git('commit', '-m', 'test: real Sandcastle lifecycle fixture')
os.symlink((runtime / 'node_modules').resolve(), fixture / 'node_modules', target_is_directory=True)
with (fixture / '.git/info/exclude').open('a') as output:
    output.write('\n/node_modules\n/.scratch/\n')
evidence = fixture / '.scratch/evidence'
environment = {**os.environ, 'SANDCASTLE_EVIDENCE': str(evidence), 'SANDCASTLE_IMAGE': 'sandcastle:yuweiju-dev-git-safe'}
temporary = fixture / '.scratch/tmp'
temporary.mkdir(parents=True)
environment['TMPDIR'] = str(temporary)
log = fixture / '.scratch/lifecycle.log'
log.parent.mkdir(parents=True, exist_ok=True)
with log.open('w') as output:
    # The tsx CLI creates a Unix socket, which DrvFS does not support. The
    # loader is the same entry used by the existing process supervisor.
    result = subprocess.run(['node', '--import', str(runtime / 'node_modules/tsx/dist/loader.mjs'), '.sandcastle/smoke-run.mts'], cwd=fixture, env=environment, stdout=output, stderr=subprocess.STDOUT)
cases = [json.loads(path.read_text()) for path in evidence.glob('*-verdict.json')]
record = {'passed': result.returncode == 0 and len(cases) == 9 and all(case['passed'] for case in cases),
          'exit': result.returncode, 'fixture': str(fixture), 'log': str(log), 'cases': cases, 'modelCalled': False, 'authMounted': False}
(parent / 'lifecycle-verdict.json').write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps({key: record[key] for key in ['passed', 'exit', 'fixture', 'log']} | {'cases': len(cases)}))
if not record['passed']:
    print('\n'.join(log.read_text().splitlines()[-20:]))
    raise SystemExit(result.returncode or 1)
