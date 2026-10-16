#!/usr/bin/env python3
"""Real keyboard/RCON acceptance of F Henshin and native bound Cast Off.
Run with a freshly connected opt-in QA player, in the prepared studio arena.
Inventory is compared as actual server NBT, never a client-only display value.
"""
import argparse,json,os,subprocess,time
from pathlib import Path
from run_runtime_qa import command

def main():
 p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--player',default='QAPlayer');p.add_argument('--display',default=':99');p.add_argument('--xdotool',default='xdotool');a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
 props=dict(s.split('=',1) for s in (a.server/'server.properties').read_text().splitlines() if '=' in s and not s.startswith('#'));env=os.environ.copy();env['DISPLAY']=a.display
 def send(c):
  result=command('127.0.0.1',int(props.get('rcon.port','25575')),props['rcon.password'],c)
  with (a.output/'commands.jsonl').open('a') as f:f.write(json.dumps({'command':c,'result':result})+'\n')
  return result
 def key(k):subprocess.run([a.xdotool,'key',k],env=env,check=True)
 def status():
  send(f'execute as {a.player} run fantasyhubqa kabuto_status');return json.loads((a.server/'qa/kabuto-runtime.json').read_text())['status']
 def shot(name):subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','1280x720','-i',a.display,'-frames:v','1','-update','1',str(a.output/(name+'.png'))],check=True)
 if a.player not in send('list'):raise RuntimeError('Connect the native client before running acceptance')
 if status().get('active'):raise RuntimeError('Reconnect the QA client before this test; initial state must be untransformed')
 before=send(f'data get entity {a.player} Inventory');report={}
 recorder=subprocess.Popen(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','1280x720','-framerate','20','-i',a.display,'-an','-c:v','libx264','-preset','ultrafast','-crf','23',str(a.output/'native-f-cast-off.mp4')],stdin=subprocess.PIPE)
 try:
  key('f');time.sleep(1.5);report['after_f']=status();report['masked_health']=send(f'attribute {a.player} minecraft:generic.max_health get');shot('f-masked-hud')
  report['inventory_preserved_after_henshin']=before==send(f'data get entity {a.player} Inventory')
  # Holding native hotbar1 maps bound Cast Off2 to visible input3.
  key('3');time.sleep(4.5);report['after_native_cast_off']=status();send(f'tp {a.player} 200.5 65 0.5 0 0');time.sleep(.5);shot('native-cast-off-rider')
  key('f');time.sleep(.7);report['after_manual_close_f']=status();shot('close-f-returns-human')
  report['inventory_preserved_after_cast_off']=before==send(f'data get entity {a.player} Inventory')
 finally:recorder.communicate(b'q',timeout=20)
 checks={'f_henshin':report['after_f'].get('active') and report['after_f'].get('form')=='MASKED','native_bound_cast_off':report['after_native_cast_off'].get('form')=='RIDER','closing_f_returns_human':report['after_manual_close_f'].get('active') is False,'henshin_inventory':report['inventory_preserved_after_henshin'],'cast_off_inventory':report['inventory_preserved_after_cast_off']}
 report['checks']=checks;report['result']='PASS' if all(checks.values()) else 'FAIL';(a.output/'verification.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report));return report['result']!='PASS'
if __name__=='__main__':raise SystemExit(main())
