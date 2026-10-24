from pathlib import Path
import sys,subprocess,time,json,os,re
runtime=Path(os.environ.get("FANTASY_RUNTIME","/workspace/runtime-master-server"))
source=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(source/'tools'))
from run_runtime_qa import command as rcon
props=dict(line.split('=',1) for line in (runtime/'server.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
def command(text):return rcon('127.0.0.1',int(props.get('rcon.port','25575')),props['rcon.password'],text)
phase=sys.argv[1];root=Path(os.environ.get('FANTASY_EVIDENCE','qa-performance'))/phase;root.mkdir(parents=True,exist_ok=True)
def cmd(c):
 try:r=command(c)
 except Exception as e:r=str(e)
 with (root/'commands.jsonl').open('a') as f:f.write(json.dumps({'at':time.time(),'command':c,'result':r})+'\n')
 return r
def await_profile(previous):
 deadline=time.monotonic()+45
 while time.monotonic()<deadline:
  files=[p for p in (runtime/'config/spark').glob('*.sparkprofile') if p not in previous]
  if files:
   latest=max(files,key=lambda p:p.stat().st_mtime)
   if latest.stat().st_size>0:return latest
  time.sleep(.25)
 raise RuntimeError('Spark did not finish writing a fresh profile within 45 seconds')
window=int(os.environ.get("FANTASY_WINDOW","60"));warmup=int(os.environ.get("FANTASY_WARMUP","30"));assert window>=30 and window%5==0 and warmup>=20
for count in map(int,os.environ.get("FANTASY_COUNTS","1,5,10,20,40").split(",")):
 path=root/str(count);path.mkdir(exist_ok=True);env=dict(os.environ,BOT_PREFIX='Bench')
 proc=subprocess.Popen(['node','bots.cjs',str(count),'240'],cwd=Path(__file__).resolve().parent,env=env,stdout=(path/'bots.log').open('w'),stderr=subprocess.STDOUT)
 expected={f'Bench{i:02}' for i in range(1,count+1)};positioned=set();deadline=time.monotonic()+40
 while time.monotonic()<deadline and positioned!=expected:
  online=cmd('list');names=set(re.findall(r'Bench[0-9]+',online))
  for name in names-positioned:
   i=int(name[5:]);cmd(f'tp {name} {83+(i%8)*2} 71 {83+(i//8)*2}');positioned.add(name)
  time.sleep(.3)
 if positioned!=expected:raise RuntimeError(f'Only {len(positioned)}/{count} connected')
 for i in range(1,count+1):
  name=f'Bench{i:02}'
  for c in [f'tp {name} {83+(i%8)*2} 71 {83+(i//8)*2}',f'svframemmo admin class {name} THOR',f'svframemmo admin exp give {name} 200000',f'svframemmo skill unlock {name} THOR_THUNDEROUS_SLAM',f'execute as {name} run svframemmo skill bind 1 THOR_THUNDEROUS_SLAM',f'execute as {name} run commerceqa seed x 0']:
   cmd(c)
  if i%5==1:cmd(f'execute as {name} at @s run spawnpokemon pikachu lvl=50')
 cmd('loadcatalogqa Bench01');import shutil;shutil.copy2(runtime/'config/FantasyHubLoadProbe/catalog.json',path/'catalog.json')
 cmd('effect give @a minecraft:instant_health 1 10 true');cmd('worldboss abort');
 result=cmd('worldboss test start thanos scheduled')
 if 'error' in result.lower():raise RuntimeError('WorldBoss fixture failed: '+result)
 time.sleep(warmup)
 cmd('fantasyhubperf reset');cmd('loadprobe start');cmd('spark profiler cancel');time.sleep(.5)
 previous_profiles=set((runtime/'config/spark').glob('*.sparkprofile'))
 cmd(f'spark profiler start --timeout {window} --save-to-file')
 at=time.monotonic();samples=[];pid=int((root/'server.pid').read_text())
 for step in range(window//5):
  time.sleep(max(0,at+5*(step+1)-time.monotonic()))
  data={'at':time.time(),'list':cmd('list'),'perf':cmd('fantasyhubperf')}
  try:data['processStat']=Path(f'/proc/{pid}/stat').read_text();data['processStatus']=[x for x in Path(f'/proc/{pid}/status').read_text().splitlines() if x.startswith(('VmRSS:','Threads:'))]
  except Exception:pass
  samples.append(data)
  if step in [window//15,window//5-2]:
   for i in range(1,count+1,4):cmd(f'item replace entity Bench{i:02} armor.head with minecraft:iron_helmet');cmd(f'execute as Bench{i:02} run fantasyhubqa damage')
  if step==2:cmd('svframemmo admin class Bench01 FLASH')
  if step==3:cmd('svframemmo admin class Bench01 THOR')
  if step==window//5-2:cmd('fantasyshop reload')
 cmd(f'loadprobe stop {phase}-{count}');time.sleep(3);cmd('spark tps')
 import shutil
 shutil.copy2(runtime/f'config/FantasyHubLoadProbe/{phase}-{count}.json',path/'ticks.json')
 (path/'samples.json').write_text(json.dumps(samples,indent=2))
 shutil.copy2(await_profile(previous_profiles),path/'cpu.sparkprofile')
 if count==20:
  cmd('spark profiler cancel');time.sleep(.5);cmd('loadprobe start');previous_profiles=set((runtime/'config/spark').glob('*.sparkprofile'));cmd(f'spark profiler start --alloc --timeout {window} --save-to-file');time.sleep(window);cmd(f'loadprobe stop {phase}-20alloc');time.sleep(3);shutil.copy2(await_profile(previous_profiles),path/'alloc.sparkprofile')
 cmd('worldboss abort');cmd('kill @e[type=cobblemon:pokemon]')
 proc.terminate();proc.wait(timeout=15);cmd('kick @a[name=!QAClass024] matrix phase completed');time.sleep(2)
 cleanup=cmd('fantasyhubperf');(path/'cleanup.json').write_text(cleanup)
 parsed=json.loads(cleanup)
 if 'pipeline' in parsed:assert parsed['pipeline']['sessions']==0 and parsed['connectedPlayers']==0,parsed
 print(phase,count,'done',flush=True)
