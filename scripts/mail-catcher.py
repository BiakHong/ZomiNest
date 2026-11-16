#!/usr/bin/env python3
"""Local development only: SMTP on 127.0.0.1:1025, mailbox on :8025.
Use Mailpit from Compose when Docker is available. Messages are memory-only.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from socketserver import StreamRequestHandler, ThreadingTCPServer
from threading import Thread, Lock
from email import policy
from email.parser import BytesParser
from html import escape
import json
messages=[]
lock=Lock()
class SMTP(StreamRequestHandler):
 def handle(self):
  self.request.settimeout(30)
  self.wfile.write(b'220 localhost development mail catcher\r\n')
  while True:
   line=self.rfile.readline(8192)
   if not line:return
   command=line.decode(errors='replace').strip().upper()
   if command.startswith('DATA'):
    self.wfile.write(b'354 End with dot\r\n');content=[];size=0
    while True:
     part=self.rfile.readline(65536)
     if not part or part==b'.\r\n':break
     size+=len(part)
     if size>2_000_000:return
     content.append(part[1:] if part.startswith(b'..') else part)
    message=BytesParser(policy=policy.default).parsebytes(b''.join(content));body=message.get_body(preferencelist=('plain',))
    entry={'to':str(message.get('To','')),'subject':str(message.get('Subject','')),'body':body.get_content() if body else ''}
    with lock:
     messages.append(entry)
     del messages[:-100]
    self.wfile.write(b'250 Stored locally\r\n')
   elif command.startswith('QUIT'):
    self.wfile.write(b'221 Goodbye\r\n');return
   else:self.wfile.write(b'250 OK\r\n')
class Mailbox(BaseHTTPRequestHandler):
 def do_GET(self):
  with lock:items=list(reversed(messages))
  if self.path=='/messages':body=json.dumps(items).encode();kind='application/json'
  else:
   body=('<!doctype html><meta charset="utf-8"><title>Local development mail</title><style>body{max-width:850px;margin:50px auto;font:16px system-ui;padding:20px}article{border-top:1px solid #ccc;padding:20px 0}pre{white-space:pre-wrap;overflow-wrap:anywhere}</style><h1>Local development mail</h1><p>Refresh to receive messages. Copy a recovery link from the message into your browser. This server is for local development only.</p>'+''.join('<article><h2>'+escape(m['subject'])+'</h2><p>To: '+escape(m['to'])+'</p><pre>'+escape(m['body'])+'</pre></article>' for m in items)).encode();kind='text/html; charset=utf-8'
  self.send_response(200);self.send_header('Content-Type',kind);self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def log_message(self,*args):pass
class Server(ThreadingTCPServer):allow_reuse_address=True
if __name__=='__main__':
 smtp=Server(('127.0.0.1',1025),SMTP);Thread(target=smtp.serve_forever,daemon=True).start();print('Local mail catcher: SMTP localhost:1025; mailbox http://localhost:8025',flush=True);ThreadingHTTPServer(('127.0.0.1',8025),Mailbox).serve_forever()
