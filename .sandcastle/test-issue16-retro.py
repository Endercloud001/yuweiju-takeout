"""No-model regression checks for #16 maintenance helpers."""
from concurrent.futures import ThreadPoolExecutor
from contextlib import closing
from email.message import Message
import importlib.util
from io import BytesIO
import json
from pathlib import Path
import runpy
import sys
import tempfile
import threading
import types
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import http.client
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent.parent


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


support = load('relay_support', '.sandcastle/environment/model-relay-support.py')
reader = load('verification_reader', '.sandcastle/read-verification.py')


class Regression(unittest.TestCase):
    def setUp(self):
        parent = ROOT / '.scratch/issue16-retro-tests'
        parent.mkdir(parents=True, exist_ok=True)
        self.directory = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name)

    def test_http_framing_reaches_local_substitute(self):
        received = []

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_POST(self):
                try:
                    body = support.read_body(self.rfile, self.headers)
                    received.append(json.loads(body))
                    self.send_response(200)
                except ValueError:
                    self.send_response(400)
                self.send_header('Content-Length', '0')
                self.end_headers()

        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            for payload, headers, chunked in [
                (b'{"reply":"ok"}', {}, False),
                (iter([b'{"reply":', b'"ok"}']), {'Transfer-Encoding': 'chunked'}, True),
                (b'bad', {'Transfer-Encoding': 'gzip'}, False),
            ]:
                with closing(http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=3)) as connection:
                    connection.request('POST', '/', body=payload, headers=headers, encode_chunked=chunked)
                    response = connection.getresponse()
                    self.assertEqual(response.status, 400 if headers.get('Transfer-Encoding') == 'gzip' else 200)
                    response.read()
            self.assertEqual(received, [{'reply': 'ok'}, {'reply': 'ok'}])
        finally:
            server.shutdown()
            server.server_close()
            worker.join()

    def test_extensions_trailer_and_bad_bodies(self):
        headers = Message()
        headers['Transfer-Encoding'] = 'Chunked'
        self.assertEqual(support.read_body(BytesIO(b'2;key=value\r\n{}\r\n0\r\nX-Test: yes\r\n\r\n'), headers), b'{}')
        for raw in (b'', b'-1\r\n', b'2\r\n{', b'2\r\n{}XX', b'0\r\n'):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                support.read_body(BytesIO(raw), headers)
        with self.assertRaises(ValueError):
            support.read_body(BytesIO(b'3\r\nabc\r\n0\r\n\r\n'), headers, limit=2)
        headers['Content-Length'] = '2'
        with self.assertRaises(ValueError):
            support.read_body(BytesIO(b'{}'), headers)
        for length in ('-1', '3', 'bad'):
            with self.subTest(length=length), self.assertRaises(ValueError):
                support.read_body(BytesIO(b'{}'), {'Content-Length': length}, limit=2)

    def test_concurrent_limit_restart_and_failed_call(self):
        ledger = self.path / 'budget.json'
        budget = support.CallBudget(ledger, 6)

        def request(_):
            try:
                return budget.reserve()
            except ValueError:
                return None

        with ThreadPoolExecutor(max_workers=12) as executor:
            results = list(executor.map(request, range(24)))
        self.assertEqual(sorted(value for value in results if value), list(range(1, 7)))
        self.assertEqual(json.loads(ledger.read_text(encoding='utf-8')), {'realCalls': 6})
        with self.assertRaises(ValueError):
            support.CallBudget(ledger, 6).reserve()
        # An upstream error never refunds a reservation, even across restart.
        failed = self.path / 'failed.json'
        support.CallBudget(failed, 1).reserve()
        with self.assertRaises(ValueError):
            support.CallBudget(failed, 1).reserve()

    def test_persistence_errors_do_not_allow_forwarding(self):
        ledger = self.path / 'budget.json'
        ledger.write_text('bad json', encoding='utf-8')
        with self.assertRaises(ValueError):
            support.CallBudget(ledger, 1)
        ledger.write_text('{"realCalls":-1}', encoding='utf-8')
        with self.assertRaises(ValueError):
            support.CallBudget(ledger, 1)
        ledger.unlink()
        budget = support.CallBudget(ledger, 1)
        with patch.object(support.os, 'replace', side_effect=OSError('local fixture')):
            with self.assertRaises(OSError):
                budget.reserve()
        with self.assertRaises(ValueError):
            budget.reserve()
        with self.assertRaises(ValueError):
            support.CallBudget(self.path / 'zero.json', 0).reserve()

    def test_utf8_chinese_and_bounded_summary(self):
        record = {'commit': 'fixture', 'exitCode': 7, 'stopped': True,
                  'independentVerification': 'failed', 'commands': ['sensitive body'],
                  'checks': [{'index': i, 'status': 'failed', 'exitCode': 7} for i in range(45)]}
        resources = self.path / 'resources.json'
        resources.write_text(json.dumps(record), encoding='utf-8-sig')
        log = self.path / 'check-0.log'
        log.write_text('中文业务正文不可打印\n' + '\n'.join(
            f'Tests run: {i}, Failures: 0, Errors: 0, Skipped: 0' for i in range(1, 5)), encoding='utf-8')
        with self.assertRaises(UnicodeDecodeError):
            log.read_text(encoding='gbk')
        summary = reader.summarize(resources)
        self.assertEqual(summary['exitCode'], 7)
        self.assertEqual(summary['checksOmitted'], 5)
        self.assertEqual(summary['logs'][0]['testSummaries'][-1]['tests'], 4)
        self.assertEqual(len(summary['logs'][0]['testSummaries']), 3)
        self.assertNotIn('sensitive', json.dumps(summary))
        self.assertNotIn('中文', json.dumps(summary, ensure_ascii=False))

    def test_reader_bad_encoding_and_json_fail(self):
        resources = self.path / 'resources.json'
        for raw in (b'\xff', b'bad json'):
            resources.write_bytes(raw)
            with self.subTest(raw=raw), self.assertRaises((UnicodeError, ValueError)):
                reader.summarize(resources)

    def test_lifecycle_failed_utf8_log_is_preserved(self):
        # Exercise the actual existing entry with local process substitutes,
        # including its failure branch; no Docker or lifecycle timers needed.
        fixture = self.path / 'fixture'
        evidence = fixture / '.scratch/evidence'
        evidence.mkdir(parents=True)
        for index in range(9):
            (evidence / f'{index}-verdict.json').write_text(
                json.dumps({'passed': index != 8, 'detail': '中文失败'}, ensure_ascii=False), encoding='utf-8')
        fake_fixture = types.ModuleType('fixture_repo')
        fake_fixture.create = lambda *args: fixture
        results = iter([0, 7])

        def command(*args, **kwargs):
            kwargs['stdout'].write('中文失败记录\n')
            return types.SimpleNamespace(returncode=next(results))

        with patch.dict(sys.modules, {'fixture_repo': fake_fixture}), \
                patch('subprocess.run', side_effect=command), patch('builtins.print') as output:
            with self.assertRaises(SystemExit) as stopped:
                runpy.run_path(str(ROOT / '.sandcastle/verify-lifecycle.py'))
        self.assertEqual(stopped.exception.code, 7)
        self.assertEqual((fixture / '.scratch/lifecycle.log').read_text(encoding='utf-8').count('中文失败'), 2)
        self.assertTrue(any('中文失败' in str(call) for call in output.call_args_list))


if __name__ == '__main__':
    unittest.main()
