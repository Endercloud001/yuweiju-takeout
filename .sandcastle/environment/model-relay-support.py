"""Task relay preparation helpers; no server, credentials or upstream calls."""
import json
import os
from pathlib import Path
import threading


def read_body(stream, headers, limit=1048576):
    """Read bounded Content-Length or chunked bodies; reject incomplete framing."""
    def exact(size):
        value = stream.read(size)
        if len(value) != size:
            raise ValueError('Incomplete HTTP body')
        return value

    transfer = headers.get('Transfer-Encoding', '').strip().lower()
    if transfer:
        if transfer != 'chunked' or headers.get('Content-Length') is not None:
            raise ValueError('Unsupported or ambiguous HTTP framing')
        parts, total = [], 0
        while True:
            line = stream.readline(8193)
            if len(line) > 8192 or not line.endswith(b'\r\n'):
                raise ValueError('Invalid chunk header')
            token = line[:-2].split(b';', 1)[0]
            if not token or any(char not in b'0123456789abcdefABCDEF' for char in token):
                raise ValueError('Invalid chunk size')
            size = int(token, 16)
            total += size
            if total > limit:
                raise ValueError('HTTP body exceeds task limit')
            if not size:
                trailer_bytes = 0
                while True:
                    line = stream.readline(8193)
                    trailer_bytes += len(line)
                    if not line.endswith(b'\r\n') or trailer_bytes > 8192:
                        raise ValueError('Invalid chunk trailer')
                    if line == b'\r\n':
                        return b''.join(parts)
            parts.append(exact(size))
            if exact(2) != b'\r\n':
                raise ValueError('Invalid chunk terminator')
    length = headers.get('Content-Length', '')
    if not length.isascii() or not length.isdecimal():
        raise ValueError('Missing or invalid Content-Length')
    size = int(length)
    if size > limit:
        raise ValueError('HTTP body exceeds task limit')
    return exact(size)


class CallBudget:
    """One shared instance per task relay process; reserve before forwarding.

    Persistence survives restart. Multiple processes must not share a ledger.
    Failed requests consume the reservation because they may have reached upstream.
    """
    def __init__(self, path, maximum):
        if type(maximum) is not int or maximum < 0:
            raise ValueError('Invalid authorized call allowance')
        self.path = Path(path)
        self.maximum = maximum
        self.lock = threading.Lock()
        if self.path.exists():
            record = json.loads(self.path.read_text(encoding='utf-8-sig'))
            self.calls = record['realCalls']
            if type(self.calls) is not int or self.calls < 0:
                raise ValueError('Invalid task call ledger')
        else:
            self.calls = 0
            self._save()

    def _save(self):
        temporary = self.path.with_name(self.path.name + '.tmp')
        temporary.write_text(json.dumps({'realCalls': self.calls}) + '\n', encoding='utf-8')
        os.replace(temporary, self.path)

    def reserve(self):
        with self.lock:
            if self.calls >= self.maximum:
                raise ValueError('Task real-call allowance exhausted')
            self.calls += 1
            # A failed write blocks this request; keep its in-memory reservation.
            self._save()
            return self.calls
