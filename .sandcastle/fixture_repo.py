"""Prepare a small real Git repo for existing no-model orchestration fixtures."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile


def create(runtime, parent):
    parent.mkdir(parents=True, exist_ok=True)
    fixture = Path(tempfile.mkdtemp(prefix='run-', dir=parent))
    (fixture / '.sandcastle').mkdir()
    for source in (runtime / '.sandcastle').iterdir():
        if source.is_file():
            shutil.copyfile(source, fixture / '.sandcastle' / source.name)
    shutil.copyfile(runtime / 'package.json', fixture / 'package.json')
    (fixture / '.sandcastle/environment').mkdir()
    shutil.copyfile(runtime / '.sandcastle/environment/verify.py', fixture / '.sandcastle/environment/verify.py')
    (fixture / 'docs/standards').mkdir(parents=True)
    shutil.copyfile(runtime / 'docs/standards/backend.md', fixture / 'docs/standards/backend.md')
    def git(*args):
        subprocess.run(['git', '-C', str(fixture), *args], check=True, stdout=subprocess.DEVNULL)
    git('init', '--initial-branch=codex/verification-fixture')
    git('config', 'user.name', 'Sandcastle fixture')
    git('config', 'user.email', 'fixture@localhost')
    git('add', '--', '.sandcastle', 'package.json', 'docs')
    git('commit', '-m', 'test: real no-model Sandcastle fixture')
    os.symlink((runtime / 'node_modules').resolve(), fixture / 'node_modules', target_is_directory=True)
    with (fixture / '.git/info/exclude').open('a') as output:
        output.write('\n/node_modules\n/.scratch/\n')
    return fixture
