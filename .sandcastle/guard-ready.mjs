import { lstatSync, statSync, realpathSync, accessSync, constants } from 'node:fs';
import { spawnSync } from 'node:child_process';
const root = '/opt/sandcastle/git-guard';
try {
  for (const path of ['/opt', '/opt/sandcastle', root, ...['git-wrapper.mjs', 'git-policy.mjs', 'guard-ready.mjs', 'real', 'real/git'].map(x => `${root}/${x}`)]) {
    const stat = lstatSync(path);
    if (stat.uid !== 0 || (stat.mode & 0o022)) throw new Error('installation ownership or write permissions are unsafe');
    try { accessSync(path, constants.W_OK); throw new Error('agent can write guard installation'); }
    catch (error) { if (error.code !== 'EACCES') throw error; }
  }
  for (const path of ['/usr/bin/git', '/usr/lib/git-core/git']) {
    if (realpathSync(path) !== `${root}/git-wrapper.mjs`) throw new Error('Git entry is not protected');
    const parent = statSync(path.slice(0, path.lastIndexOf('/')));
    if (parent.uid !== 0 || (parent.mode & 0o022)) throw new Error('Git entry directory is writable');
  }
  for (const binary of ['git', '/usr/bin/git', '/usr/lib/git-core/git']) {
    const denied = spawnSync(binary, ['push'], { encoding: 'utf8' });
    if (denied.status !== 2 || !denied.stderr?.includes('SANDCASTLE_GIT_BLOCKED [push]')) throw new Error('protected entry did not reject push');
  }
  const version = spawnSync('/usr/bin/git', ['--version'], { encoding: 'utf8' });
  if (version.status !== 0 || !version.stdout.startsWith('git version ')) throw new Error('normal Git entry failed');
  console.log('Sandcastle Git guard ready: root-owned installation; push denied; normal Git available.');
} catch (error) {
  console.error(`SANDCASTLE_GIT_GUARD_UNAVAILABLE: ${error.message}. Select the git-safe image; coding will not start.`);
  process.exit(2);
}
