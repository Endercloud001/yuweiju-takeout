// This policy reduces Git accidents; alternate binaries and filesystem writes
// remain outside its boundary. Never print raw arguments (they can contain secrets).
export class GitDenied extends Error {
  constructor(operation, reason) {
    super(`SANDCASTLE_GIT_BLOCKED [${operation}]: ${reason}. Preserve this worktree, record the blocker in progress, and ask the maintainer to handle it. Do not retry through another binary, shell, or configuration.`);
  }
}
const deny = (operation, reason) => { throw new GitDenied(operation, reason); };
const valueGlobals = new Set(['-C', '-c', '--git-dir', '--work-tree', '--namespace', '--config-env']);
const boolGlobals = new Set(['--no-pager', '--paginate', '-p', '-P', '--no-optional-locks', '--literal-pathspecs', '--glob-pathspecs', '--noglob-pathspecs', '--icase-pathspecs', '--no-replace-objects', '--bare']);
export function splitInvocation(argv) {
  const global = [];
  let index = 0;
  for (; index < argv.length && argv[index].startsWith('-'); index++) {
    const token = argv[index];
    if (valueGlobals.has(token)) {
      if (index + 1 >= argv.length) deny('arguments', 'missing global option value');
      global.push(token, argv[++index]);
    } else if (boolGlobals.has(token)) global.push(token);
    else if ([...valueGlobals].filter(x => x.startsWith('--')).some(x => token.startsWith(x + '=')) || /^-(C|c).+/.test(token)) global.push(token);
    else if (['--version', '-v', '--help', '-h', '--html-path', '--man-path', '--info-path', '--exec-path'].includes(token) && argv.length === 1) return { global: [], command: '', args: argv };
    else deny('arguments', 'unsupported global option; execution cannot be classified safely');
  }
  return { global, command: argv[index] ?? '', args: argv.slice(index + 1) };
}
// Git string aliases use whitespace, single/double quotes and backslash escaping.
export function aliasWords(value) {
  const result = [];
  let word = '', quote = '', started = false;
  for (let i = 0; i < value.length; i++) {
    const ch = value[i];
    if (ch === '\\' && quote !== "'") {
      if (++i === value.length) deny('alias', 'invalid alias quoting');
      word += value[i]; started = true;
    } else if (quote) {
      if (ch === quote) quote = ''; else word += ch;
    } else if (ch === '"' || ch === "'") { quote = ch; started = true; }
    else if (/\s/.test(ch)) { if (started) result.push(word); word = ''; started = false; }
    else { word += ch; started = true; }
  }
  if (quote) deny('alias', 'invalid alias quoting');
  if (started) result.push(word);
  if (!result.length) deny('alias', 'empty alias cannot be classified');
  return result;
}
const opts = args => args.slice(0, args.indexOf('--') < 0 ? args.length : args.indexOf('--'));
const short = (args, flag) => opts(args).some(x => /^-[^-]/.test(x) && x.slice(1).includes(flag));
// Git accepts unambiguous abbreviations of long options, e.g. --hard -> --ha.
const long = (args, flag) => opts(args).some(x => x.startsWith('--') && x.length > 2 && flag.startsWith(x.split('=')[0]));
const dirtyPaths = (global, paths, git) => {
  for (const mode of [[], ['--cached']]) {
    const check = git([...global, 'diff', ...mode, '--quiet', '--no-ext-diff', '--no-textconv', '--', ...paths]);
    if (check.status === 1) return true;
    if (check.status !== 0) deny('restore', 'cannot inspect the selected files safely');
  }
  return false;
};
function restorePaths(args) {
  if (opts(args).some(x => x.startsWith('--pathspec-from-file'))) deny('restore', 'file-based pathspec needs maintainer review');
  const separator = args.indexOf('--');
  if (separator >= 0) return args.slice(separator + 1);
  const paths = [];
  for (let i = 0; i < args.length; i++) {
    const x = args[i];
    if (['--source', '-s', '--conflict'].includes(x)) { i++; continue; }
    if (!x.startsWith('-')) paths.push(x);
    else if (x.startsWith('--pathspec-from-file')) deny('restore', 'file-based pathspec needs maintainer review');
    else if (x === '-p' || long([x], '--patch')) deny('restore', 'interactive restoration needs maintainer review');
    else if (!['--staged', '-S', '--worktree', '-W', '--ours', '--theirs', '--merge', '-m', '--quiet', '-q', '--ignore-unmerged', '--ignore-skip-worktree-bits', '--no-overlay', '--overlay', '--recurse-submodules', '--no-recurse-submodules', '--progress', '--no-progress'].includes(x) && !/^--(source|conflict)=/.test(x) && !/^-s.+/.test(x)) deny('restore', 'unsupported restoration option');
  }
  return paths;
}
const builtins = new Set(['push', 'reset', 'clean', 'branch', 'checkout', 'restore', 'status', 'diff', 'show', 'log', 'add', 'commit', 'config', 'rev-parse', 'init', 'clone', 'fetch', 'help', 'version', 'ls-files', 'ls-tree', 'cat-file', 'check-ref-format', 'check-ignore', 'worktree', 'reflog', 'merge', 'rebase', 'switch', 'tag', 'stash', 'update-ref', 'gc', 'apply', 'cherry-pick']);
export function checkGit(argv, git, depth = 0) {
  if (depth > 10) deny('alias', 'recursive alias');
  const { global, command, args } = splitInvocation(argv);
  if (!command) return;
  if (!builtins.has(command)) {
    // Query the installed Git rather than treating every command omitted from
    // our fast-path list as an external helper (e.g. ls-remote, describe, pull).
    const mainCommands = git(['--list-cmds=main']);
    if (mainCommands.status === 0 && mainCommands.stdout.split(/\s+/).includes(command)) return;
    const alias = git([...global, 'config', '--get', `alias.${command}`]);
    if (alias.status === 0) {
      const expansion = alias.stdout.trim();
      if (expansion.startsWith('!')) deny('alias', 'shell aliases cannot be classified before execution');
      return checkGit([...global, ...aliasWords(expansion), ...args], git, depth + 1);
    }
    if (alias.status !== 1) deny('alias', 'cannot read alias configuration safely');
    // An external git-command could execute arbitrary code; do not call it.
    deny('command', 'unknown Git subcommand needs maintainer review');
  }
  if (command === 'push') deny('push', 'remote writes are reserved for an authorized host operation');
  if (command === 'reset' && long(args, '--hard')) deny('reset', 'hard reset can discard uncommitted work');
  if (command === 'clean' && !(short(args, 'n') || long(args, '--dry-run'))) deny('clean', 'clean can delete untracked progress and task files');
  if (command === 'branch' && (short(args, 'D') || ((short(args, 'd') || long(args, '--delete')) && (short(args, 'f') || long(args, '--force'))))) deny('branch', 'forced branch deletion can remove retained task references');
  if (command === 'branch' && (short(args, 'f') || long(args, '--force') || short(args, 'M') || short(args, 'C'))) deny('branch', 'forced branch rewriting can replace retained task references');
  if (command === 'checkout') {
    if (short(args, 'f') || long(args, '--force') || short(args, 'B')) deny('checkout', 'forced checkout can discard files or rewrite branch references');
    if (args.some(x => x.startsWith('--pathspec-from-file')) || short(args, 'p') || long(args, '--patch')) deny('checkout', 'interactive or file-based checkout needs maintainer review');
    let paths;
    const separator = args.indexOf('--');
    if (separator >= 0) paths = args.slice(separator + 1);
    else {
      // Branch creation and ordinary branch switching are left to Git's own
      // conflict checks. Explicit path restoration must inspect both diffs.
      if (short(args, 'b') || long(args, '--orphan')) return;
      const tokens = args.filter(x => !x.startsWith('-'));
      if (!tokens.length) return;
      const revision = git([...global, 'rev-parse', '--verify', '--quiet', '--end-of-options', `${tokens[0]}^{commit}`]);
      paths = revision.status === 0 ? tokens.slice(1) : tokens;
      if (revision.status === 0 && !paths.length) return;
    }
    if (dirtyPaths(global, paths, git)) deny('checkout', 'checkout would overwrite modified or staged files');
  }
  if (command === 'restore') {
    const paths = restorePaths(args);
    const stagedOnly = (short(args, 'S') || long(args, '--staged')) && !(short(args, 'W') || long(args, '--worktree'));
    if (!stagedOnly && dirtyPaths(global, paths, git)) deny('restore', 'restore would overwrite modified or staged files');
  }
  if (command === 'switch' && (short(args, 'f') || long(args, '--force') || short(args, 'C') || long(args, '--force-create') || long(args, '--discard-changes'))) deny('switch', 'forced switching can discard files or rewrite branch references');
}
