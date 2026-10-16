#!/usr/bin/env python3
"""Exercise native Kabuto skills on a connected Minecraft client; retain unedited screen evidence."""
import argparse,json,subprocess,time
from pathlib import Path
from run_runtime_qa import command

def main():
 p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--player',default='QAPlayer');p.add_argument('--display',default=':99');a=p.parse_args()
 props=dict(s.split('=',1) for s in (a.server/'server.properties').read_text().splitlines() if '=' in s and not s.startswith('#'));a.output.mkdir(parents=True,exist_ok=True)
 def send(c):
  r=command('127.0.0.1',int(props.get('rcon.port','25575')),props['rcon.password'],c)
  with (a.output/'commands.jsonl').open('a') as f:f.write(json.dumps({'command':c,'response':r})+'\n')
  return r
 def screenshot(name):subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','1280x720','-i',a.display,'-frames:v','1','-update','1',str(a.output/(name+'.png'))],check=True)
 if a.player not in send('list'):raise RuntimeError('Connect real Minecraft client first')
 recorder=subprocess.Popen(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','1280x720','-framerate','20','-i',a.display,'-an','-c:v','libx264','-preset','ultrafast','-crf','23',str(a.output/'kabuto-native-skills.mp4')],stdin=subprocess.PIPE)
 evidence={};failed=[]
 def act(s,wait=1.2,label=None):
  send(f'execute as {a.player} run fantasyhubqa kabuto_{s}');time.sleep(min(.25,wait));screenshot((label or s)+'-active');time.sleep(max(0,wait-.25))
  state=json.loads((a.server/'qa/kabuto-runtime.json').read_text());name=label or s;evidence[name]=state.get(s,state);screenshot(name)
  if isinstance(evidence[name],dict) and evidence[name].get('cast_accepted') is False:failed.append(name+': cast rejected')
  (a.output/'kabuto-runtime.json').write_text(json.dumps(evidence,indent=2));print(name,flush=True)
 try:
  send(f'kill @e[tag=qa_kabuto]');send(f'tp {a.player} 0 65 0 0 0');act('prepare',2);
  if evidence['prepare'].get('initial',{}).get('gun') is not True:raise RuntimeError('Reconnect the QA client first; baseline must be Gun Mode')
  if evidence['prepare'].get('belt_equipped_via_real_api') is not True:failed.append('prepare: native belt API')
  if len(set(evidence['prepare'].get('equipment_definitions',[])))!=6:failed.append('prepare: six distinct equipment instances')
  if evidence['prepare'].get('belt_skill_damage')!=5:failed.append('prepare: real belt skill-damage stat')
  act('henshin',1.3);act('henshin',1.3,label='henshin_repeat');
  if evidence['henshin_repeat'].get('current_health_before')!=evidence['henshin_repeat'].get('current_health_after'):failed.append('repeated henshin: current HP changed')
  before_reload=send(f'data get entity {a.player} Health');reload_reply=send('kabuto reload');after_reload=send(f'data get entity {a.player} Health');evidence['reload']={'response':reload_reply,'health_before':before_reload,'health_after':after_reload}
  if 'fantasyhub.kabuto.reloaded' not in reload_reply and 'Kabuto configuration reloaded' not in reload_reply:failed.append('kabuto reload: command failed')
  if before_reload!=after_reload:failed.append('reload: current HP changed')
  act('cast_off',4.2);act('charge',1);act('clock_up',5.8,label='clock_up_normal');act('rider_kick',4)
  act('kunai_strike',4,label='gun_projectile');act('kunai_mode',.7);act('kunai_strike',4,label='kunai_bleed');act('hyper_form',1.3);act('clock_up',5.8,label='hyper_clock_up')
  if evidence.get('gun_projectile',{}).get('state_immediate',{}).get('gun') is not True:failed.append('gun projectile: wrong mode')
  if evidence.get('kunai_bleed',{}).get('state_immediate',{}).get('gun') is not False:failed.append('kunai bleed: wrong mode')
  for label,key in [('cast_off','slam_damage_applied'),('clock_up_normal','mob_ai_frozen'),('clock_up_normal','slow_rejected'),('clock_up_normal','expired'),('clock_up_normal','mob_ai_restored'),('rider_kick','kill_resets_cooldown'),('gun_projectile','damage_applied'),('kunai_bleed','damage_applied')]:
   if evidence.get(label,{}).get(key) is not True:failed.append(label+': '+key)
  # Delayed results land after snapshots; reread authoritative reports once more.
  final=json.loads((a.server/'qa/kabuto-runtime.json').read_text());(a.output/'server-final.json').write_text(json.dumps(final,indent=2))
 finally:
  if recorder.poll() is None:recorder.communicate(b'q',timeout=20)
 summary={'result':'FAIL' if failed else 'PASS','failed':failed,'source':'real dedicated server + connected Minecraft client'};(a.output/'summary.json').write_text(json.dumps(summary,indent=2));print(json.dumps(summary));return bool(failed)
if __name__=='__main__':raise SystemExit(main())
