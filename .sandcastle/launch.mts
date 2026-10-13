import { spawn } from 'node:child_process';
import { resolve } from 'node:path';
import { appendFileSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { StringDecoder } from 'node:string_decoder';
import { repo, evidence } from './common.mts';

// Same common-secret boundary as save-iteration.py; logs still need review
// before sharing. Buffer whole lines so split stream chunks cannot expose values.
const redact = (value: string) => value
  .replace(/(authorization\s*[:=]\s*)[^\r\n]+/gim, '$1[REDACTED]')
  .replace(/("(?:password|passwd|token|secret|api[_-]?key|openid|authorization)"\s*:\s*)"(?:[^"\\]|\\.)*"/gi, '$1"[REDACTED]"')
  .replace(/(bearer\s+)[\w.~-]+/gi, '$1[REDACTED]')
  .replace(/((?:password|passwd|token|secret|api[_-]?key|openid|authorization)["\s]*[:=]["\s]*)[^\r\n,;"}]+/gi, '$1[REDACTED]')
  .replace(/(https?:\/\/)[^/\s:@]+:[^/\s@]+@/g, '$1[REDACTED]@');
// Separate process group: terminal signals go to this supervisor. The upstream
// synchronous shutdown registry remains installed in the worker as a fallback.
export async function supervise(entry: string, args: string[] = [], signalOnReady?: 'SIGINT' | 'SIGTERM') {
  mkdirSync(evidence, { recursive: true });
  const previous = new Set(readdirSync(evidence));
  const directory = mkdtempSync(resolve(evidence, 'host-'));
  const record: { [key: string]: any } = { entry, startedAt: new Date().toISOString(),
    hostPid: process.pid, exitCode: null, signal: null, resourceState: 'unknown', resources: [] };
  const retained = new Map<string, any>();
  const save = () => {
    const names = new Set([...retained.keys(), ...readdirSync(evidence).filter(name => !previous.has(name) && name.endsWith('-resources.json'))]);
    for (const name of names) {
      try {
        const value = JSON.parse(redact(readFileSync(resolve(evidence, name), 'utf8')));
        if (value.workerPid === record.workerPid)
          retained.set(name, { source: resolve(evidence, name), observedAt: new Date().toISOString(), lastKnown: value });
      } catch (error) {
        if (retained.has(name)) retained.set(name, { ...retained.get(name), readError: redact(String(error)) });
      }
    }
    record.resources = [...retained.values()];
    record.resourceState = record.resources.length && record.resources.every((r: any) => !r.readError && r.lastKnown?.stopped === true)
      ? 'last-known-stopped' : 'unknown';
    writeFileSync(resolve(directory, 'host.json'), JSON.stringify(record, null, 2));
  };
  save();
  const child = spawn(process.execPath, ['--import', resolve(repo, 'node_modules/tsx/dist/loader.mjs'), resolve(repo, '.sandcastle', entry), ...args],
    { cwd: repo, detached: true, stdio: ['ignore', 'pipe', 'pipe', 'ipc'] });
  record.workerPid = child.pid;
  const capture = (name: string, stream: NodeJS.ReadableStream, target: NodeJS.WriteStream) => {
    const file = resolve(directory, name + '.log');
    writeFileSync(file, '');
    const decoder = new StringDecoder('utf8');
    let pending = '';
    const flush = () => { appendFileSync(file, redact(pending)); pending = ''; };
    stream.on('data', (chunk: Buffer) => {
      target.write(chunk);
      pending += decoder.write(chunk);
      let end;
      while ((end = pending.indexOf('\n')) >= 0) {
        appendFileSync(file, redact(pending.slice(0, end + 1)));
        pending = pending.slice(end + 1);
      }
    });
    stream.on('end', () => { pending += decoder.end(); flush(); });
  };
  capture('stdout', child.stdout, process.stdout);
  capture('stderr', child.stderr, process.stderr);
  const sample = setInterval(save, 5000);
  const cancel = (reason: string) => { if (child.connected) child.send({ type: 'cancel', reason }); };
  const sigint = () => cancel('Host SIGINT'); const sigterm = () => cancel('Host SIGTERM');
  process.on('SIGINT', sigint); process.on('SIGTERM', sigterm);
  child.on('message', (message: any) => { if (message?.type === 'smoke-ready' && signalOnReady) process.kill(process.pid, signalOnReady); });
  try {
    return await new Promise<number>((accept, reject) => {
      child.once('error', error => { record.spawnError = redact(String(error)); reject(error); });
      // close follows drained output streams; exit alone can miss trailing bytes.
      child.once('close', (code, signal) => {
        record.exitCode = code; record.signal = signal;
        if (signal) reject(new Error(`Worker terminated by ${signal}`)); else accept(code ?? 1);
      });
    });
  } finally {
    clearInterval(sample);
    record.endedAt = new Date().toISOString(); save();
    process.off('SIGINT', sigint); process.off('SIGTERM', sigterm);
  }
}
