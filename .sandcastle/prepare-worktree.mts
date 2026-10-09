import { execFile } from 'node:child_process';
import { existsSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

// Sandcastle 0.12.0 hardcodes a 30-second worktree-creation timeout.
// DrvFS checkout can exceed it. Prepare only this fresh run's managed worktree
// asynchronously under the same total AbortSignal, then let Sandcastle reuse it.
export async function prepareWorktree(repo: string, branch: string, base: string, signal: AbortSignal) {
  const directory = resolve(repo, '.sandcastle/worktrees');
  const path = resolve(directory, branch.replaceAll('/', '-'));
  if (existsSync(path)) throw new Error(`Worktree already exists; inspect preserved results: ${path}`);
  mkdirSync(directory, { recursive: true });
  await new Promise<void>((accept, reject) => {
    execFile('git', ['-C', repo, 'worktree', 'add', '-b', branch, path, base],
      { signal, killSignal: 'SIGTERM', maxBuffer: 1024 * 1024 }, error => error ? reject(error) : accept());
  });
  return path;
}
