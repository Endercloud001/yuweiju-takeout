import { task } from './task-config.mts';
import { codex } from '@ai-hero/sandcastle';
import { boundedRun, evidence } from './common.mts';
import { resultSummary, failureSummary } from './result-summary.mts';
import { spawn } from 'node:child_process';
import { resolve } from 'node:path';
import { repo } from './common.mts';
const controller = new AbortController();
const messageHandler = (message: any) => { if (message?.type === 'cancel') controller.abort(new Error(message.reason)); };
process.on('message', messageHandler);
if (!task) throw new Error('Explicit task configuration required; do not run this entry for environment checks');
const agent = codex('gpt-6.1-sol', { effort: 'medium', captureSessions: false });
try {
  // Preflight is outside the coding budget and never consumes an iteration.
  const preparationExit = await new Promise<number>((accept, reject) => {
    const child = spawn('python3', [resolve(repo, '.sandcastle/preflight.py'), '--config', process.env.SANDCASTLE_TASK_CONFIG!, '--auth'], { stdio: 'inherit' });
    const cancel = () => child.kill('SIGTERM');
    controller.signal.addEventListener('abort', cancel, { once: true });
    if (controller.signal.aborted) cancel();
    child.once('error', reject);
    child.once('exit', code => { controller.signal.removeEventListener('abort', cancel); accept(code ?? 1); });
  });
  if (preparationExit !== 0 || controller.signal.aborted) throw new Error(`Preflight stopped before coding; exit ${preparationExit}. Inspect evidence; no automatic relaunch.`);
  const { result, resourceFile } = await boundedRun(agent, task.branch, false, {}, task.totalMs ?? 30 * 60_000, controller);
  const summary = resultSummary(result, resourceFile, task.progressFile);
  console.log(JSON.stringify(summary));
  if (summary.status !== 'agent-reported-complete-awaiting-independent-review') process.exitCode = 1;
} catch (error) { console.error(JSON.stringify(failureSummary(error, evidence))); process.exitCode = 1; }
finally { process.off('message', messageHandler); if (process.connected) process.disconnect(); }
