import { task, repoRoot } from './task-config.mts';
import { run, type AgentProvider, type RunOptions } from '@ai-hero/sandcastle';
import { docker } from '@ai-hero/sandcastle/sandboxes/docker';
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { protectAgent, guardReadyCommand } from './git-safe-agent.mts';
import { prepareWorktree } from './prepare-worktree.mts';
export const repo = repoRoot;
export const evidence = process.env.SANDCASTLE_EVIDENCE ?? resolve(repo, '.scratch/sandcastle-evidence');
export const image = task?.image ?? process.env.SANDCASTLE_IMAGE ?? 'sandcastle:yuweiju-dev-git-safe';
export const quote = (s: string) => "'" + s.replaceAll("'", "'\\''") + "'";
// Trusted dependency preparation keeps native Git semantics. This PATH is
// confined to the one hook process; the agent gets the default guarded entry.
export const prepareInstallCommand = (command: string) =>
  `env PATH=/opt/sandcastle/git-guard/real:"$PATH" /bin/bash -c ${quote(command)}`;
export const limits = { maxIterations: task?.maxIterations ?? 1, idleTimeoutSeconds: 600, completionTimeoutSeconds: 60 };
export const dockerProvider = (smoke = false, cacheDirectory?: string, iterationDirectory?: string) => docker({
  network: task?.networks,
  imageName: image, containerUid: 1000, containerGid: 1000,
  env: { LANG: 'C.UTF-8', LC_ALL: 'C.UTF-8' },
  mounts: [...(iterationDirectory ? [
    { hostPath: iterationDirectory, sandboxPath: '/afk-evidence' },
    { hostPath: resolve(repo, '.sandcastle'), sandboxPath: '/afk-tools', readonly: true },
  ] : []), ...(cacheDirectory ? [
    { hostPath: resolve(cacheDirectory, 'maven'), sandboxPath: '/home/agent/.m2' },
    { hostPath: resolve(cacheDirectory, 'npm'), sandboxPath: '/home/agent/.npm' },
  ] : []), ...(smoke ? [{ hostPath: evidence, sandboxPath: '/smoke-evidence' }, ...(task ? [{ hostPath: task.inputDirectory, sandboxPath: '/home/agent/task-input', readonly: true }] : [])] : [
    { hostPath: task!.authDirectory, sandboxPath: '/home/agent/.codex' },
    ...(task!.skillsDirectory ? [{ hostPath: task!.skillsDirectory, sandboxPath: '/home/agent/.agents/skills', readonly: true }] : []),
    { hostPath: task!.inputDirectory, sandboxPath: '/home/agent/task-input', readonly: true },
  ])],
});
export function matchingContainers(worktree: string) {
  const ids = execFileSync('docker', ['ps', '-aq'], { encoding: 'utf8' }).trim().split(/\s+/).filter(Boolean);
  if (!ids.length) return [];
  const entries = JSON.parse(execFileSync('docker', ['inspect', ...ids], { encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 }));
  return entries.filter((c: any) => c.Mounts.some((m: any) => m.Source === worktree && m.Destination === '/home/agent/workspace'));
}
export async function boundedRun(agent: AgentProvider, branch: string, smoke = false,
  options: Partial<RunOptions> = {}, totalMs = 30 * 60_000, suppliedController?: AbortController) {
  if (!smoke && !task) throw new Error('Explicit SANDCASTLE_TASK_CONFIG is required for a coding task');
  if (!smoke) {
    if (branch === 'codex/afk-issue-4') throw new Error('Completed pilot branch is reserved');
    execFileSync('git', ['-C', repo, 'check-ref-format', '--branch', branch]);
    execFileSync('git', ['-C', repo, 'rev-parse', '--verify', '--end-of-options', task!.startCommit + '^{commit}']);
    const refs = execFileSync('git', ['-C', repo, 'branch', '--list', branch], { encoding: 'utf8' }).trim();
    if (refs) throw new Error('Task branch already exists; inspect preserved results before any separately authorized resume');
  }
  const id = branch.replaceAll('/', '-') + '-' + Date.now();
  mkdirSync(evidence, { recursive: true });
  const resourceFile = resolve(evidence, id + '-resources.json');
  const iterationDirectory = resolve(evidence, id + '-iterations');
  mkdirSync(iterationDirectory, { recursive: true });
  const cacheDirectory = resolve(evidence, id + '-dependency-cache');
  for (const name of ['maven', 'npm']) mkdirSync(resolve(cacheDirectory, name), { recursive: true });
  const controller = suppliedController ?? new AbortController();
  const abort = (reason: string) => controller.abort(new Error(reason));
  const sigint = () => abort('Host SIGINT');
  const sigterm = () => abort('Host SIGTERM');
  process.on('SIGINT', sigint); process.on('SIGTERM', sigterm);
  const timer = setTimeout(() => abort('AFK total time limit reached'), totalMs);
  const deadline = Date.now() + totalMs;
  const closingMs = Math.min(task?.closingMs ?? 120_000, Math.floor(totalMs / 5));
  const closingTimer = setTimeout(() => {
    writeFileSync(resolve(iterationDirectory, 'closing.json'), JSON.stringify({ deadline,
      instruction: 'Save candidate commit, remaining changes, checks and unfinished items now; original deadline remains in force.' }));
    console.error('AFK closing window: preserve candidate and evidence before the original deadline.');
  }, totalMs - closingMs);
  const command = `${quote(process.execPath)} --import ${quote(resolve(repo, 'node_modules/tsx/dist/loader.mjs'))} ${quote(resolve(repo, '.sandcastle/resource-check.mts'))} ${quote(resourceFile)} ${quote(branch)}`;
  const started = new Date().toISOString();
  let result; let failure: unknown; let preparedWorktreePath: string | undefined;
  let iteration = 0;
  const guarded = protectAgent(options.agent ?? agent, !smoke);
  const retainedAgent: AgentProvider = { ...guarded, buildPrintCommand(args) {
    iteration++;
    const instructions = `This is supervisor iteration ${iteration}/${options.maxIterations ?? limits.maxIterations}. Previous iteration commits are already on this branch. Read prior evidence under /afk-evidence. The shared coding deadline is ${new Date(deadline).toISOString()}; installation and checks count inside it. Before that deadline save a candidate commit, remaining changes, checks and blockers. Check /afk-evidence/closing.json between work steps. COMPLETE requires actual task commits and passed checks.\n`;
    const original = guarded.buildPrintCommand({ ...args, prompt: smoke ? args.prompt : instructions + args.prompt });
    return { ...original, command: `node /afk-tools/iteration-agent.mjs ${quote('/afk-evidence/iteration-' + iteration)} ${quote(JSON.stringify(task?.evidencePaths ?? [task?.progressFile ?? '.sandcastle/task-progress.md']))} ${quote(String(deadline))} ${quote(original.command)}` };
  } };
  try {
    preparedWorktreePath = await prepareWorktree(repo, branch, task?.startCommit ?? 'HEAD', controller.signal);
    result = await run({ cwd: repo, sandbox: dockerProvider(smoke, cacheDirectory, iterationDirectory), ...limits,
      branchStrategy: { type: 'branch', branch, baseBranch: task?.startCommit },
      prompt: smoke ? 'Run the predetermined local smoke scenario only.' : undefined,
      promptFile: smoke ? undefined : resolve(task!.promptFile),
      ...options,
      agent: retainedAgent,
      logging: { type: 'file', path: resolve(evidence, id + '.log') },
      hooks: { host: { onSandboxReady: [{ command, timeoutMs: 30_000 }] },
        sandbox: { onSandboxReady: smoke ? [
          { command: guardReadyCommand, timeoutMs: 30_000 },
          { command: 'test "$(id -u)" = 1000', timeoutMs: 30_000 },
        ] : [
          { command: guardReadyCommand, timeoutMs: 30_000 },
          { command: 'codex login status', timeoutMs: 30_000 },
          ...task!.installCommands.map(command => ({ command: prepareInstallCommand(command), timeoutMs: 300_000 })),
        ] } }, signal: controller.signal,
    });
  } catch (error) { failure = error; }
  finally {
    clearTimeout(timer); clearTimeout(closingTimer); process.off('SIGINT', sigint); process.off('SIGTERM', sigterm);
    if (existsSync(resourceFile)) {
      const resource = JSON.parse(readFileSync(resourceFile, 'utf8'));
      const remaining = matchingContainers(resource.worktree);
      resource.stopped = remaining.length === 0;
      resource.iterationEvidence = iterationDirectory;
      resource.ended = new Date().toISOString();
      for (const iteration of resource.iterations ?? []) {
        const observed = execFileSync('docker', ['ps', '-aq', '--filter', 'id=' + iteration.containerId], { encoding: 'utf8' }).trim();
        iteration.stopped = !observed;
      }
      writeFileSync(resourceFile, JSON.stringify(resource, null, 2));
      if (remaining.length) failure = new Error(`Resource cleanup failed: ${remaining.map((c: any) => c.Id).join(',')}; inspect ${resourceFile}`);
    }
  }
  writeFileSync(resolve(evidence, id + '-result.json'), JSON.stringify({ pid: process.pid, branch, started,
    ended: new Date().toISOString(), resourceFile, aborted: controller.signal.aborted,
    preparedWorktreePath, iterationEvidence: iterationDirectory,
    budget: { totalMs, maxIterations: options.maxIterations ?? limits.maxIterations, closingMs, deadline, installationIncluded: true },
    codingStatus: controller.signal.aborted ? 'aborted' : failure ? 'failed' : result?.completionSignal && result.commits.length ? 'agent-reported-complete' : 'incomplete',
    independentVerification: 'not-performed', humanAcceptance: 'not-performed', delivery: 'not-performed',
    configuration: smoke ? { image, modelCalled: false, authMounted: false } : { branch, startCommit: task!.startCommit, image, promptFile: task!.promptFile, inputDirectory: task!.inputDirectory, checkCommands: task!.checkCommands },
    failure: failure ? String(failure) : null,
    result: result ? { branch: result.branch, commits: result.commits, completionSignal: result.completionSignal,
      iterations: result.iterations.length, preservedWorktreePath: result.preservedWorktreePath, logFilePath: result.logFilePath } : null }, null, 2));
  if (failure) throw failure;
  return { result: result!, resourceFile };
}
