"""Isolated HTTP regression, using only project scratch and a loopback ephemeral port."""
from functools import partial
from http.server import ThreadingHTTPServer
import importlib.util
from pathlib import Path
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

spec = importlib.util.spec_from_file_location('admin_server', Path(__file__).with_name('admin-server.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class StaticTests(unittest.TestCase):
    def test_candidate_routes_and_assets(self):
        scratch = Path(__file__).resolve().parents[2] / '.scratch/retro-verification'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory) / 'dist'
            root.mkdir()
            (root / 'index.html').write_text('<html>candidate</html>', encoding='utf-8')
            (root / 'assets').mkdir()
            (root / 'assets/中文 图.svg').write_text('<svg></svg>', encoding='utf-8')
            (root / 'assets/app.js').write_text('window.candidate = true;', encoding='utf-8')
            (Path(directory) / 'outside.txt').write_text('private', encoding='utf-8')
            # WSL supports symlinks; preserve that boundary in local acceptance.
            (root / 'leak.txt').symlink_to(Path(directory) / 'outside.txt')
            server = ThreadingHTTPServer(('127.0.0.1', 0), partial(module.Handler, directory=str(root)))
            thread = threading.Thread(target=server.serve_forever)
            thread.start()
            base = f'http://127.0.0.1:{server.server_port}'
            try:
                for path in ['/', '/order/detail/912001?tab=all', '/order/']:
                    with urlopen(base + path, timeout=3) as response:
                        self.assertIn(b'candidate', response.read())
                        self.assertEqual(response.status, 200)
                with urlopen(base + '/assets/%E4%B8%AD%E6%96%87%20%E5%9B%BE.svg?v=1', timeout=3) as response:
                    self.assertEqual(response.read(), b'<svg></svg>')
                    self.assertEqual(response.headers.get_content_type(), 'image/svg+xml')
                with urlopen(Request(base + '/assets/app.js', method='HEAD'), timeout=3) as response:
                    self.assertIn('javascript', response.headers.get_content_type())
                    self.assertEqual(response.read(), b'')
                for path in ['/assets/missing.png', '/assets/missing', '/assets/', '/api', '/api/orders', '/%2e%2e/outside.txt', '/leak.txt']:
                    with self.assertRaises(HTTPError) as error:
                        urlopen(base + path, timeout=3)
                    self.assertEqual(error.exception.code, 404, path)
                    error.exception.close()
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=3)
                self.assertFalse(thread.is_alive(), 'test server stopped')


if __name__ == '__main__':
    unittest.main()
