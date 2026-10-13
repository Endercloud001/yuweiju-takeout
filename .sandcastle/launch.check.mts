import assert from 'node:assert/strict';
import { mkdirSync, readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { supervise } from './launch.mts';
import { repo, evidence } from './common.mts';

const directory = resolve(repo, '.scratch/host-fixture');
mkdirSync(directory, { recursive: true });
mkdirSync(evidence, { recursive: true });
const entry = '../.scratch/host-fixture/worker.mts';
writeFileSync(resolve(directory, 'worker.mts'), `
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
const scenario = process.argv[2];
const root = process.env.SANDCASTLE_EVIDENCE!;
process.stdout.write('宿主输出\\ntok');
setTimeout(() => {
  process.stdout.write('en=fixture-sensitive\\ntrailing-output');
  process.stderr.write('engine-disconnected\\n');
  writeFileSync(resolve(root, scenario+'-'+process.pid+'-other-resources.json'), JSON.stringify({workerPid: -1, stopped: true}));
  if (scenario === 'stopped') writeFileSync(resolve(root, scenario+'-'+process.pid+'-resources.json'), JSON.stringify({workerPid: process.pid, stopped: true}));
  if (scenario === 'lost') writeFileSync(resolve(root, scenario+'-'+process.pid+'-resources.json'), JSON.stringify({workerPid: process.pid, stopped: false, cleanupFailure:'unreachable'}));
  if (scenario === 'corrupt') {
    const file = resolve(root, scenario+'-'+process.pid+'-resources.json');
    writeFileSync(file, JSON.stringify({workerPid: process.pid, stopped: true}));
    setTimeout(() => { writeFileSync(file, '{incomplete'); process.exitCode = 7; }, 5200);
    return;
  }
  if (scenario === 'signal') process.kill(process.pid, 'SIGTERM');
  else process.exitCode = scenario === 'stopped' ? 0 : 7;
}, 20);
`);
for (const scenario of ['missing', 'lost', 'stopped', 'signal', 'corrupt']) {
  const before = new Set(readdirSync(evidence));
  if (scenario === 'signal') await assert.rejects(supervise(entry, [scenario]), /SIGTERM/);
  else assert.equal(await supervise(entry, [scenario]), scenario === 'stopped' ? 0 : 7);
  const name = readdirSync(evidence).find(name => !before.has(name) && name.startsWith('host-'))!;
  assert.ok(name, 'Host record must survive a nonzero worker exit with no resource record');
  const host = resolve(evidence, name);
  const record = JSON.parse(readFileSync(resolve(host, 'host.json'), 'utf8'));
  assert.equal(record.resourceState, scenario === 'stopped' ? 'last-known-stopped' : 'unknown');
  assert.equal(record.resources.length, ['stopped', 'lost', 'corrupt'].includes(scenario) ? 1 : 0);
  if (scenario === 'corrupt') {
    assert.equal(record.resources[0].lastKnown.stopped, true);
    assert.ok(record.resources[0].readError);
  }
  assert.equal(record.exitCode, scenario === 'signal' ? null : scenario === 'stopped' ? 0 : 7);
  assert.equal(record.signal, scenario === 'signal' ? 'SIGTERM' : null);
  const stdout = readFileSync(resolve(host, 'stdout.log'), 'utf8');
  assert.match(stdout, /宿主输出/);
  assert.match(stdout, /token=\[REDACTED\]/);
  assert.match(stdout, /trailing-output$/);
  assert.ok(!stdout.includes('fixture-sensitive'));
  assert.match(readFileSync(resolve(host, 'stderr.log'), 'utf8'), /engine-disconnected/);
}
console.log('Host supervisor: 5 scenarios passed; no model, auth or containers.');
