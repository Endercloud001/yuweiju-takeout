import assert from 'node:assert/strict';
import { createSandbox } from '@ai-hero/sandcastle';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { dockerProvider, matchingContainers, evidence, repo } from './common.mts';
const branch = 'codex/afk-install-check-' + Date.now();
const sandbox = await createSandbox({ cwd: repo, branch, sandbox: dockerProvider() });
const containers = matchingContainers(sandbox.worktreePath);
assert.equal(containers.length, 1);
const container = containers[0];
let status: unknown;
try {
  assert.equal(container.HostConfig.Privileged, false);
  assert.deepEqual(container.HostConfig.Devices, []);
  assert.equal(container.Config.User, '1000:1000');
  const envNames = container.Config.Env.map((item: string) => item.split('=')[0]);
  for (const name of ['OPENAI_API_KEY', 'OPENAI_KEY', 'GH_TOKEN', 'GITHUB_TOKEN']) assert.equal(envNames.includes(name), false, name + ' must not be injected');
  assert.equal(container.Mounts.some((m: any) => m.Destination === '/var/run/docker.sock'), false);
  const auth = container.Mounts.find((m: any) => m.Destination === '/home/agent/.codex');
  const skills = container.Mounts.find((m: any) => m.Destination === '/home/agent/.agents/skills');
  const docs = container.Mounts.find((m: any) => m.Destination === '/home/agent/task-input');
  assert.equal(auth.RW, true); assert.equal(skills.RW, false); assert.equal(docs.RW, false);
  const check = await sandbox.exec('codex login status && test -w /home/agent/.codex/auth.json && test -r AGENTS.md && test -r yuweiju-web-vue/AGENTS.md && test -r docs/agents/workflow.md && test -r docs/standards/admin.md && test -r docs/yuweiju-restore-spec.md && test -r /home/agent/task-input/issue-4.md && test -r /home/agent/task-input/sandcastle-early-grilling.md && test -r /home/agent/task-input/sandcastle-early-research.md && test -r /home/agent/.agents/skills/diagnosing-bugs/scripts/hitl-loop.template.sh');
  assert.equal(check.exitCode, 0, check.stderr); assert.match(check.stderr + check.stdout, /ChatGPT/);
  const rpc = await sandbox.exec('node --input-type=module -', { stdin: readFileSync(resolve(repo, '.sandcastle/probe-client.mjs'), 'utf8') });
  assert.equal(rpc.exitCode, 0, rpc.stderr);
  const facts = JSON.parse(rpc.stdout);
  assert.equal(facts.config.model, 'gpt-6.1-sol');
  assert.equal(facts.config.model_reasoning_effort, 'medium');
  assert.equal(facts.config.service_tier, null);
  assert.equal(facts.config.forced_login_method, 'chatgpt');
  const loaded = facts.skills.flatMap((e: any) => e.skills).filter((s: any) => s.enabled).map((s: any) => s.name);
  for (const name of ['yuweiju-java-backend', 'yuweiju-stack', 'yuweiju-ui', 'diagnosing-bugs', 'verification-before-completion']) assert.ok(loaded.includes(name), `${name} not loaded`);
  for (const entry of facts.skills) assert.deepEqual(entry.errors, []);
  status = { passed: true, branch, worktree: sandbox.worktreePath, containerId: container.Id, pid: process.pid,
    user: container.Config.User, mounts: container.Mounts.map((m: any) => ({source:m.Source,destination:m.Destination,writable:m.RW})), facts };
} finally {
  await sandbox.close();
  assert.equal(matchingContainers(sandbox.worktreePath).length, 0);
}
writeFileSync(resolve(evidence, 'install-provider-check.json'), JSON.stringify({ ...status as object, stopped: true }, null, 2));
console.log(JSON.stringify({ passed: true, branch, containerId: container.Id, stopped: true, noInference: true }));
