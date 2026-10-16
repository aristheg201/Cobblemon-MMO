#!/usr/bin/env python3
"""Execute the source Hunter wrappers on a real, already connected QA Minecraft client."""
import argparse,json,subprocess,time
from pathlib import Path
from run_runtime_qa import command

def main():
    p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--player',default='QAPlayer');p.add_argument('--display',default=':99');p.add_argument('--host',default='127.0.0.1');a=p.parse_args()
    properties=dict(l.split('=',1) for l in (a.server/'server.properties').read_text().splitlines() if '=' in l and not l.startswith('#'))
    a.output.mkdir(parents=True,exist_ok=True)
    def send(text):
        result=command(a.host,int(properties.get('rcon.port','25575')),properties['rcon.password'],text)
        with (a.output/'commands.jsonl').open('a') as f:f.write(json.dumps({'command':text,'response':result})+'\n')
        return result
    if a.player not in send('list'):raise RuntimeError('Connect the actual QA client first')
    send(f'execute as {a.player} run fantasyhubqa hunter_prepare');time.sleep(5.5)
    for scenario,seconds in [('aard',2),('igni',5.5),('quen',6.8),('adrenaline_rush',2),('blackhole',6.8),('yrden',2),('targets',2.5)]:
        if a.player not in send('list'):raise RuntimeError('QA client disconnected during '+scenario)
        send(f'execute as {a.player} run fantasyhubqa hunter_{scenario}');time.sleep(.6)
        subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','1280x720','-i',a.display,'-frames:v','1','-update','1',str(a.output/(scenario+'-final.png'))],check=True)
        time.sleep(max(0,seconds-.6))
        print(scenario,flush=True)
    source=a.server/'qa/hunter-runtime.json';data=json.loads(source.read_text());(a.output/'hunter-runtime-final.json').write_text(json.dumps(data,indent=2))
    failed=[]
    def check(node,path=''):
        if isinstance(node,dict):
            for k,v in node.items():check(v,path+'.'+k)
        elif node is False:failed.append(path)
    check(data)
    print(json.dumps({'result':'FAIL' if failed else 'PASS','failed_checks':failed,'evidence':str(a.output/'hunter-runtime-final.json')}))
    return bool(failed)
if __name__=='__main__':raise SystemExit(main())
