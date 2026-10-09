#!/usr/bin/env python3
"""Exercise the installed wrapper in one disposable, unauthenticated container."""
import json
from pathlib import Path
import subprocess
import tempfile
import time

repo = Path(__file__).resolve().parent.parent
evidence = repo / '.scratch/git-guard-verification'
evidence.mkdir(parents=True, exist_ok=True)
fixture = Path(tempfile.mkdtemp(prefix='fixture-', dir=evidence))
name = 'yuweiju-git-guard-' + str(time.time_ns())
image = 'sandcastle:yuweiju-dev-git-safe'
results = []

def host_git(*args):
    result = subprocess.run(['git', '-C', str(fixture), *args], text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f'Isolated fixture Git failed (exit {result.returncode}): {result.stderr}')
    return result.stdout

host_git('init', '--initial-branch=task')
host_git('config', 'user.name', 'Guard fixture')
host_git('config', 'user.email', 'guard@localhost')
host_git('config', 'safe.directory', '/fixture')
for filename in ['tracked.txt', 'clean.txt']:
    (fixture / filename).write_text('initial\n')
host_git('add', '--', 'tracked.txt', 'clean.txt')
host_git('commit', '-m', 'test: isolated initial fixture')
host_git('branch', 'retained')
(fixture / 'tracked.txt').write_text('modified\n')
(fixture / 'progress.txt').write_text('retained\n')
(fixture / 'staged.txt').write_text('staged\n')
host_git('add', '--', 'staged.txt')
initial_ref = host_git('rev-parse', 'retained').strip()

def execute(argv, expected=0, label=None, user='1000:1000'):
    completed = subprocess.run(['docker', 'exec', '--user', user, '-w', '/fixture', name, *argv], text=True, capture_output=True)
    results.append({'case': label or ' '.join(argv[:2]), 'exit': completed.returncode, 'expected': expected})
    assert completed.returncode == expected, f'{label or argv}: {completed.returncode}: {completed.stderr[-1000:]}'
    return completed

def retained():
    assert (fixture / 'tracked.txt').read_text() == 'modified\n'
    assert (fixture / 'progress.txt').read_text() == 'retained\n'
    assert (fixture / 'staged.txt').read_text() == 'staged\n'
    assert host_git('rev-parse', 'retained').strip() == initial_ref
    assert host_git('diff', '--cached', '--name-only').strip() == 'staged.txt'

