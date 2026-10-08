import { resultSummary } from './result-summary.mts';
import assert from 'node:assert/strict';
import { supervise } from './launch.mts';
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync, mkdirSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { boundedRun, repo, evidence, quote } from './common.mts';
import type { AgentProvider } from '@ai-hero/sandcastle';
const scenarioArg = process.argv[2];
const scenarios = scenarioArg ? [scenarioArg] : ['exit7', 'incomplete', 'complete', 'cancel', 'deadline', 'sigint', 'sigterm', 'idle', 'missingcommit'];
const batch = Date.now().toString();
const git = (args: string[]) => execFileSync('git', ['-C', repo, ...args], { encoding: 'utf8' }).trim();
for (const scenario of scenarios) {
  if ((scenario === 'sigint' || scenario === 'sigterm') && !process.argv.includes('--worker')) {
    const status = await supervise('smoke-run.mts', [scenario, '--worker'], scenario === 'sigint' ? 'SIGINT' : 'SIGTERM');
    assert.equal(status, 0, 'Signal worker must complete cleanup and assertions');
    continue;
  }
  const key = `${scenario}-${batch}`;
  const branch = `codex/afk-smoke-${key}`;
  const controller = new AbortController();
  let ready = false;
  const messageHandler = (message: any) => { if (message?.type === 'cancel') controller.abort(new Error(message.reason)); };
  process.on('message', messageHandler);
  const provider: AgentProvider = {
    name: 'local-smoke', env: {}, captureSessions: false,
    buildPrintCommand: () => ({ command: `node --experimental-strip-types .sandcastle/smoke-agent.mts ${quote(scenario)} ${quote(key)}` }),
    parseStreamLine(line) {
      const event = JSON.parse(line);
      if (event.type === 'text' && typeof event.text === 'string') {
        if (event.text === `smoke-ready:${key}`) {
          ready = true;
          const files = readdirSync(evidence).filter(f => f.startsWith(branch.replaceAll('/', '-') + '-') && f.endsWith('-resources.json'));
          assert.equal(files.length, 1);
          const resource = JSON.parse(readFileSync(resolve(evidence, files[0]), 'utf8'));
          resource.agentProcesses = execFileSync('docker', ['top', resource.containerId, '-eo', 'pid,args'], { encoding: 'utf8' });
          writeFileSync(resolve(evidence, files[0]), JSON.stringify(resource, null, 2));
          if (scenario === 'cancel') setTimeout(() => controller.abort(new Error('Requested smoke cancellation')), 500);
          if (scenario === 'sigint' || scenario === 'sigterm') process.send?.({ type: 'smoke-ready' });
        }
        return [{ type: 'text', text: event.text }];
      }
      return [];
    },
  };
  let outcome; let error: unknown;
  try { outcome = await boundedRun(provider, branch, true, {
    idleTimeoutSeconds: scenario === 'idle' ? 1 : 10,
    completionTimeoutSeconds: 1,
  }, scenario === 'deadline' ? 5000 : 30000, controller); }
  catch (e) { error = e; }
  const records = readdirSync(evidence).filter(f => f.startsWith(branch.replaceAll('/', '-') + '-') && f.endsWith('-resources.json'));
  assert.equal(records.length, 1, 'Actual Docker resource must be recorded');
  const resource = JSON.parse(readFileSync(resolve(evidence, records[0]), 'utf8'));
  assert.equal(resource.stopped, true, 'Container cleanup must be verified');
  const inspect = (() => { try { return execFileSync('docker', ['inspect', resource.containerId], { encoding: 'utf8', stdio: ['ignore','pipe','pipe'] }); } catch { return null; } })();
  assert.equal(inspect, null, 'Specific container must have been removed');
  const processes = execFileSync('ps', ['-eo', 'pid,args'], { encoding: 'utf8' }).split('\n');
  assert.equal(processes.some(p => p.includes('docker exec') && (p.includes(resource.containerId) || p.includes(resource.containerName.slice(1)))), false, 'Host docker exec must have exited');
  if (scenario === 'exit7') { assert.ok(error); assert.match(String(error), /exited with code 7/); }
  else if (scenario === 'incomplete') { assert.equal(error, undefined); assert.equal(outcome!.result.completionSignal, undefined); assert.equal(outcome!.result.iterations.length, 1); assert.equal(existsSync(resource.worktree), false); }
  else if (scenario === 'missingcommit') { assert.equal(error, undefined); assert.equal(resultSummary(outcome!.result, records[0]).status, 'incomplete-missing-task-commit'); assert.equal(existsSync(resource.worktree), true); }
  else if (scenario === 'complete') { assert.equal(error, undefined); assert.equal(outcome!.result.completionSignal, '<promise>COMPLETE</promise>'); assert.equal(outcome!.result.commits.length, 1); assert.equal(outcome!.result.preservedWorktreePath, resource.worktree); }
  else { assert.ok(error); if (scenario !== 'idle') { assert.equal(ready, true); assert.equal(controller.signal.aborted, true); } else assert.match(String(error), /idle|output|timeout/i); }
  const recovery = resolve(evidence, 'recovery', key);
  if (scenario !== 'incomplete' && scenario !== 'idle' && scenario !== 'missingcommit') {
    const head = git(['rev-parse', branch]);
    const content = git(['show', `${branch}:smoke-committed.txt`]);
    assert.equal(content, `committed:${key}`);
    assert.equal(existsSync(resource.worktree), true);
    assert.equal(readFileSync(resolve(resource.worktree, 'smoke-uncommitted.txt'), 'utf8'), `uncommitted:${key}\n`);
    mkdirSync(resolve(evidence, 'recovery'), { recursive: true });
    execFileSync('git', ['-C', repo, 'worktree', 'add', '--detach', recovery, head], { stdio: 'ignore' });
    assert.equal(readFileSync(resolve(recovery, 'smoke-committed.txt'), 'utf8'), `committed:${key}\n`);
    assert.equal(existsSync(resolve(recovery, 'smoke-uncommitted.txt')), false);
    writeFileSync(resolve(recovery, 'smoke-uncommitted.txt'), readFileSync(resolve(resource.worktree, 'smoke-uncommitted.txt')));
    assert.equal(readFileSync(resolve(recovery, 'smoke-uncommitted.txt'), 'utf8'), `uncommitted:${key}\n`);
  }
  const heartbeat = resolve(evidence, key + '-heartbeat.txt');
  if (existsSync(heartbeat)) {
    const before = readFileSync(heartbeat, 'utf8');
    await new Promise(r => setTimeout(r, 350));
    assert.equal(readFileSync(heartbeat, 'utf8'), before, 'Child process heartbeat must stop');
  }
  const verdict = { scenario, branch, passed: true, error: error ? String(error) : null,
    containerId: resource.containerId, worktree: resource.worktree, recovery,
    stopped: resource.stopped, committed: scenario !== 'incomplete' && scenario !== 'idle' && scenario !== 'missingcommit' ? git(['rev-parse', branch]) : null,
    resourceFile: resolve(evidence, records[0]), hostPid: process.pid };
  writeFileSync(resolve(evidence, key + '-verdict.json'), JSON.stringify(verdict, null, 2));
  console.log(JSON.stringify(verdict));
  process.off('message', messageHandler);
}

if (process.connected) process.disconnect();
