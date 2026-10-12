"""Exercise task preparation through its CLI, with an offline GitHub boundary."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

RUNTIME = Path(__file__).resolve().parent.parent
MATERIALS = (
    '.agents/skills/yuweiju-ponytail/SKILL.md',
    '.agents/skills/yuweiju-ponytail/references/ponytail-upstream.md',
    '.agents/skills/yuweiju-ponytail/LICENSE',
)


class TaskInputTests(unittest.TestCase):
    def setUp(self):
        temporary_root = RUNTIME / '.scratch' / 'prepare-input-tests'
        temporary_root.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(dir=temporary_root)
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.source = self.directory / 'source'
        self.output = self.directory / 'input'
        self.bin = self.directory / 'bin'
        self.bin.mkdir()
        for index, name in enumerate(MATERIALS):
            path = self.source / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(f'material fixture {index}\n', encoding='utf-8')
        gh = self.bin / 'gh'
        gh.write_text('#!/bin/sh\nprintf \'%s\\n\' \'{"number":123,"state":"OPEN",'
                      '"title":"Offline fixture","body":"","comments":[],"updatedAt":"2026-10-12"}\'\n',
                      encoding='utf-8', newline='\n')
        gh.chmod(0o755)
        self.environment = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ['PATH'])

    def prepare(self, *arguments):
        return subprocess.run(
            [sys.executable, str(RUNTIME / '.sandcastle' / 'prepare-input.py'),
             '--issue', '123', '--source-root', str(self.source), '--output', str(self.output), *arguments],
            cwd=RUNTIME, env=self.environment, capture_output=True, text=True, timeout=15)

    def test_new_task_gets_closeout_materials_without_manual_flags(self):
        result = self.prepare()
        self.assertEqual(result.returncode, 0, result.stderr)
        for index, name in enumerate(MATERIALS):
            self.assertEqual((self.output / name).read_text(encoding='utf-8'), f'material fixture {index}\n')
        self.assertEqual(json.loads((self.output / 'issue.json').read_text())['number'], 123)

    def test_missing_closeout_reference_stops_before_issue_fetch(self):
        (self.source / MATERIALS[1]).unlink()
        result = self.prepare()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('ponytail-upstream.md', result.stderr)
        self.assertFalse((self.output / 'fetches').exists())

    def test_extra_material_and_raw_issue_evidence_are_retained(self):
        extra = self.source / 'docs' / 'issue-summary.md'
        extra.parent.mkdir(parents=True)
        extra.write_text('Authorized task summary\n', encoding='utf-8')
        result = self.prepare('--material', 'docs/issue-summary.md')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.output / 'docs/issue-summary.md').read_text(), 'Authorized task summary\n')
        fetch = next((self.output / 'fetches').iterdir())
        self.assertEqual(json.loads((fetch / 'stdout.raw').read_text())['number'], 123)
        self.assertEqual(json.loads((fetch / 'fetch.json').read_text())['exitCode'], 0)

    def test_extra_material_outside_source_remains_rejected(self):
        outside = self.directory / 'outside.md'
        outside.write_text('Not a repository material\n', encoding='utf-8')
        result = self.prepare('--material', '../outside.md')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Material must stay within the selected source repository', result.stderr)


if __name__ == '__main__':
    unittest.main()
