import assert from 'node:assert/strict';
import { test } from 'node:test';
import { checkGit, GitDenied, aliasWords } from './git-policy.mjs';

const cleanGit = args => args.includes('diff') ? { status: 0, stdout: '' } : { status: 1, stdout: '' };
test('dangerous argument forms and aliases are classified before execution', () => {
  const rejected = [
    ['push'], ['-C', '/repo', 'push'], ['-c', 'x=y', 'push', '--force'],
    ['reset', '--hard'], ['reset', '--ha', 'HEAD'], ['clean', '-xdf'], ['clean', '-i'],
    ['branch', '-D', 'retained'], ['branch', '--delete', '--force', 'retained'], ['branch', '-df', 'retained'],
    ['checkout', '-f'], ['checkout', '--fo', 'task'], ['checkout', '-B', 'retained'],
    ['switch', '--discard-changes', 'task'], ['switch', '-C', 'retained'],
    ['--exec-path=/untrusted', 'status'], ['custom-command'],
  ];
  for (const args of rejected) assert.throws(() => checkGit(args, cleanGit), GitDenied, args.join(' '));
  const aliases = args => args.includes('alias.publish') ? { status: 0, stdout: 'push --force\n' } : cleanGit(args);
  assert.throws(() => checkGit(['publish'], aliases), /\[push\]/);
  assert.throws(() => checkGit(['danger'], () => ({ status: 0, stdout: '!git reset --hard\n' })), /shell aliases/);
});
test('safe operations, dry-run clean and clean-file restoration remain available', () => {
  for (const args of [['status'], ['diff'], ['show'], ['log'], ['add', '--', 'task.txt'], ['commit', '-m', 'task'], ['clean', '-fdn'], ['clean', '--dry-run'], ['checkout', '-b', 'new'], ['restore', '--', 'clean.txt'], ['restore', '--staged', '--', 'task.txt']]) checkGit(args, cleanGit);
});
test('restoration inspects only selected paths and refuses inspection failures', () => {
  const dirtyGit = args => args.includes('diff') ? { status: args.includes('dirty.txt') ? 1 : 0, stdout: '' } : cleanGit(args);
  assert.throws(() => checkGit(['restore', '--worktree', '--', 'dirty.txt'], dirtyGit), /overwrite/);
  assert.throws(() => checkGit(['checkout', '--', 'dirty.txt'], dirtyGit), /overwrite/);
  checkGit(['restore', '--', 'clean.txt'], dirtyGit);
  checkGit(['restore', '--staged', '--', 'dirty.txt'], dirtyGit);
  assert.throws(() => checkGit(['restore', '--', 'file'], () => ({ status: 128 })), /cannot inspect/);
  assert.throws(() => checkGit(['restore', '--pathspec-from-file=files'], cleanGit), /pathspec/);
});
test('alias splitting handles quotes, escaped spaces and malformed inputs', () => {
  assert.deepEqual(aliasWords('commit -m "task change"'), ['commit', '-m', 'task change']);
  assert.deepEqual(aliasWords("add 'task file'"), ['add', 'task file']);
  assert.deepEqual(aliasWords('add task\\ file'), ['add', 'task file']);
  assert.throws(() => aliasWords('add "unfinished'), GitDenied);
  assert.throws(() => aliasWords('add trailing\\'), GitDenied);
});
test('denial messages never echo arguments or credential-bearing configuration', () => {
  assert.throws(() => checkGit(['push', 'https://secret-token@example.invalid/repo'], cleanGit), error => error instanceof GitDenied && !error.message.includes('secret-token'));
});
test('installed standard commands stay available without allowing external helpers', () => {
  const installedGit = args => args[0] === '--list-cmds=main' ? { status: 0, stdout: 'ls-remote\ndescribe\npull\n' } : cleanGit(args);
  checkGit(['ls-remote', '--help'], installedGit);
  checkGit(['describe', '--always'], installedGit);
  assert.throws(() => checkGit(['custom-helper'], installedGit), GitDenied);
});
