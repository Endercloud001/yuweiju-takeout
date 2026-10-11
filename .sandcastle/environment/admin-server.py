"""Read-only SPA assets for local acceptance; API proxying belongs to the gateway."""
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit


class Handler(SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        # Request URLs can carry tokens; retain only the HTTP status.
        if len(args) > 1:
            print('static HTTP', args[1], flush=True)

    def list_directory(self, path):
        self.send_error(404)
        return None

    def send_head(self):
        root = Path(self.directory).resolve()
        target = Path(self.translate_path(self.path)).resolve()
        if not target.is_relative_to(root):
            self.send_error(404)
            return None
        path = unquote(urlsplit(self.path).path)
        if path == '/api' or path.startswith('/api/'):
            self.send_error(404)
            return None
        if target.is_dir() and not (target / 'index.html').resolve().is_relative_to(root):
            self.send_error(404)
            return None
        if not target.exists() and not Path(path).suffix and not path.startswith('/assets/'):
            # Browser history routes fall back; missing assets retain their 404.
            self.path = '/index.html'
            return self.send_head()
        return super().send_head()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--port', type=int, required=True)
    parser.add_argument('--bind', default='127.0.0.1')
    args = parser.parse_args()
    root = args.directory.resolve()
    if not root.is_dir() or not (root / 'index.html').is_file():
        parser.error('directory must contain the candidate index.html')
    with ThreadingHTTPServer((args.bind, args.port), partial(Handler, directory=str(root))) as server:
        print(f'Serving candidate assets on {args.bind}:{server.server_port}', flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == '__main__':
    main()
