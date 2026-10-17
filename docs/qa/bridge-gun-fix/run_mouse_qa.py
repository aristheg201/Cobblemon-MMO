from pathlib import Path
import sys,subprocess,os,time,json,re
sys.path.insert(0,'/workspace/scratch');from expanded_qa import command
out=Path('/workspace/qa-evidence/bridge-gun-fix');server=Path('/workspace/runtime-server');checks={};records=[]
def send(c):
 r=command(c);records.append({'command':c,'response':r});(out/'commands.json').write_text(json.dumps(records,indent=2));return r
def control(display,args):
 env=os.environ.copy();env.update(DISPLAY=display,LD_LIBRARY_PATH='/workspace/tools/x11/usr/lib/x86_64-linux-gnu')
 subprocess.run(['/workspace/tools/x11/usr/bin/xdotool',*args],env=env,check=True)
def click(display,y):control(display,['mousemove','640',str(y),'click','1']);time.sleep(.9)
def observe(who):
 uid={'actor':'199f2445-7f42-3492-b97b-f1a69c57ee90','observer':'e585ad00-e318-3e35-aa1c-115aefd03416'}[who]
 p=server/'qa/fantasyhub'/(uid+'.jsonl')
 with p.open('rb') as f:f.seek(max(0,p.stat().st_size-65536));rows=f.read().decode().splitlines()
 return json.loads(rows[-1])
def snap(name):
 data={who:observe(who) for who in ['actor','observer']};(out/(name+'.json')).write_text(json.dumps(data,indent=2));return data
def capture(name,display=':99'):
 subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','1280x720','-i',display,'-frames:v','1','-update','1',str(out/(name+'.png'))],check=True)
def wait_until(fn,label,timeout=45):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  if fn():return
  time.sleep(.3)
 raise RuntimeError('Timeout '+label)
wait_until(lambda:'Done (' in (out/'server-runtime.log').read_text(errors='replace'),'server startup',200)
for player,display,ys in [('QAPlayer',':99',[460,589,535]),('QAAttacker',':101',[418,632,416])]:
 control(display,['windowfocus','2097159'])
 for y in ys:click(display,y)
 wait_until(lambda:player in send('list'),'player join '+player,45);print(player,'connected',flush=True);time.sleep(3)
send('kill @e[tag=qa_kabuto]');send('kill @e[tag=qa_gun_target]');send('gamemode spectator QAAttacker')
send('tp QAPlayer 200.5 65 0.5 0 0');send('tp QAAttacker 202.8 65.8 2.7 133 10')
send('execute as QAPlayer run fantasyhubqa kabuto_prepare');time.sleep(.8)
send('execute as QAPlayer run fantasyhubqa kabuto_henshin');time.sleep(1)
send('execute as QAPlayer run fantasyhubqa kabuto_gun_air')
def air_done():
 p=server/'qa/kabuto-runtime.json'
 return p.exists() and 'verdict' in json.loads(p.read_text()).get('gun_air',{})
wait_until(air_done,'native air shot')
air=json.loads((server/'qa/kabuto-runtime.json').read_text())['gun_air'];(out/'native-air.json').write_text(json.dumps(air,indent=2));checks['native_air_callback']=air['verdict']=='PASS';print('Native air',air,flush=True)
# Switch the existing primary camera from front third person to first person.
control(':99',['key','F5']);time.sleep(.8)
rec=subprocess.Popen(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','1280x720','-framerate','20','-i',':99','-an','-c:v','libx264','-preset','ultrafast','-crf','23',str(out/'native-right-click.mp4')],stdin=subprocess.PIPE)
try:
 time.sleep(1);baseline=snap('before-native-mouse')
 # These are actual right mouse inputs, not a direct skill command.
 control(':99',['click','3']);time.sleep(.18);control(':99',['click','3']);time.sleep(1.2)
 air_after=snap('after-native-air-mouse')
 for who in ['actor','observer']:checks[who+'_air_right_click_sound']=air_after[who]['gunSounds']==baseline[who]['gunSounds']+1
 checks['air_does_not_build_energy']=air_after['actor']['kabuto']['energy']==baseline['actor']['kabuto']['energy']
 capture('air-shot')
 send('tp QAPlayer 200.5 65 0.5 0 0')
 send('summon minecraft:cow 200.5 65 6.5 {NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Tags:["qa_gun_target"]}')
 send('attribute @e[tag=qa_gun_target,limit=1] minecraft:generic.max_health base set 200')
 send('data merge entity @e[tag=qa_gun_target,limit=1] {Health:200f}')
 send('fill 199 66 3 201 67 3 minecraft:stone');time.sleep(1.3)
 control(':99',['click','3']);time.sleep(1.2)
 blocked=send('data get entity @e[tag=qa_gun_target,limit=1] Health');checks['wall_blocks_damage']='200.0f' in blocked;capture('wall-stops-shot')
 send('fill 199 66 3 201 67 3 minecraft:air');time.sleep(.3)
 control(':99',['click','3']);time.sleep(1.2)
 hit=send('data get entity @e[tag=qa_gun_target,limit=1] Health');numbers=re.findall(r'([-+]?\d+(?:\.\d+)?)f',hit)
 checks['native_mouse_hits_mob']=bool(numbers) and float(numbers[-1])<200
 capture('target-hit');snap('after-target')
 print('Target health',blocked,hit,flush=True)
finally:
 rec.communicate(b'q',timeout=15)
 send('fill 199 66 3 201 67 3 minecraft:air');send('kill @e[tag=qa_gun_target]')
report={'result':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'source':'Actual dedicated Fabric server, two connected clients, native UseItemCallback and xdotool right mouse input','native_air':air}
(out/'verification.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2),flush=True)
if report['result']!='PASS':raise RuntimeError('Gun QA failed')
