// Forward the provider stream unchanged; save ignored evidence before normal exit.
import { spawn, spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
const [directory, paths, deadline, command] = process.argv.slice(2);
mkdirSync(directory, { recursive: true });
const started = Date.now();
writeFileSync(`${directory}/started.json`, JSON.stringify({ started, deadline: Number(deadline) }));
const child = spawn('bash', ['-c', command], { stdio: ['inherit', 'pipe', 'inherit'] });
let complete = false;
let tail = '';
child.stdout.on('data', chunk => {
  tail = (tail + chunk.toString()).slice(-2048);
  complete ||= tail.includes('<promise>COMPLETE</promise>');
  process.stdout.write(chunk);
});
const save = (code, signal) => {
  const result = spawnSync('python3', ['/afk-tools/save-iteration.py', '--destination', directory,
    '--paths', paths, ...(code !== null ? ['--exit-code', String(code)] : []),
    ...(signal ? ['--signal', signal] : []), ...(complete ? ['--completion'] : [])], { stdio: ['ignore', 'ignore', 'pipe'] });
  if (result.status !== 0) process.stderr.write('Iteration evidence save failed; inspect host resources.\n');
  return result.status === 0;
};
// A periodic copy also retains evidence on cancellation or abrupt Docker close.
const sample = setInterval(() => save(null, null), 5000);
const term = () => child.kill('SIGTERM');
process.on('SIGTERM', term); process.on('SIGINT', term);
child.on('error', () => { clearInterval(sample); save(null, 'spawn-error'); process.exitCode = 1; });
child.on('close', (code, signal) => {
  clearInterval(sample);
  process.off('SIGTERM', term); process.off('SIGINT', term);
  const saved = save(code, signal);
  writeFileSync(`${directory}/ended.json`, JSON.stringify({ started, ended: Date.now(), code, signal }));
  process.exitCode = saved ? (code ?? 1) : 1;
});
