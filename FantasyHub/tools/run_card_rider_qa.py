#!/usr/bin/env python3
"""Record 21 real native Rider casts in the disposable test arena at 200,65,0.

Requires an opt-in SVFRAME_RUNTIME_QA=1 server/client pair, two connected test
accounts, administrator permissions, X11, ffmpeg and xdotool. The real fixture
changes class/level/equipment and clears the actor's test inventory. Reports and
videos come from Minecraft, not a simulated combat engine.
"""
from pathlib import Path
import argparse,sys,time,json,subprocess,os,re,uuid,struct
from run_runtime_qa import command as rcon
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--server',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--actor',default='QAPlayer')
parser.add_argument('--observer',default='QAAttacker')
parser.add_argument('--actor-display',default=':99')
parser.add_argument('--observer-display',default=':101')
parser.add_argument('--host',default='127.0.0.1')
parser.add_argument('--xdotool',default='xdotool')
parser.add_argument('--x11-library-path')
args=parser.parse_args()
for name in [args.actor,args.observer]:
 if not re.fullmatch(r'[A-Za-z0-9_]{1,16}',name):parser.error('Invalid test account name')
properties=dict(line.split('=',1) for line in (args.server/'server.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
password=os.environ.get('FANTASYHUB_QA_RCON_PASSWORD') or properties.get('rcon.password')
if not password:parser.error('Set FANTASYHUB_QA_RCON_PASSWORD or configure local RCON')
def command(text):return rcon(args.host,int(properties.get('rcon.port','25575')),password,text)
raw=command('data get entity '+args.actor+' UUID')
match=re.search(r'\[I;\s*([^]]+)\]',raw)
if not match:raise RuntimeError('Connected actor UUID not returned by native server')
actor_uuid=uuid.UUID(bytes=struct.pack('>4i',*[int(x.strip()) for x in match.group(1).split(',')]))
OUT=args.output;OUT.mkdir(parents=True,exist_ok=True)
REPORT=args.server/'qa/rider-gameplay'/f'{actor_uuid}.json'

commands=[]
def cmd(text):
 text=text.replace('QAPlayer',args.actor).replace('QAAttacker',args.observer)
 r=command(text);commands.append({'command':text,'response':r});(OUT/'commands.json').write_text(json.dumps(commands,indent=2)+'\n');return r
def report(scenario,settled=True,timeout=15):
 until=time.monotonic()+timeout
 while time.monotonic()<until:
  try:
   r=json.loads(REPORT.read_text())
   if r['scenario'].lower()==scenario.lower() and (not settled or r.get('settledReport')):return r
  except (OSError,json.JSONDecodeError):pass
  time.sleep(.1)
 raise RuntimeError('Native report timeout '+scenario)
def cast(id,settled=True):
 scenario='cast_'+id.lower();cmd('execute as QAPlayer run fantasyhubqa rider_game_'+scenario)
 return report(scenario,settled)
def snap(name,display=None):
 display=display or args.observer_display
 subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','1280x720','-i',display,'-frames:v','1','-update','1',str(OUT/(name+'.png'))],check=True)
def key_f():
 subprocess.run([args.xdotool,'key','f'],env={**os.environ,'DISPLAY':args.actor_display,**({'LD_LIBRARY_PATH':args.x11_library_path} if args.x11_library_path else {})},check=True)
def targets():
 cmd('kill @e[tag=rider_qa_target]')
 for i,(x,z) in enumerate([(200.5,3.2),(202,4.8),(199.3,5.3)]):
  cmd('summon minecraft:zombie '+str(x)+' 65 '+str(z)+' {Tags:["rider_qa_target"],NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[0f,0f],Health:1000f,Attributes:[{Name:"minecraft:generic.max_health",Base:1000d},{Name:"minecraft:generic.armor",Base:20d}]}')
 cmd('execute as @e[tag=rider_qa_target] run attribute @s minecraft:generic.max_health base set 1000')
 cmd('execute as @e[tag=rider_qa_target] run attribute @s minecraft:generic.armor base set 20')
 cmd('execute as @e[tag=rider_qa_target] run data merge entity @s {Health:1000f}')
def prepare(rider,elevated=False):
 cmd('execute as QAPlayer run fantasyhubqa rider_game_prepare_'+rider);r=report('prepare_'+rider)
 assert r['equippedViaRealApi'],r
 cmd('tp QAPlayer 200.5 65 .5 0 0');targets()
 cmd('clear QAPlayer');cmd('give QAPlayer minecraft:diamond_sword');cmd('give QAPlayer minecraft:stone 7')
 key_f();time.sleep(.5)
 cmd('execute as QAPlayer run fantasyhubqa rider_game_status');r=report('status')
 assert r['settled']['active'],r
 if elevated:
  r=cast(rider+('_complete' if rider=='decade' else '_survive'))
  assert r['castResult'] and r['settled']['elevated'],r
 return r
SKILLS={
 'ryuki':['STRIKE_VENT','SWORD_VENT','GUARD_VENT','ADVENT','SURVIVE','SHOOT_VENT','FINAL_VENT'],
 'knight':['SWORD_VENT','NASTY_VENT','TRICK_VENT','GUARD_VENT','SURVIVE','BLAST_VENT','FINAL_VENT'],
 'decade':['SLASH_BLAST','ILLUSION','CLOCK_UP','HEISEI_SPIRIT','COMPLETE','ULTIMATE_SUMMON','DIMENSION_KICK']}
DEFER_DAMAGE={'GUARD_VENT','SURVIVE','COMPLETE','HEISEI_SPIRIT'}
def main():
 assert args.actor in cmd('list') and args.observer in cmd('list')
 cmd('gamerule doMobLoot false');cmd('kill @e[type=minecraft:item,x=185,y=64,z=-15,dx=40,dy=20,dz=55]')
 cmd('fill 185 64 -15 225 64 40 minecraft:stone');cmd('time set midnight');cmd('gamerule doDaylightCycle false');cmd('weather clear')
 cmd('tp QAAttacker 207.5 66 3.5 109 -10')
 cmd('effect give QAPlayer minecraft:night_vision 3600 0 true');cmd('effect give QAAttacker minecraft:night_vision 3600 0 true')
 results=[]
 for rider,skills in SKILLS.items():
  for ability in skills:
   prepare(rider,rider=='decade' and ability in ['ULTIMATE_SUMMON','DIMENSION_KICK'])
   # Record the real observer screen. A video is evidence, not an asserted success by itself.
   video=subprocess.Popen(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-framerate','15','-video_size','1280x720','-i',args.observer_display,'-t','4','-c:v','libx264','-preset','ultrafast','-crf','24','-pix_fmt','yuv420p',str(OUT/(rider+'-'+ability.lower()+'.mp4'))])
   time.sleep(.15)
   scenario='cast_'+rider+'_'+ability.lower();cmd('execute as QAPlayer run fantasyhubqa rider_game_'+scenario)
   immediate=report(scenario,False);time.sleep(.65);snap(rider+'-'+ability.lower())
   r=report(scenario);video.wait(timeout=15)
   checks={'accepted':bool(r['castResult']),'inventoryPreserved':r.get('inventoryBefore')==r.get('inventoryAfter'),'cooldownApplied':r.get('cooldownAfter',0)>0,'actualDamageEvents':len(r.get('hits',[]))}
   if ability not in DEFER_DAMAGE:checks['damageObserved']=len(r.get('hits',[]))>0
   checks['resourceConsumed']=r['manaAfter']<=r['manaBefore'] and r['staminaAfter']<=r['staminaBefore']
   r['checks']=checks;(OUT/(rider+'-'+ability.lower()+'.json')).write_text(json.dumps(r,indent=2)+'\n')
   result={'class':rider,'ability':ability,'checks':checks,'status':'PASS' if all(v for k,v in checks.items() if isinstance(v,bool)) else 'FAIL'};results.append(result)
   (OUT/'results.json').write_text(json.dumps(results,indent=2)+'\n');print(json.dumps(result),flush=True)
 (OUT/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
 print('NATIVE_SKILLS',len(results),'failed',sum(r['status']=='FAIL' for r in results),flush=True)
if __name__=='__main__':main()
