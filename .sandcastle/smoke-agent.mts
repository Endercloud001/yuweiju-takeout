import { spawn, execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
const [scenario, key] = process.argv.slice(2);
const emit = (text: string) => console.log(JSON.stringify({ type: 'text', text }));
if (!['exit7', 'incomplete', 'complete', 'cancel', 'deadline', 'sigint', 'sigterm', 'idle', 'missingcommit'].includes(scenario)) throw new Error('Unknown local scenario');
if (scenario !== 'incomplete' && scenario !== 'idle' && scenario !== 'missingcommit') {
  writeFileSync('smoke-committed.txt', `committed:${key}\n`);
  execFileSync('git', ['add', '--', 'smoke-committed.txt']);
  execFileSync('git', ['commit', '-m', `test: Sandcastle local smoke ${scenario}`], { stdio: 'ignore' });
  writeFileSync('smoke-uncommitted.txt', `uncommitted:${key}\n`);
}
writeFileSync(`/smoke-evidence/${key}-agent.json`, JSON.stringify({ pid: process.pid, scenario }));
if (scenario === 'exit7') { emit('local exit 7'); process.exit(7); }
if (scenario === 'incomplete') { emit('normal exit without completion'); process.exit(0); }
if (scenario === 'missingcommit') { writeFileSync('.sandcastle/task-progress.md', 'No-model missing commit fixture'); emit('<promise>COMPLETE</promise>'); process.exit(0); }
if (scenario === 'complete') { emit('<promise>COMPLETE</promise>'); process.exit(0); }
const heartbeat = `/smoke-evidence/${key}-heartbeat.txt`;
const child = spawn(process.execPath, ['-e', 'const fs=require("node:fs");setInterval(()=>fs.writeFileSync(process.argv[1],String(Date.now())),100)', heartbeat], { stdio: 'ignore' });
writeFileSync(`/smoke-evidence/${key}-agent.json`, JSON.stringify({ pid: process.pid, childPid: child.pid, scenario }));
if (scenario !== 'idle') emit(`smoke-ready:${key}`);
setInterval(() => {}, 1000);
