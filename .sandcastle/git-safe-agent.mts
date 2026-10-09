import type { AgentProvider } from '@ai-hero/sandcastle';

export const guardReadyCommand = '/usr/local/bin/node /opt/sandcastle/git-guard/guard-ready.mjs';
export const gitSafetyInstructions = `Sandcastle Git safety:
Report repeated validation commands as check batches. The host counts orchestration iterations from result/resource records; keep the configured iteration and time limits unchanged.
Use ordinary Git inspection, explicit staging of authorized task files, and a normal local task commit.
Push, hard reset, destructive clean, forced branch operations, forced checkout/switch, and restoration over modified files are blocked.
On SANDCASTLE_GIT_BLOCKED, preserve the worktree and record the operation category, reason, and next step in progress. Do not log credentials or raw credential-bearing commands.
Do not bypass a rejection via another executable, shell, alias, configuration, or filesystem operation. Continue only independent authorized work; if the blocked operation is necessary, report the blocker and stop without the COMPLETE promise.
Host publication and exceptions require the maintainer's task authorization; do not change the installed policy.
`;

export function protectAgent(agent: AgentProvider, instruct = true): AgentProvider {
  return {
    ...agent,
    buildPrintCommand(options) {
      const command = agent.buildPrintCommand({ ...options, prompt: instruct ? `${gitSafetyInstructions}\n${options.prompt}` : options.prompt });
      // Check again after dependency hooks, immediately before the agent starts.
      // Silence readiness stdout so it cannot corrupt provider JSON streaming.
      return { ...command, command: `${guardReadyCommand} >/dev/null && ${command.command}` };
    },
  };
}
