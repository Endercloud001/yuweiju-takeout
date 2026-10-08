"""Local HTTP/model/storage substitutes. Never forward to real providers or log headers."""
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
import json,signal,threading,time,urllib.parse
ROOT=Path('/runtime/uploads');ROOT.mkdir(parents=True,exist_ok=True)
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def respond(self,status,body,kind='application/json',headers=None):
  self.send_response(status);self.send_header('Content-Type',kind)
  for k,v in (headers or {}).items():self.send_header(k,v)
  self.end_headers()
  try:self.wfile.write(body)
  except (BrokenPipeError,ConnectionResetError):pass
 def do_POST(self):
  length=int(self.headers.get('Content-Length','0'));self.rfile.read(length)
  case=urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query).get('case',['bad-output'])[0]
  if case=='timeout':time.sleep(3)
  if case in ['402','503']:self.respond(int(case),b'{"error":"local fixture only"}');return
  self.respond(200,b'{"choices":[{"message":{"content":"not valid business output"}}]}')
 def do_PUT(self):
  name=Path(urllib.parse.urlsplit(self.path).path).name
  if not name or name in ['.','..']:self.respond(400,b'{}');return
  if 'chunked' in self.headers.get('Transfer-Encoding','').lower():
   chunks=[]
   while True:
    size=int(self.rfile.readline().split(b';')[0].strip(),16)
    if size==0:
     while self.rfile.readline().strip():pass
     break
    chunks.append(self.rfile.read(size));self.rfile.read(2)
   payload=b''.join(chunks)
  else:payload=self.rfile.read(int(self.headers.get('Content-Length','0')))
  (ROOT/name).write_bytes(payload)
  # HTTP ETag is part of the existing OSS client protocol, not a new project integrity hash.
  self.respond(200,b'',headers={'ETag':'"fixture-etag"','x-oss-request-id':'isolated-fixture'})
 def do_GET(self):
  if self.path=='/health':self.respond(200,b'{"fixture":true}');return
  name=Path(urllib.parse.urlsplit(self.path).path).name;p=ROOT/name
  if p.is_file():self.respond(200,p.read_bytes(),'application/octet-stream')
  else:self.respond(404,b'{}')
servers=[ThreadingHTTPServer(('127.0.0.1',port),Handler) for port in [18081,18082]]
for server in servers:threading.Thread(target=server.serve_forever,daemon=True).start()
def stop(sig,frame):raise SystemExit()
signal.signal(signal.SIGTERM,stop)
try:threading.Event().wait()
finally:
 for server in servers:server.shutdown();server.server_close()
