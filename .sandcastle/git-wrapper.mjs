#!/usr/local/bin/node
import { spawnSync } from 'node:child_process';
import { basename } from 'node:path';
import { checkGit, GitDenied } from './git-policy.mjs';
const real = '/opt/sandcastle/git-guard/real/git';
const entry = basename(process.argv[1]);
// Debian's git-upload-pack/git-receive-pack entries are symlinks to git.
// Preserve their implicit subcommand when Node executes this wrapper.
const args = [...(entry.startsWith('git-') && !entry.endsWith('.mjs') ? [entry.slice(4)] : []), ...process.argv.slice(2)];
try {
  checkGit(args, argv => spawnSync(real, argv, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }));
} catch (error) {
  console.error(error instanceof GitDenied ? error.message : 'SANDCASTLE_GIT_BLOCKED [policy]: policy inspection failed; preserve the worktree and report the blocker.');
  process.exit(2);
}
const result = spawnSync(real, args, { stdio: 'inherit' });
if (result.error) { console.error('SANDCASTLE_GIT_BLOCKED [runtime]: real Git could not start.'); process.exit(2); }
if (result.signal) process.kill(process.pid, result.signal);
else process.exit(result.status ?? 2);
