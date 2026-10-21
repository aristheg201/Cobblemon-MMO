#!/usr/bin/env python3
"""Capture EVERY Thor/Strange/Spider/Batman skill in an existing native multiplayer session.

This script never starts/stops/deploys clients or server. --execute is required to change the
QA world. Video frames come exclusively from X11; screenshots are native frame captures.
Requires both existing clients joined, SVFRAME_RUNTIME_QA=1 and ffmpeg/x11grab.
"""
from pathlib import Path
import argparse, importlib.util, json, os, subprocess, time

ROOT=Path(__file__).resolve().parents[1]
HEROES=('THOR','SORCERER_SUPREME','SPIDER_MAN','BATMAN')
EXPECTED={'THOR':8,'SORCERER_SUPREME':7,'SPIDER_MAN':7,'BATMAN':7,'FLASH':7,'IRON_MAN':7}
RECAST={'STORMBREAKER_THROW':1.7,'COCOON_SLAM':1.4,'ELDRITCH_WHIP':.9,'BATARANG':1.0}

def matrix(catalog, heroes=HEROES):
 rows=[]
 for hid in heroes:
  hero=next(h for h in catalog['heroes'] if h['id']==hid)
  if len(hero['abilities'])!=EXPECTED[hid]:raise RuntimeError(f'{hid}: expected {EXPECTED[hid]} skills, found {len(hero["abilities"])}')
  for skill in hero['abilities']:
   last=max((p.get('start',0)+max(0,p.get('hits',1)-1)*p.get('interval',0) for p in skill.get('phases',[])),default=0)
   end=max(last,skill.get('cast_time',0),skill.get('duration',0),RECAST.get(skill['key'],0))
   rows.append({'hero':hid,'skill':skill['id'],'key':skill['key'],'duration':max(5,end+2),'lastDamagePhase':last,'castTime':skill.get('cast_time',0),'recastAt':RECAST.get(skill['key'])})
 return rows

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--execute',action='store_true',help='Run against the already running QA world; without this flag only print the matrix.')
 p.add_argument('--heroes',default=','.join(HEROES),help='Comma-separated native hero class IDs')
 p.add_argument('--server',type=Path,default=Path('/workspace/runtime-server'))
 p.add_argument('--output',type=Path,default=Path('/workspace/qa-evidence/hero-overhaul/native-29-skills'))
 p.add_argument('--display',default=':100',help='runtime-client-two observer display')
 p.add_argument('--actor',default='QAPlayer');p.add_argument('--actor-uuid',default='199f2445-7f42-3492-b97b-f1a69c57ee90')
 p.add_argument('--observer',default='QAAttacker');p.add_argument('--observer-uuid',default='e585ad00-e318-3e35-aa1c-115aefd03416')
 p.add_argument('--camera-fix',action='store_true',help='Side-on full axe corridor and elevated leap camera for supplementary coverage');p.add_argument('--reactive',action='store_true',help='Exercise shield/block response with a controlled incoming melee hit');p.add_argument('--angles',default='wide,close');p.add_argument('--only',default='',help='Comma-separated full skill IDs, optional subset for reruns')
 p.add_argument('--audio-source',default='',help='Optional Pulse monitor source. Omitted means the video has no audio; report records this.')
 p.add_argument('--size',default='1280x720');p.add_argument('--fps',type=int,default=30)
 a=p.parse_args();rows=matrix(json.loads((ROOT/'core/src/main/resources/default/heroes.json').read_text()),a.heroes.split(','))
 if a.only:rows=[r for r in rows if r['skill'] in a.only.split(',')]
 if not a.execute:print(json.dumps({'execute':False,'skillCount':len(rows),'angles':a.angles.split(','),'matrix':rows},indent=2));return
 props=dict(l.split('=',1) for l in (a.server/'server.properties').read_text().splitlines() if '=' in l and not l.startswith('#'))
 spec=importlib.util.spec_from_file_location('runtime_qa',ROOT/'tools/run_runtime_qa.py');mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
 def cmd(text):return mod.command('127.0.0.1',int(props.get('rcon.port','25575')),props['rcon.password'],text)
 def qa(text):
  path=a.server/'qa/heroes'/f'{a.actor_uuid}-{text.split()[0]}.json'
  previous=path.stat().st_mtime_ns if path.exists() else None
  response=cmd(f'heroqa {a.actor} {text}')
  if not path.exists() or path.stat().st_mtime_ns==previous:raise RuntimeError('QA report was not freshly written for '+text+'; RCON response: '+response[:300])
  report=json.loads(path.read_text())
  if report.get('action')!=text.split()[0]:raise RuntimeError('Mismatched QA report action')
  return report
 def observation(uid):
  path=a.server/'qa/fantasyhub'/f'{uid}.jsonl'
  if not path.exists():return {'unavailable':str(path)}
  with path.open('rb') as f:f.seek(max(0,path.stat().st_size-120000));lines=f.read().decode(errors='replace').splitlines()
  for line in reversed(lines):
   try:return json.loads(line)
   except json.JSONDecodeError:pass
  return {'unavailable':'No complete observation row'}
 if a.actor not in cmd('list') or a.observer not in cmd('list'):raise RuntimeError('Both native clients must already be connected')
 a.output.mkdir(parents=True,exist_ok=True)
 meta={'nativeSource':'X11 '+a.display,'audio':a.audio_source or 'NOT CAPTURED: no Pulse monitor selected','client':'runtime-client-two','skillsExpected':len(rows),'startedUtc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'status':'RUNNING','results':[]}
 def save():(a.output/'report.json').write_text(json.dumps(meta,indent=2))
 def shot(path):subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size',a.size,'-i',a.display,'-frames:v','1','-update','1',str(path)],check=True)
 cmd(f'gamemode spectator {a.observer}')
 cmd('gamerule doMobSpawning false')
 for row in rows:
  for angle in a.angles.split(','):
   if angle not in ('wide','close'):raise RuntimeError('Angles must be wide or close')
   name=row['skill'].lower()+'-'+angle;out=a.output/name;out.mkdir(exist_ok=True)
   qa('prepare '+row['hero']);cmd('kill @e[type=cobblemon:pokemon,nbt={Pokemon:{PokemonOriginalTrainerType:"NONE"}}]');cmd(f'effect give {a.actor} minecraft:resistance 9999 4 true');qa('equip');cmd(f'tp {a.actor} 90.5 70 90.5 0 6');qa('fixture')
   if row['key']=='BATCLAW':
    cmd(f'execute at {a.actor} run tp @e[tag=hero_qa_target,distance=..40,sort=nearest,limit=1] 90.5 70 98.5')
   if row['hero']=='THOR':qa('weapon');qa('back thor')
   elif row['hero']=='BATMAN':qa('back batman')
   elif row['hero']=='SORCERER_SUPREME':qa('back strange')
   if row['key']=='BATCLAW':cmd(f'tp {a.observer} '+('100.5 74 94.5 90 12' if angle=='wide' else '98.5 72.5 94.5 90 10'))
   elif a.camera_fix and row['key']=='STORMBREAKER_THROW':cmd(f'tp {a.observer} 105 76 100.5 90 15')
   elif a.camera_fix and row['key']=='BRING_ME_THANOS':cmd(f'tp {a.observer} 95 75 94 128 16')
   elif angle=='wide':cmd(f'tp {a.observer} 98 73.2 96 128 15')
   else:cmd(f'tp {a.observer} 94.5 71.8 93.5 128 12')
   time.sleep(.25)
   if a.reactive and row['key'] in ('WEB_SHIELD','TAO_MANDALAS'):cmd(f'effect clear {a.actor} minecraft:resistance')
   before=qa('inspect')
   if not before.get('targets') or any(t['health']<999 for t in before['targets']):raise RuntimeError('Fresh full-health 1000HP fixture required before '+row['skill'])
   cmd(f'title {a.observer} actionbar '+json.dumps({'text':row['skill']+' | 00-before | HP '+str(before['targets'][0]['health']),'color':'gold'}));time.sleep(.15)
   shot(out/'00-before.png')
   args=['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size',a.size,'-framerate',str(a.fps),'-i',a.display]
   if a.audio_source:args+=['-f','pulse','-i',a.audio_source]
   args+=['-c:v','libx264','-preset','ultrafast','-crf','20']
   if a.audio_source:args+=['-c:a','aac','-b:a','160k']
   record=subprocess.Popen(args+[str(out/'native.mp4')],stdin=subprocess.PIPE,stderr=(out/'ffmpeg.log').open('wb'))
   result={'skill':row['skill'],'hero':row['hero'],'angle':angle,'before':before,'phases':[],'video':str(out/'native.mp4'),'audioCaptured':bool(a.audio_source),'nativeVisualReview':'PENDING'}
   try:
    cmd(f'title {a.observer} actionbar '+json.dumps({'text':row['skill']+' | '+angle+' | target HP '+str(before['targets'][0]['health']),'color':'gold'}))
    start=time.monotonic();result['cast']=qa('cast '+row['skill'])
    if not result['cast'].get('accepted'):raise RuntimeError(row['skill']+' rejected')
    if row['key']=='BATCLAW':cmd(f'execute at {a.actor} run data merge entity @e[tag=hero_qa_target,distance=..40,sort=nearest,limit=1] {{NoAI:0b}}')
    milestones=[(.12,'01-telegraph'),(.38,'02-cast'),(.8,'03-travel-channel'),(max(1.2,row['lastDamagePhase']+.15),'04-impact'),(row['duration']-.4,'05-end')]
    if row['recastAt']:milestones.append((row['recastAt'],'recast'))
    if a.reactive and row['key'] in ('WEB_SHIELD','TAO_MANDALAS'):milestones.extend([(.65,'incoming'),(1.15,'incoming'),(1.65,'incoming')])
    for at,label in sorted(milestones):
     while time.monotonic()-start<at:time.sleep(max(0,min(.1,at-(time.monotonic()-start))))
     if label=='recast':result['recast']=qa('cast '+row['skill']);continue
     if label=='incoming':result.setdefault('incoming',[]).append(qa('incoming 6'));continue
     inspection=qa('inspect');hp=', '.join(str(round(t['health'],2)) for t in inspection.get('targets',[])) or 'no target';cmd(f'title {a.observer} actionbar '+json.dumps({'text':row['skill']+' | '+label+' | HP '+hp,'color':'gold'}))
     shot(out/(label+'.png'));result['phases'].append({'label':label,'seconds':round(time.monotonic()-start,3),'inspection':inspection,'actorObservation':observation(a.actor_uuid),'observerObservation':observation(a.observer_uuid)})
    result['after']=qa('inspect')
    before_health={t['uuid']:t['health'] for t in before.get('targets',[])}
    result['targetHealthDelta']={t['uuid']:before_health[t['uuid']]-t['health'] for t in result['after'].get('targets',[]) if t['uuid'] in before_health}
    result['reportedAppliedDamage']=sum(h.get('damage',0) for h in result['after'].get('hits',[]))
    result['accepted']=True
   except Exception as ex:
    result['error']=str(ex);result['accepted']=False
   finally:
    if record.poll() is None:record.communicate(b'q\n',timeout=30)
    result['ffmpegExit']=record.returncode
    (out/'evidence.json').write_text(json.dumps(result,indent=2));meta['results'].append(result);save()
   print(name,'accepted',result['accepted'],'damage',result.get('reportedAppliedDamage'),'native visual review PENDING',flush=True)
 meta['status']='CAPTURED; MANUAL NATIVE VISUAL AND AUDIO REVIEW REQUIRED';save()
 print('Captured',len(meta['results']),'native angle runs; this does not certify visual pass.',flush=True)
if __name__=='__main__':main()
