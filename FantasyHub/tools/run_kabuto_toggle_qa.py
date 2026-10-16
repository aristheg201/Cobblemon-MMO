#!/usr/bin/env python3
"""Native F + Henshin toggle regression with two real clients; isolated QA only."""
import argparse,json,os,re,subprocess,time,uuid
from pathlib import Path
from run_runtime_qa import command
p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--player',default='QAPlayer');p.add_argument('--observer',default='QAAttacker');p.add_argument('--display',default=':99');p.add_argument('--observer-display',default=':101');p.add_argument('--xdotool',default='xdotool');a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
props=dict(v.split('=',1) for v in (a.server/'server.properties').read_text().splitlines() if '=' in v and not v.startswith('#'));env=os.environ.copy();env['DISPLAY']=a.display
records=[];evidence={};checks={}
def send(s):
 r=command('127.0.0.1',int(props.get('rcon.port','25575')),props['rcon.password'],s);records.append({'command':s,'response':r});(a.output/'commands.json').write_text(json.dumps(records,indent=2));return r
def key(s):subprocess.run([a.xdotool,'key',s],env=env,check=True)
def status():
 send(f'execute as {a.player} run fantasyhubqa kabuto_status');return json.loads((a.server/'qa/kabuto-runtime.json').read_text())['status']
def attributes():
 return {name:float(send(f'attribute {a.player} minecraft:generic.{name} get').rsplit(' is ',1)[1]) for name in ['max_health','armor','knockback_resistance','movement_speed','attack_speed']}
def observe():
 result=send(f'data get entity {a.player} UUID');parts=[int(v) for v in re.search(r'\[I;([^]]+)\]',result).group(1).split(',')];pid=str(uuid.UUID(int=sum((v&0xffffffff)<<((3-i)*32) for i,v in enumerate(parts))))
 file=a.server/'qa/fantasyhub'/(pid+'.jsonl')
 with file.open('rb') as f:f.seek(max(0,file.stat().st_size-65536));return json.loads(f.read().decode().splitlines()[-1])
def shot(name):
 for display,suffix in [(a.display,'actor'),(a.observer_display,'observer')]:subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','1280x720','-i',display,'-frames:v','1','-update','1',str(a.output/(name+'-'+suffix+'.png'))],check=True)
def wait_active(expected,timeout=12):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  s=status()
  if s.get('active') is expected:return s
  time.sleep(.2)
 raise RuntimeError('Transformation did not reach active='+str(expected))
def snap(name,active):
 state=wait_active(active);time.sleep(.75);evidence[name]={'server':state,'attributes':attributes(),'client':observe()};shot(name);(a.output/'observations.json').write_text(json.dumps(evidence,indent=2));return state
