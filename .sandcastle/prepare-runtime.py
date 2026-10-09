#!/usr/bin/env python3
"""Create a project-local runtime; never edit the external source checkout."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess

project = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument('--source', default='/home/endercloud/projects/yuweiju-sandcastle-env')
parser.add_argument('--finish-existing', action='store_true', help='finish a clean clone at the source commit after interrupted preparation')
args = parser.parse_args()
source = Path(args.source).resolve()
runtime = project / '.scratch/sandcastle-git-safety/runtime'
if runtime.exists() and not args.finish_existing:
    raise SystemExit(f'Runtime already exists; inspect it rather than overwriting: {runtime}')
runtime.parent.mkdir(parents=True, exist_ok=True)
def run(argv, cwd=None):
    return subprocess.check_output(argv, cwd=cwd, text=True).strip()
source_commit = run(['git', '-C', str(source), 'rev-parse', 'HEAD'])
if args.finish_existing:
    if run(['git', 'rev-parse', 'HEAD'], runtime) != source_commit or run(['git', 'status', '--porcelain'], runtime):
        raise SystemExit('Existing clone is not clean at the source commit; inspect it manually')
else:
    run(['git', 'clone', '--no-hardlinks', '--no-checkout', str(source), str(runtime)])
    # Explicit cwd avoids clone's internal checkout on WSL/DrvFS paths.
    run(['git', 'restore', '--source=HEAD', '--staged', '--worktree', '--', '.'], runtime)
run(['git', 'checkout', '-b', 'codex/sandcastle-git-safety'], runtime)
run(['git', 'remote', 'remove', 'origin'], runtime)
run(['git', 'config', 'user.name', 'Sandcastle task agent'], runtime)
run(['git', 'config', 'user.email', 'sandcastle@localhost'], runtime)
for overlay in (project / '.sandcastle').iterdir():
    if overlay.is_file():
        shutil.copyfile(overlay, runtime / '.sandcastle' / overlay.name)
for note in ['sandcastle-git-safety-plan.md', 'sandcastle-git-safety-review.md', 'sandcastle-git-safety-implementation.md']:
    shutil.copyfile(project / 'docs' / note, runtime / 'docs' / note)
shutil.copytree(project / '.agents/skills/yuweiju-afk', runtime / '.agents/skills/yuweiju-afk', dirs_exist_ok=True, ignore=shutil.ignore_patterns('__pycache__', '*.pyc'))
package_file = runtime / 'package.json'
package = json.loads(package_file.read_text())
package['scripts']['check:git-policy'] = 'node --test .sandcastle/git-policy.test.mjs'
package['scripts']['check:git-guard'] = 'python3 .sandcastle/verify-git-guard.py'
package['scripts']['check:lifecycle'] = 'python3 .sandcastle/verify-lifecycle.py'
package_file.write_text(json.dumps(package, indent=2) + '\n')
modules = source / 'node_modules'
if not modules.is_dir():
    raise SystemExit('Source Linux node_modules is absent; install dependencies in the new runtime before verification')
os.symlink(modules, runtime / 'node_modules', target_is_directory=True)
exclude = runtime / '.git/info/exclude'
with exclude.open('a') as output:
    output.write('\n/node_modules\n/.scratch/\n')
run(['git', 'add', '--', '.sandcastle', 'package.json', 'docs/sandcastle-git-safety-plan.md', 'docs/sandcastle-git-safety-review.md', 'docs/sandcastle-git-safety-implementation.md', '.agents/skills/yuweiju-afk'], runtime)
run(['git', '-c', 'user.name=Sandcastle preparation', '-c', 'user.email=sandcastle@localhost', 'commit', '-m', 'feat: protect unattended Sandcastle Git operations'], runtime)
record = {'source': str(source), 'sourceCommit': source_commit, 'runtime': str(runtime),
          'preparationCommit': run(['git', 'rev-parse', 'HEAD'], runtime),
          'modelCalled': False, 'authCopied': False, 'remoteConfigured': False}
(runtime.parent / 'preparation.json').write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps(record))
