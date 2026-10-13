After initial checks pass, read /home/agent/task-input/.agents/skills/yuweiju-ponytail/SKILL.md and complete Ponytail for all task changes from the configured startCommit. Explicitly stop Ponytail, rerun affected required checks, inspect scope, stage only task files and save the final successful candidate commit. Keep progress, failed checks and unfinished items within the original budget.

Only after all required work, checks and the task commit are complete, emit the following exact line alone as the last output line:
<promise>COMPLETE</promise>

Plain COMPLETE does not satisfy the supervisor. Missing materials, failed checks or unfinished work remain incomplete; preserve the candidate and report the blocker without emitting the promise. This signal reports agent completion; independent verification and human acceptance remain separate.
