#!/usr/bin/env python3
"""Native single-client UI checks; reuses the existing opt-in RCON fixture CLI.
Requires QAPlayer on display :99, ffmpeg and xdotool, in an isolated test world.
Test progression/equipment are granted by the existing fixture; screenshots are
raw client captures, and casts use the real server skill runtime.
"""
from pathlib import Path
import json,time,os,subprocess
import run_card_rider_native_qa as q
OBS=q.args.server/'qa/fantasyhub/199f2445-7f42-3492-b97b-f1a69c57ee90.jsonl'
rows=[]
def observation():
 with OBS.open('rb') as f:
  f.seek(max(0,OBS.stat().st_size-65536));data=f.read().decode().splitlines()
 return json.loads(data[-1])
def capture(name,checks=None):
 time.sleep(.6);q.snap(name,':99');obs=observation();result={'scenario':name,'checks':{'sevenRenderedSlots':obs.get('renderedSkillSlots')==7,**(checks or {})},'observation':obs}
 (q.OUT/(name+'.json')).write_text(json.dumps(result,indent=2)+'\n');rows.append({'scenario':name,**result['checks']});(q.OUT/'results.json').write_text(json.dumps(rows,indent=2)+'\n');print(name,result['checks'],flush=True);assert all(result['checks'].values())
until=time.monotonic()+210
while time.monotonic()<until:
 try:
  if 'QAPlayer' in q.cmd('list') and OBS.exists() and time.time()-OBS.stat().st_mtime<3:break
 except (OSError,ConnectionError,json.JSONDecodeError):pass
 time.sleep(1)
else:raise RuntimeError('Native client not ready')
time.sleep(3);q.cmd('time set noon');q.cmd('gamerule doDaylightCycle false');q.cmd('weather clear')
for rider in ['ryuki','knight','decade']:
 q.prepare(rider);q.cmd('kill @e[tag=rider_qa_target]');time.sleep(3)
 capture(rider+'-ready')
 if rider=='ryuki':
  r=q.cast('ryuki_strike_vent',False);capture('ryuki-cooldown',{'actualCastAccepted':r['castResult'],'actualCooldown':r['cooldownAfter']>0});q.report('cast_ryuki_strike_vent')
 if rider=='decade':
  r=q.cast('decade_slash_blast',False);capture('decade-recast',{'actualCastAccepted':r['castResult']});q.report('cast_decade_slash_blast')
 r=q.cast(rider+('_complete' if rider=='decade' else '_survive'));capture(rider+'-elevated',{'actualCastAccepted':r['castResult'],'elevated':r['settled']['elevated']})
q.key_f();time.sleep(1);q.cmd('execute as QAPlayer run fantasyhubqa rider_game_status');r=q.report('status');q.snap('F-hidden',':99');obs=observation();result={'scenario':'F-hides-skillbar','inactive':not r['settled']['active'],'zeroRenderedSlots':obs.get('renderedSkillSlots')==0};rows.append(result);(q.OUT/'F-hidden.json').write_text(json.dumps({'checks':result,'observation':obs},indent=2)+'\n');(q.OUT/'results.json').write_text(json.dumps(rows,indent=2)+'\n');assert result['inactive'] and result['zeroRenderedSlots'];# A smaller native window exercises Minecraft's lower effective GUI scale.
q.key_f();time.sleep(1)
window=subprocess.check_output([q.args.xdotool,'search','--onlyvisible','--name','^Minecraft'],env={**os.environ,'DISPLAY':':99'},text=True).splitlines()[0]
try:
 subprocess.run([q.args.xdotool,'windowsize',window,'960','540'],env={**os.environ,'DISPLAY':':99'},check=True);time.sleep(1.5)
 subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','960x540','-i',':99','-frames:v','1','-update','1',str(q.OUT/'decade-small-window.png')],check=True)
 obs=observation();r={'scenario':'small-window-960x540','sevenRenderedSlots':obs.get('renderedSkillSlots')==7,'width':960,'height':540};assert r['sevenRenderedSlots'];rows.append(r);(q.OUT/'results.json').write_text(json.dumps(rows,indent=2)+'\n');(q.OUT/'small-window.json').write_text(json.dumps({'checks':r,'observation':obs},indent=2)+'\n')
finally:
 subprocess.run([q.args.xdotool,'windowsize',window,'1280','720'],env={**os.environ,'DISPLAY':':99'},check=True);q.key_f()
q.cmd('execute as QAPlayer run trinkets set legs belt 0 minecraft:air');q.cmd('execute as QAPlayer run fantasyhubqa kabuto_prepare');time.sleep(2);q.key_f();time.sleep(2)
capture('kabuto-gauges',{'kabutoStateVisible':'kabuto' in observation()});q.key_f()
print('NATIVE_HUD',len(rows),'PASS',flush=True)
