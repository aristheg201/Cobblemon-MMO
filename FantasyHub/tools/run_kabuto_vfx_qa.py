#!/usr/bin/env python3
from pathlib import Path
import argparse,json,time,subprocess,uuid
from run_runtime_qa import command as rcon
parser=argparse.ArgumentParser(description="Two real connected clients; native Minecraft + Cobblemon Kabuto effects")
parser.add_argument('--server',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--player',default='QAPlayer');parser.add_argument('--observer',default='QAAttacker');parser.add_argument('--display',default=':101')
a=parser.parse_args();out=a.output;out.mkdir(parents=True,exist_ok=True)
props=dict(line.split('=',1) for line in (a.server/'server.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
def command(c):return rcon('127.0.0.1',int(props.get('rcon.port','25575')),props['rcon.password'],c.replace('QAPlayer',a.player).replace('QAAttacker',a.observer))
def player_uuid(name):
 import re
 response=command('data get entity '+name+' UUID');values=[int(v) for v in re.search(r'\[I;([^]]+)\]',response).group(1).split(',')]
 return str(uuid.UUID(int=sum((value&0xffffffff)<<((3-i)*32) for i,value in enumerate(values))))
ids={'actor':player_uuid(a.player),'observer':player_uuid(a.observer)}
def observe(who='observer'):
 p=(a.server/'qa/fantasyhub')/(ids[who]+'.jsonl')
 with p.open('rb') as f:f.seek(max(0,p.stat().st_size-65536));rows=f.read().decode().splitlines()
 return json.loads(rows[-1])
records=[];snapshots={}
def send(s):
 r=command(s);records.append({'command':s,'response':r,'utc':time.time()});(out/'commands.json').write_text(json.dumps(records,indent=2));return r
def screenshot(n):subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','1280x720','-i',a.display,'-frames:v','1','-update','1',str(out/(n+'.png'))],check=True)
def place():send('tp QAPlayer 200.5 65 0.5 0 0');send('tp QAAttacker 204.2 66.7 7.5 152.13 15.0');time.sleep(.7)
def act(name,seconds=2.0,label=None):
 label=label or name
 send('execute as QAPlayer run fantasyhubqa kabuto_'+name);time.sleep(.35);screenshot(label);time.sleep(max(0,seconds-.35));snapshots[label]={'actor':observe('actor'),'observer':observe()};(out/'observations.json').write_text(json.dumps(snapshots,indent=2))
 if name not in ['prepare','charge']:
  report=json.load((a.server/'qa/kabuto-runtime.json').open());r=report.get(name,{});snapshots[label]['gameplay']=r
  if r.get('cast_accepted') is False:raise RuntimeError('Cast was rejected: '+name)
 (out/'observations.json').write_text(json.dumps(snapshots,indent=2));print(name,flush=True)
assert 'QAAttacker' in send('list') and 'QAPlayer' in send('list')
send('kill @e[tag=qa_kabuto]');send('gamemode spectator QAAttacker');send('gamerule doDaylightCycle false');send('time set noon');send('weather clear');place()
baseline={'actor':observe('actor'),'observer':observe()};(out/'baseline.json').write_text(json.dumps(baseline,indent=2))
rec=subprocess.Popen(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','1280x720','-framerate','20','-i',a.display,'-an','-c:v','libx264','-preset','ultrafast','-crf','23',str(out/'kabuto-minecraft-cobblemon-vfx.mp4')],stdin=subprocess.PIPE)
try:
 act('prepare',1.5);act('henshin',1.6);act('cast_off',4.2);place();act('charge',.7);act('clock_up',6,'clock_up');place();act('rider_kick',3.9);place();act('kunai_strike',3.9,'gun');act('kunai_mode',.7);act('kunai_strike',3.9,'kunai');act('hyper_form',1.8);act('clock_up',6,'hyper_clock_up')
 time.sleep(2);snapshots['settled']={'actor':observe('actor'),'observer':observe()};screenshot('settled');(out/'observations.json').write_text(json.dumps(snapshots,indent=2))
finally:rec.communicate(b'q',timeout=20)
final=snapshots['settled'];checks={}
for who in ['actor','observer']:
 e=final[who]['kabutoEffects'];native=e['cobblemon'];checks[who+'_minecraft']=e['minecraftParticleRequests']>baseline[who]['kabutoEffects']['minecraftParticleRequests'];checks[who+'_native_api']=native['available'];checks[who+'_real_native_particles']=native['nativeParticlesPeak']>0;checks[who+'_finite_cleanup']=e['activeTimelines']==0
 for effect in ['swordsdance_sparkle','metal_sparks','explosion_burst_flash','impact_steel','bulletpunch_targetsparks','quickattack_dashlines','thundershock_actorbolts','thunderbolt_targetimpact','impact_electric','scratch_target','scratch_targetsparks']:
  checks[who+'_native_'+effect]=native['emitters'].get(effect,0)>baseline[who]['kabutoEffects']['cobblemon']['emitters'].get(effect,0)
 checks[who+'_flash_texture_fixed']=native.get('textureRepairs',{}).get('explosion_burst_flash')=='cobblemon:vanilla/flash'
 checks[who+'_metal_texture_fixed']=native.get('textureRepairs',{}).get('metal_sparks')=='cobblemon:generic/orb/scaling'
 for phase in ['HENSHIN','CAST_OFF','LAND','CLOCK','CLOCK_END','KICK','KICK_HIT','GUN_MUZZLE','GUN_HIT','SLASH','BLEED','HYPER','DASH']:checks[who+'_'+phase]=e['phases'].get(phase,0)>baseline[who]['kabutoEffects']['phases'].get(phase,0)
report={'result':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'actor_final':final['actor']['kabutoEffects'],'observer_final':final['observer']['kabutoEffects']};(out/'verification.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2));assert report['result']=='PASS'
