"""Copy selected text evidence before Sandcastle replaces an iteration worktree."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def redact(text):
    text = re.sub(r'(?im)(authorization\s*[:=]\s*)[^\r\n]+', r'\1[REDACTED]', text)
    text = re.sub(r'(?i)("(?:password|passwd|token|secret|api[_-]?key|openid|authorization)"\s*:\s*)"(?:[^"\\]|\\.)*"', r'\1"[REDACTED]"', text)
    text = re.sub(r'(?i)(bearer\s+)[\w.~-]+', r'\1[REDACTED]', text)
    text = re.sub(r'(?i)((?:password|passwd|token|secret|api[_-]?key|openid|authorization)["\s]*[:=]["\s]*)[^\r\n,;"}]+', r'\1[REDACTED]', text)
    return re.sub(r'(https?://)[^/\s:@]+:[^/\s@]+@', r'\1[REDACTED]@', text)


def save(root, destination, paths, exit_code=None, signal=None, completion=False):
    root = root.resolve()
    destination.mkdir(parents=True, exist_ok=True)
    skipped = []
    copied = []
    total = 0
    for relative in paths:
        source = root / relative
        if not source.resolve().is_relative_to(root):
            skipped.append({'path': relative, 'reason': 'outside-worktree'})
            continue
        files = source.rglob('*') if source.is_dir() else [source]
        for file in files:
            if not file.is_file():
                continue
            name = file.relative_to(root).as_posix()
            if file.is_symlink() or not file.resolve().is_relative_to(root) or any(
                part.lower() in {'.codex', 'auth.json', 'credentials.json', 'node_modules', '.git'} or part.lower().startswith('.env') for part in file.parts
            ) or file.suffix.lower() not in {'.log', '.json', '.md', '.txt', '.tsv'}:
                skipped.append({'path': name, 'reason': 'not-selected-text'})
                continue
            size = file.stat().st_size
            if size > 4 * 1024 * 1024 or total + size > 32 * 1024 * 1024 or len(copied) >= 500:
                skipped.append({'path': name, 'reason': 'size-limit'})
                continue
            try:
                text = file.read_text(encoding='utf-8')
            except (UnicodeError, OSError):
                skipped.append({'path': name, 'reason': 'unreadable-text'})
                continue
            target = destination / 'files' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(redact(text), encoding='utf-8', newline='\n')
            total += size
            copied.append(name)
    def git(*args):
        result = subprocess.run(['git', '-C', str(root), *args], capture_output=True, text=True)
        return {'exitCode': result.returncode, 'output': redact(result.stdout), 'error': redact(result.stderr)}
    record = {'savedAt': time.time(), 'worktree': str(root), 'agentExitCode': exit_code,
              'agentSignal': signal, 'completionTextObserved': completion,
              'head': git('rev-parse', 'HEAD'), 'status': git('status', '--short'),
              'files': copied, 'skipped': skipped}
    (destination / 'iteration.json').write_text(json.dumps(record, indent=2), encoding='utf-8', newline='\n')
    return record


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path.cwd())
    parser.add_argument('--destination', type=Path, required=True)
    parser.add_argument('--paths', required=True)
    parser.add_argument('--exit-code', type=int)
    parser.add_argument('--signal')
    parser.add_argument('--completion', action='store_true')
    args = parser.parse_args()
    save(args.root, args.destination, json.loads(args.paths), args.exit_code, args.signal, args.completion)
