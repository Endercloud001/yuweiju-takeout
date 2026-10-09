import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { boundedRun, evidence, repo, quote } from './common.mts';
import { protectAgent, gitSafetyInstructions } from './git-safe-agent.mts';
import type { AgentProvider } from '@ai-hero/sandcastle';

const scenario = process.argv[2];
if (!['complete', 'cancel', 'missing'].includes(scenario)) throw new Error('Unknown scenario');
const branch = `codex/git-guard-${scenario}-${Date.now()}`;
const controller = new AbortController();
let behaviorVerified = false;
const provider: AgentProvider = {
  name: 'git-guard-no-model', env: {}, captureSessions: false,
  buildPrintCommand: () => ({ command: `node .sandcastle/git-guard-agent.mjs ${quote(scenario)}` }),
  parseStreamLine(line) {
    // Readiness errors are not provider events; preparation should fail first.
    if (!line.startsWith('{')) return [];
    const event = JSON.parse(line);
    if (event.text === 'guard-behavior-verified') {
      behaviorVerified = true;
      if (scenario === 'cancel') setTimeout(() => controller.abort(new Error('Guard fixture cancellation')), 300);
    }
    return event.type === 'text' ? [{ type: 'text', text: event.text }] : [];
  },
};
// Verify generic safety instructions are added without changing task text/stdin.
const echoProvider = { ...provider, buildPrintCommand: (options: { prompt: string }) => ({ command: 'true', stdin: options.prompt }) };
assert.equal(protectAgent(echoProvider).buildPrintCommand({ prompt: 'TASK', dangerouslySkipPermissions: true }).stdin, `${gitSafetyInstructions}\nTASK`);
let outcome; let error: unknown;
try { outcome = await boundedRun(provider, branch, true, { idleTimeoutSeconds: 30, completionTimeoutSeconds: 1 }, 120_000, controller); }
catch (caught) { error = caught; }
const records = readdirSync(evidence).filter(x => x.startsWith(branch.replaceAll('/', '-') + '-') && x.endsWith('-resources.json'));
assert.equal(records.length, 1, `Docker readiness resource was not recorded: ${String(error)}`);
const resourceFile = resolve(evidence, records[0]);
const resource = JSON.parse(readFileSync(resourceFile, 'utf8'));
assert.equal(resource.stopped, true);
assert.equal(resource.mounts.some((m: any) => m.destination === '/home/agent/.codex'), false);
if (scenario === 'missing') {
  assert.ok(error, 'unprotected image must fail before the fixture starts');
  assert.equal(behaviorVerified, false);
  assert.equal(existsSync(resource.worktree), false);
} else {
  assert.equal(behaviorVerified, true);
  if (scenario === 'complete') {
    assert.equal(error, undefined);
    assert.equal(outcome!.result.completionSignal, '<promise>COMPLETE</promise>');
    assert.equal(outcome!.result.commits.length, 1);
  } else { assert.ok(error); assert.equal(controller.signal.aborted, true); }
  assert.equal(readFileSync(resolve(resource.worktree, 'guard-committed.txt'), 'utf8'), 'modified\n');
  assert.equal(readFileSync(resolve(resource.worktree, 'guard-untracked.txt'), 'utf8'), 'retained progress\n');
  assert.equal(execFileSync('git', ['-C', repo, 'show', `${branch}:guard-committed.txt`], { encoding: 'utf8' }), 'initial\n');
  if (scenario === 'cancel') {
    const heartbeat = resolve(evidence, 'guard-heartbeat.txt');
    const before = readFileSync(heartbeat, 'utf8');
    await new Promise(r => setTimeout(r, 350));
    assert.equal(readFileSync(heartbeat, 'utf8'), before);
  }
}
const verdict = { scenario, passed: true, authMounted: false, modelCalled: false, behaviorVerified, containerStopped: resource.stopped, worktree: resource.worktree, resourceFile, error: error ? String(error) : null };
writeFileSync(resolve(evidence, `guard-${scenario}-verdict.json`), JSON.stringify(verdict, null, 2));
console.log(JSON.stringify(verdict));
