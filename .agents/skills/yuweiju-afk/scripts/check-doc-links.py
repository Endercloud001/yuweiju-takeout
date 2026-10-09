"""Check relative inline Markdown file links without interpreting code as prose."""
import argparse
import json
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit


def prose_only(text):
    lines = []
    fence = None
    for line in text.splitlines(keepends=True):
        match = re.match(r'^[ \t]{0,3}(`{3,}|~{3,})(.*)$', line.rstrip('\r\n'))
        if fence:
            if match and match[1][0] == fence[0] and len(match[1]) >= len(fence) and not match[2].strip():
                fence = None
            lines.append('\n' if line.endswith('\n') else '')
        elif match:
            fence = match[1]
            lines.append('\n' if line.endswith('\n') else '')
        else:
            lines.append(line)
    return re.sub(r'(`+)(.*?)\1', lambda match: '\n' * match[0].count('\n'), ''.join(lines), flags=re.S)


def check(root, names):
    failures = []
    pattern = re.compile(r'''!?\[[^\]\n]*\]\(\s*(?:<([^>\n]+)>|([^\s)\n]+))(?:\s+["'][^\n]*?["'])?\s*\)''')
    for name in names:
        document = (root / name).resolve()
        if not document.is_file():
            failures.append({'file': name, 'reason': 'document does not exist'})
            continue
        prose = prose_only(document.read_text(encoding='utf-8-sig'))
        for match in pattern.finditer(prose):
            target = match[1] or match[2]
            parts = urlsplit(target)
            if parts.scheme or parts.netloc or not parts.path:
                continue
            path = unquote(parts.path)
            if not (document.parent / path).exists():
                failures.append({'file': name, 'line': prose.count('\n', 0, match.start()) + 1,
                                 'target': target, 'reason': 'local file does not exist'})
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path.cwd(), help='repository or fresh snapshot root')
    parser.add_argument('files', nargs='+', help='Markdown files relative to root')
    args = parser.parse_args()
    try:
        failures = check(args.root.resolve(), args.files)
    except (OSError, UnicodeError, ValueError) as error:
        failures = [{'reason': str(error)}]
    print(json.dumps({'files': args.files, 'failures': failures}, ensure_ascii=False))
    return 1 if failures else 0


if __name__ == '__main__':
    raise SystemExit(main())