assert a.player in send('list') and a.observer in send('list')
assert status().get('active') is False,'Fresh untransformed connection required'
send('kill @e[tag=qa_kabuto]');send(f'tp {a.player} 200.5 65 0.5 0 0');send(f'gamemode spectator {a.observer}');send(f'tp {a.observer} 201.8 65.0 2.7 149.42 15.73');time.sleep(.75)
inventory=send(f'data get entity {a.player} Inventory');base=attributes();evidence['baseline']={'attributes':base,'client':observe()}
recorders=[]
for display,name in [(a.display,'native-toggle'),(a.observer_display,'observer-toggle')]:recorders.append(subprocess.Popen(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','1280x720','-framerate','20','-i',display,'-an','-c:v','libx264','-preset','ultrafast','-crf','23',str(a.output/(name+'.mp4'))],stdin=subprocess.PIPE))
try:
 key('f');snap('f-on-masked',True);checks['f_on_masked']=evidence['f-on-masked']['server']['form']=='MASKED';checks['masked_bonus_applies']=evidence['f-on-masked']['attributes']['max_health']>base['max_health']
 key('f');snap('f-off-human',False);checks['f_off_restores_attributes']=evidence['f-off-human']['attributes']==base;checks['client_form_cleared']='kabuto' not in evidence['f-off-human']['client'];checks['client_actual_health_restored']=evidence['f-off-human']['client']['clientMaxHealth']==base['max_health']
 key('f');wait_active(True);key('3');time.sleep(4.5);snap('cast-off-rider',True);checks['cast_off_preserved']=evidence['cast-off-rider']['server']['form']=='RIDER'
 key('2');snap('henshin-again-human',False);checks['henshin_recast_restores_attributes']=evidence['henshin-again-human']['attributes']==base
 # Wait real cooldown before toggling on through the same native binding.
 time.sleep(2.5)
 for _ in range(12):
  key('2');time.sleep(.3)
  if status().get('active'):break
 snap('henshin-again-masked',True);checks['henshin_recast_can_transform_again']=evidence['henshin-again-masked']['server']['form']=='MASKED'
 send(f'execute as {a.player} run fantasyhubqa kabuto_cast_off');time.sleep(4.5);send(f'tp {a.player} 200.5 65 0.5 0 0');send(f'execute as {a.player} run fantasyhubqa kabuto_charge');send(f'execute as {a.player} run fantasyhubqa kabuto_hyper_form');send('summon minecraft:cow 202.0 65.0 1.5 {Tags:["qa_kabuto","qa_toggle_freeze"],PersistenceRequired:1b}');send(f'execute as {a.player} run fantasyhubqa kabuto_clock_up');time.sleep(.4)
 evidence['clock_before_close']=status();checks['clock_was_active']=evidence['clock_before_close'].get('clock') is True;checks['hyper_was_active']=evidence['clock_before_close'].get('hyper') is True
 selector='@e[type=minecraft:cow,tag=qa_toggle_freeze,limit=1]'
 def position(reply):return [float(v.strip().rstrip('d')) for v in re.search(r'\[([^]]+)\]',reply).group(1).split(',')]
 frozen_before=send(f'data get entity {selector} Pos');send(f'execute as {selector} at @s run tp @s ~ ~ ~1');time.sleep(.25);frozen_after=send(f'data get entity {selector} Pos');checks['mob_was_frozen']=abs(position(frozen_after)[2]-position(frozen_before)[2])<.1
 evidence['feedback_before_clock_close']=observe()
 key('f');wait_active(False)
 before=send(f'execute at {a.player} run data get entity {selector} Pos')
 send(f'execute at {a.player} as {selector} at @s run tp @s ~ ~ ~1');time.sleep(.4)
 after=send(f'execute at {a.player} run data get entity {selector} Pos')
 def position(reply):return [float(v.strip().rstrip('d')) for v in re.search(r'\[([^]]+)\]',reply).group(1).split(',')]
 checks['clock_freeze_released']=position(after)[2]-position(before)[2]>.7;evidence['freeze_release']={'before':before,'after':after}
 snap('clock-cancel-human',False);s=evidence['clock-cancel-human']['server'];checks['clock_and_hyper_cancelled']=not s.get('clock') and not s.get('hyper');checks['clock_speed_restored']=evidence['clock-cancel-human']['attributes']==base;checks['effects_cleaned']=evidence['clock-cancel-human']['client']['kabutoEffects']['activeTimelines']==0
 checks['inventory_unchanged']=inventory==send(f'data get entity {a.player} Inventory');checks['no_new_hurt_packets']=evidence['f-off-human']['client']['damagePackets']==evidence['f-on-masked']['client']['damagePackets'] and evidence['henshin-again-human']['client']['damagePackets']==evidence['cast-off-rider']['client']['damagePackets'] and evidence['clock-cancel-human']['client']['damagePackets']==evidence['feedback_before_clock_close']['damagePackets'];checks['no_new_hurt_camera']=evidence['clock-cancel-human']['client']['hurtCameraFrames']==evidence['baseline']['client']['hurtCameraFrames']
finally:
 for recorder in recorders:recorder.communicate(b'q',timeout=20)
(a.output/'observations.json').write_text(json.dumps(evidence,indent=2));report={'result':'PASS' if all(checks.values()) else 'FAIL','checks':checks};(a.output/'verification.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2));assert report['result']=='PASS'
