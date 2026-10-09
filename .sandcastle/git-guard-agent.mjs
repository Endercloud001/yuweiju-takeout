import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { writeFileSync, readFileSync } from 'node:fs';
const scenario = process.argv[2];
const emit = text => console.log(JSON.stringify({ type: 'text', text }));
const git = args => spawnSync('/usr/bin/git', args, { encoding: 'utf8' });
writeFileSync('guard-committed.txt', 'initial\n');
for (const args of [['add', '--', 'guard-committed.txt'], ['commit', '-m', 'test: guarded task commit']]) {
  const result = git(args);
  assert.equal(result.status, 0, `${args[0]} failed: ${result.stderr}`);
}
writeFileSync('guard-committed.txt', 'modified\n');
writeFileSync('guard-untracked.txt', 'retained progress\n');
for (const args of [['push'], ['reset', '--hard'], ['clean', '-fd'], ['restore', '--', 'guard-committed.txt'], ['checkout', '--', 'guard-committed.txt'], ['-C', '.', 'reset', '--hard'], ['-c', 'alias.danger=reset --hard', 'danger']]) {
  const denied = git(args);
  assert.equal(denied.status, 2, JSON.stringify(args));
  assert.match(denied.stderr, /SANDCASTLE_GIT_BLOCKED/);
  assert.equal(readFileSync('guard-committed.txt', 'utf8'), 'modified\n');
  assert.equal(readFileSync('guard-untracked.txt', 'utf8'), 'retained progress\n');
}
emit('guard-behavior-verified');
if (scenario === 'complete') { emit('<promise>COMPLETE</promise>'); }
else if (scenario === 'cancel') {
  writeFileSync('/smoke-evidence/guard-heartbeat.txt', String(Date.now()));
  setInterval(() => writeFileSync('/smoke-evidence/guard-heartbeat.txt', String(Date.now())), 100);
}
else throw new Error('Unknown guard scenario');
