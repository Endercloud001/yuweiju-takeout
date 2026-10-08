import { spawn } from 'node:child_process';
import { resolve } from 'node:path';
import { repo } from './common.mts';
// Separate process group: terminal signals go to this supervisor. The upstream
// synchronous shutdown registry remains installed in the worker as a fallback.
export async function supervise(entry: string, args: string[] = [], signalOnReady?: 'SIGINT' | 'SIGTERM') {
  const child = spawn(process.execPath, ['--import', resolve(repo, 'node_modules/tsx/dist/loader.mjs'), resolve(repo, '.sandcastle', entry), ...args],
    { cwd: repo, detached: true, stdio: ['ignore', 'inherit', 'inherit', 'ipc'] });
  const cancel = (reason: string) => { if (child.connected) child.send({ type: 'cancel', reason }); };
  const sigint = () => cancel('Host SIGINT'); const sigterm = () => cancel('Host SIGTERM');
  process.on('SIGINT', sigint); process.on('SIGTERM', sigterm);
  child.on('message', (message: any) => { if (message?.type === 'smoke-ready' && signalOnReady) process.kill(process.pid, signalOnReady); });
  try {
    return await new Promise<number>((accept, reject) => {
      child.once('error', reject);
      child.once('exit', (code, signal) => { if (signal) reject(new Error(`Worker terminated by ${signal}`)); else accept(code ?? 1); });
    });
  } finally { process.off('SIGINT', sigint); process.off('SIGTERM', sigterm); }
}
