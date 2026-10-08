"""Loopback-published fixed upstream relay; backend stays on its internal network."""
import select,socket,socketserver
class Relay(socketserver.BaseRequestHandler):
 def handle(self):
  with socket.create_connection(('backend',8080),timeout=10) as upstream:
   peers=[self.request,upstream]
   while True:
    ready,_,_=select.select(peers,[],[],600)
    if not ready:return
    for source in ready:
     data=source.recv(65536)
     if not data:return
     target=upstream if source is self.request else self.request
     target.sendall(data)
class Server(socketserver.ThreadingTCPServer):
 allow_reuse_address=True
 daemon_threads=True
with Server(('0.0.0.0',18080),Relay) as server:server.serve_forever()
