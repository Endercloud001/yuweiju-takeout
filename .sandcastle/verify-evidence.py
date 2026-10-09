"""Check evidence recovery, redaction and path boundaries using local fixtures."""
import json
from pathlib import Path
import tempfile
import unittest
import importlib.util

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('save_iteration', ROOT / '.sandcastle/save-iteration.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class EvidenceTests(unittest.TestCase):
    def test_secret_fields_and_http_authorization(self):
        for value in ['Authorization: Basic dXNlcjpwYXNz', 'Authorization: Bearer abc.secret',
                      'password=two words secret', '{"password": "two words secret"}',
                      '{"token": "escaped\\\"secret"}', 'https://user:secret@example.com/']:
            result = module.redact(value)
            for secret in ['dXNlcjpwYXNz', 'abc.secret', 'words secret', 'escaped', 'user:secret']:
                self.assertNotIn(secret, result)
            self.assertIn('[REDACTED]', result)

    def test_selected_ignored_text_survives_rebuild(self):
        parent = ROOT / '.scratch/evidence-verification'
        parent.mkdir(parents=True, exist_ok=True)
        fixture = Path(tempfile.mkdtemp(dir=parent))
        source = fixture / 'worktree'
        logs = source / '.scratch/checks'
        logs.mkdir(parents=True)
        (logs / 'check.log').write_text('PASS\npassword=two words secret\n')
        destination = fixture / 'evidence/iteration-1'
        record = module.save(source, destination, ['.scratch/checks'], exit_code=7)
        (logs / 'check.log').unlink()
        self.assertIn('PASS', (destination / 'files/.scratch/checks/check.log').read_text())
        self.assertNotIn('words secret', (destination / 'files/.scratch/checks/check.log').read_text())
        self.assertEqual(record['agentExitCode'], 7)

    def test_symlinks_auth_binary_and_limits_are_recorded(self):
        parent = ROOT / '.scratch/evidence-verification'
        parent.mkdir(parents=True, exist_ok=True)
        fixture = Path(tempfile.mkdtemp(dir=parent))
        source = fixture / 'worktree'
        source.mkdir()
        (fixture / 'outside.txt').write_text('secret outside')
        (source / 'escape.txt').symlink_to(fixture / 'outside.txt')
        (source / 'auth.json').write_text('{"token":"secret"}')
        (source / '.env.local').write_text('secret')
        (source / 'binary.bin').write_bytes(b'\x00\x01')
        (source / 'huge.log').write_text('x' * (4 * 1024 * 1024 + 1))
        record = module.save(source, fixture / 'evidence', ['.', '../outside.txt'])
        self.assertEqual(record['files'], [])
        self.assertEqual(len(record['skipped']), 6)
        self.assertTrue(any(entry['reason'] == 'outside-worktree' for entry in record['skipped']))
        self.assertTrue(any(entry['reason'] == 'size-limit' for entry in record['skipped']))


if __name__ == '__main__':
    unittest.main()
