import type { RunResult } from '@ai-hero/sandcastle';
import { join } from 'node:path';

type ResultDetails = Pick<RunResult, 'completionSignal' | 'branch' | 'commits' | 'logFilePath' | 'preservedWorktreePath'>;

export function resultSummary(result: ResultDetails, resourceFile: string, progressFile = '.sandcastle/task-progress.md') {
  const agentReportedComplete = Boolean(result.completionSignal);
  const hasTaskCommit = result.commits.length > 0;
  const status = !agentReportedComplete ? 'incomplete' : hasTaskCommit
    ? 'agent-reported-complete-awaiting-independent-review' : 'incomplete-missing-task-commit';
  return {
    status, agentReportedComplete, hasTaskCommit,
    branch: result.branch, commits: result.commits, log: result.logFilePath,
    preservedWorktreePath: result.preservedWorktreePath,
    progressFile: result.preservedWorktreePath ? join(result.preservedWorktreePath, progressFile) : undefined,
    resourceFile, independentVerification: 'not-performed', humanAcceptance: 'not-performed',
    nextStep: status === 'agent-reported-complete-awaiting-independent-review'
      ? 'independent-verification-and-maintainer-review'
      : status === 'incomplete-missing-task-commit'
        ? 'inspect-retained-files-and-missing-commit; no-automatic-rerun'
        : 'inspect-progress-and-blockers; await-new-manual-authorization',
  };
}

export function failureSummary(error: unknown, diagnosticsDirectory: string) {
  // Full failure text remains in boundedRun's local log/result/resource records.
  return {
    status: 'failed', error: String(error).split('\n', 1)[0].slice(0, 500),
    diagnosticsDirectory, independentVerification: 'not-performed', humanAcceptance: 'not-performed',
    nextStep: 'inspect-local-failure-record-and-preserved-worktree; await-new-manual-authorization',
  };
}
