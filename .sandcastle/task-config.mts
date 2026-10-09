import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
export type TaskConfig = { branch: string; startCommit: string; promptFile: string; inputDirectory: string;
  image: string; checkCommands: string[]; installCommands: string[]; authDirectory: string; skillsDirectory?: string;
  networks?: string[]; totalMs?: number; maxIterations?: number; progressFile?: string;
  verificationMs?: number; closingMs?: number; evidencePaths?: string[];
  preflightUrls?: string[]; preflightServices?: { host: string; port: number }[] };
export const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
export const task: TaskConfig | undefined = process.env.SANDCASTLE_TASK_CONFIG
  ? JSON.parse(readFileSync(resolve(process.env.SANDCASTLE_TASK_CONFIG), 'utf8')) : undefined;
if (task) {
  for (const key of ['verificationMs', 'closingMs'] as const)
    if (task[key] !== undefined && (!Number.isSafeInteger(task[key]) || task[key]! < 1 || task[key]! > 2147483647))
      throw new Error(`${key} must be an integer from 1 to 2147483647`);
  if (task.closingMs !== undefined && task.closingMs >= (task.totalMs ?? 1800000))
    throw new Error('closingMs must fit inside totalMs');
  for (const key of ['networks', 'evidencePaths', 'preflightUrls'] as const)
    if (task[key] !== undefined && (!Array.isArray(task[key]) || task[key]!.some(value => typeof value !== 'string' || !value)))
      throw new Error(`${key} must be an array of nonempty strings`);
  if (task.evidencePaths?.some(path => path.startsWith('/') || path.split(/[\\/]/).includes('..')))
    throw new Error('evidencePaths must remain relative to the worktree');
  if (task.maxIterations !== undefined && (!Number.isSafeInteger(task.maxIterations) || task.maxIterations < 1))
    throw new Error('maxIterations must be a positive safe integer');
  if (task.totalMs !== undefined && (!Number.isSafeInteger(task.totalMs) || task.totalMs < 1 || task.totalMs > 2147483647))
    throw new Error('totalMs must be an integer from 1 to 2147483647');
  for (const key of ['branch', 'startCommit', 'promptFile', 'inputDirectory', 'image', 'authDirectory'] as const)
    if (!task[key] || typeof task[key] !== 'string') throw new Error(`Missing task setting: ${key}`);
  if (!Array.isArray(task.checkCommands) || !task.checkCommands.length || !Array.isArray(task.installCommands))
    throw new Error('Explicit installation and check commands are required');
}
