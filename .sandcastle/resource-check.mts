import { matchingContainers } from './common.mts';
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
const [file, branch] = process.argv.slice(2);
const entries = matchingContainers(process.cwd());
if (entries.length !== 1) throw new Error(`Expected exactly one container for ${process.cwd()}, got ${entries.length}`);
const c = entries[0];
const previous = existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : {};
const record = { branch, hookPid: process.pid, worktree: process.cwd(),
  containerId: c.Id, containerName: c.Name, containerHostPid: c.State.Pid,
  mounts: c.Mounts.map((m: any) => ({ source: m.Source, destination: m.Destination, writable: m.RW })),
  privileged: c.HostConfig.Privileged, devices: c.HostConfig.Devices,
  started: new Date().toISOString(), environment: {
    locale: execFileSync('docker', ['exec', c.Id, 'locale'], { encoding: 'utf8' }).trim(),
    java: execFileSync('docker', ['exec', c.Id, 'sh', '-c', 'command -v java; java -XshowSettings:properties -version 2>&1 | sed -n \"/java.home =/p;/java.version =/p;/native.encoding =/p;/sun.jnu.encoding =/p\"'], { encoding: 'utf8' }).trim(),
  }, processes: execFileSync('docker', ['top', c.Id, '-eo', 'pid,args'], { encoding: 'utf8' }) };
writeFileSync(file, JSON.stringify({ ...record, iterations: [...(previous.iterations ?? []), record] }, null, 2));
