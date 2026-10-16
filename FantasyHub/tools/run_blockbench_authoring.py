#!/usr/bin/env python3
"""Run authoring operations in an actual, already-open Blockbench desktop editor.

Start Blockbench with --remote-debugging-port=9223 --remote-allow-origins='*'.
Requires websocket-client for the local DevTools connection. Gradle uses the
resulting packaged resources directly; end users never need Blockbench.
"""
import argparse,hashlib,json,urllib.request
from pathlib import Path
import websocket

def main():
 p=argparse.ArgumentParser();p.add_argument('--port',type=int,default=9223);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]);a=p.parse_args();root=a.root.resolve()
 with urllib.request.urlopen(f'http://127.0.0.1:{a.port}/json',timeout=5) as r:tabs=json.load(r)
 tab=next(t for t in tabs if 'Blockbench' in t.get('title',''))
 ws=websocket.create_connection(tab['webSocketDebuggerUrl'],origin='http://localhost',timeout=90)
 expression='globalThis.FANTASYHUB_ROOT='+json.dumps(str(root))+';\n'+(root/'tools/author_kabuto_blockbench.js').read_text()
 try:
  ws.send(json.dumps({'id':1,'method':'Runtime.evaluate','params':{'expression':expression,'awaitPromise':True,'returnByValue':True}}))
  while True:
   result=json.loads(ws.recv())
   if result.get('id')==1:break
  payload=result.get('result',{})
  if 'exceptionDetails' in payload:raise RuntimeError(json.dumps(payload['exceptionDetails']))
  report=payload.get('result',{}).get('value',{})
  if len(report.get('projects',[]))!=7:raise RuntimeError('Missing native authoring outputs: '+json.dumps(report))
 finally:ws.close()
 out=root/'client/src/imported/resources';index={str(f.relative_to(out)):hashlib.sha256(f.read_bytes()).hexdigest() for f in (out/'assets').rglob('*') if f.is_file() and f.name!='asset-index.json'}
 (out/'assets/fantasyhub/asset-index.json').write_text(json.dumps(index,indent=2)+'\n');print(json.dumps(report))
if __name__=='__main__':main()