try:
    subprocess.run(['docker', 'run', '-d', '--name', name, '--user', '1000:1000', '--mount', f'type=bind,src={fixture},dst=/fixture', image], check=True, capture_output=True)
    execute(['node', '/opt/sandcastle/git-guard/guard-ready.mjs'], label='root-owned guard readiness')
    execute(['sh', '-c', 'test ! -w /opt/sandcastle/git-guard/git-policy.mjs && test ! -w /usr/bin && test ! -w /usr/lib/git-core'], label='agent cannot modify guard installation')
    rejected = [
        ['push'], ['push', '--force'], ['-C', '/fixture', 'push'],
        ['reset', '--hard'], ['reset', '--ha'], ['-C/fixture', 'reset', '--hard'],
        ['clean', '-f'], ['clean', '-xdf'], ['clean', '-ffdx'], ['clean', '-i'],
        ['branch', '-D', 'retained'], ['branch', '-df', 'retained'], ['branch', '--delete', '--force', 'retained'],
        ['checkout', '-f', 'retained'], ['checkout', '-B', 'retained'], ['checkout', '.',],
        ['checkout', '--', 'tracked.txt'], ['checkout', 'HEAD', '--', 'tracked.txt'],
        ['restore', '.'], ['restore', '--worktree', '--', 'tracked.txt'],
        ['restore', '--source=HEAD', '--staged', '--worktree', '--', 'staged.txt'],
        ['switch', '--discard-changes', 'retained'], ['switch', '-C', 'retained'],
        ['-c', 'alias.danger=reset --hard', 'danger'],
        ['-c', 'alias.publish=push --force', 'publish'],
        ['-c', 'alias.unsafe-script=!touch shell-alias-ran', 'unsafe-script'],
    ]
    for args in rejected:
        rejected_run = execute(['/usr/bin/git', *args], expected=2, label='reject ' + ' '.join(args))
        assert 'SANDCASTLE_GIT_BLOCKED' in rejected_run.stderr
        retained()
    for binary in ['git', '/usr/lib/git-core/git']:
        execute([binary, 'reset', '--hard'], expected=2, label='reject through ' + binary)
        retained()
    execute(['sh', '-c', 'git\treset --hard'], expected=2, label='reject shell whitespace variant')
    (fixture / 'child.sh').write_text('#!/bin/sh\n/usr/bin/git clean -fd\n')
    execute(['sh', '/fixture/child.sh'], expected=2, label='reject subprocess script')
    retained()
    assert not (fixture / 'shell-alias-ran').exists()
    secret = execute(['/usr/bin/git', 'push', 'https://fixture-secret@example.invalid/repo'], expected=2, label='redact credential-bearing argument')
    assert 'fixture-secret' not in secret.stderr
    for args in [['status', '--short'], ['diff'], ['show', '--stat'], ['log', '-1'], ['clean', '-fdn'], ['checkout', '--', 'clean.txt'], ['restore', '--', 'clean.txt'], ['-c', 'alias.inspect=diff --stat', 'inspect']]:
        execute(['git', *args], label='allow ' + ' '.join(args))
        retained()
    execute(['git', 'describe', '--always'], label='allow installed standard command beyond fast-path list')
    execute(['git', 'ls-remote', '/fixture'], label='allow dependency Git query against local fixture only')
    execute(['/usr/bin/git-upload-pack', '--advertise-refs', '/fixture'], label='preserve implicit subcommand of Git helper symlink')
    execute(['git', 'custom-unavailable-helper'], expected=2, label='reject unknown external helper')
    retained()
    preparation = "mkdir /fixture/preparation && cd /fixture/preparation && git init -q && git config user.name 'Preparation fixture' && git config user.email preparation@localhost && printf initial > file && git add -- file && git commit -qm initial && printf modified > file && git checkout -f && test \"$(cat file)\" = initial"
    prepared_command = subprocess.check_output(['node', '--import', './node_modules/tsx/dist/loader.mjs', '--input-type=module', '-e', "import {prepareInstallCommand} from './.sandcastle/common.mts'; console.log(prepareInstallCommand(process.argv[1]));", preparation], cwd=repo, text=True).strip()
    execute(['sh', '-c', prepared_command], label='trusted preparation has native Git semantics for entire command chain')
    execute(['git', '-C', '/fixture/preparation', 'checkout', '-f'], expected=2, label='agent still rejects force checkout after preparation')
    execute(['node', '/opt/sandcastle/git-guard/guard-ready.mjs'], label='preparation PATH does not persist into agent context')
    retained()
    execute(['git', 'restore', '--staged', '--', 'staged.txt'], label='allow unstage while retaining working file')
    assert (fixture / 'staged.txt').read_text() == 'staged\n'
    assert not host_git('diff', '--cached', '--name-only').strip()
    execute(['git', 'checkout', '-b', 'normal-task'], label='allow normal branch creation')
    execute(['git', 'add', '--', 'tracked.txt', 'staged.txt'], label='allow explicit staging')
    execute(['git', 'commit', '-m', 'test: ordinary guarded task commit'], label='allow ordinary commit')
    assert host_git('show', 'HEAD:tracked.txt') == 'modified\n'
    assert (fixture / 'progress.txt').read_text() == 'retained\n'
    # The host's own Git remains unrestricted; a normal task worktree can be
    # created and removed even though container clean/push are blocked.
    host_tree = fixture / 'host-worktree'
    host_git('worktree', 'add', '--detach', str(host_tree), 'HEAD')
    host_git('worktree', 'remove', str(host_tree))
    results.append({'case': 'host worktree creation and cleanup unaffected', 'exit': 0, 'expected': 0})
    # Explicitly establish the boundary: the delegate is still executable.
    # Run only status through it; never demonstrate a destructive bypass.
    execute(['/opt/sandcastle/git-guard/real/git', 'status', '--short'], label='known boundary: delegate remains callable')
    execute(['mv', '/opt/sandcastle/git-guard/git-policy.mjs', '/opt/sandcastle/git-guard/git-policy.disabled'], user='0:0', label='simulate missing policy in disposable container')
    execute(['node', '/opt/sandcastle/git-guard/guard-ready.mjs'], expected=2, label='missing policy fails readiness')
    missing = subprocess.run(['docker', 'exec', '-w', '/fixture', name, '/usr/bin/git', 'reset', '--hard'], text=True, capture_output=True)
    assert missing.returncode != 0
    assert (fixture / 'progress.txt').read_text() == 'retained\n'
    assert host_git('show', 'HEAD:tracked.txt') == 'modified\n'
    results.append({'case': 'missing policy does not execute real Git', 'exit': missing.returncode, 'expected': 'nonzero'})
finally:
    cleanup = subprocess.run(['docker', 'rm', '-f', name], text=True, capture_output=True)
    # Remove only the explicitly named disposable test container, never prune.
    stopped = subprocess.run(['docker', 'inspect', name], capture_output=True).returncode != 0
    record = {'image': image, 'fixture': str(fixture), 'container': name, 'containerStopped': stopped,
              'authMounted': False, 'modelCalled': False, 'results': results,
              'knownBypasses': ['delegate or alternate Git executable', 'direct filesystem operations', 'operations outside the documented policy']}
    (evidence / 'wrapper-verdict.json').write_text(json.dumps(record, indent=2) + '\n')
    assert cleanup.returncode == 0 and stopped, 'test container cleanup failed'
print(json.dumps({'passed': True, 'cases': len(results), 'containerStopped': stopped, 'evidence': str(evidence / 'wrapper-verdict.json')}))
