from pathlib import Path
import sys,json,time,subprocess,os
sys.path.insert(0,'/workspace/scratch');import card_master_skill_qa as q
q.OUT=Path('/workspace/integration/docs/qa/rider-models/runtime/final-models');q.OUT.mkdir(exist_ok=True)
def key(name,display=':99'):
 subprocess.run(['/workspace/tools/x11/usr/bin/xdotool','key',name],env={**os.environ,'DISPLAY':display,'LD_LIBRARY_PATH':'/workspace/tools/x11/usr/lib/x86_64-linux-gnu'},check=True)
q.cmd('gamemode spectator QAAttacker');q.cmd('time set noon');q.cmd('weather clear');q.cmd('effect clear QAAttacker minecraft:night_vision')
q.cmd('fill 193 64 -6 208 64 9 minecraft:smooth_stone');q.cmd('fill 193 65 -6 208 73 -6 minecraft:gray_concrete');q.cmd('fill 193 65 9 208 73 9 minecraft:gray_concrete');q.cmd('fill 193 65 -6 193 73 9 minecraft:gray_concrete');q.cmd('fill 208 65 -6 208 73 9 minecraft:gray_concrete')
q.cmd('fill 190 159 -10 210 159 15 minecraft:air');time.sleep(3)
views={'front':'200.5 64.4 2.9 180 0','rear':'200.5 64.4 -1.9 0 0','left':'202.9 64.4 .5 90 0','right':'198.1 64.4 .5 -90 0','three-quarter':'202.2 64.4 2.2 135 0','helmet-front':'200.5 65.0 1.4 180 0','helmet-rear':'200.5 65.0 -.4 0 0','helmet-side':'201.4 65.0 .5 90 0','belt':'200.5 64.25 1.2 180 0','weapon-grip':'202.0 64.65 1.6 125 5'}
for rider in ['ryuki','knight','decade']:
 q.prepare(rider);q.cmd('kill @e[tag=rider_qa_target]');q.cmd('tp QAPlayer 200.5 65 .5 0 0');q.cmd('effect clear QAPlayer minecraft:night_vision');time.sleep(1)
 for name,view in views.items():
  q.cmd('tp QAAttacker '+view);time.sleep(.55);q.snap(rider+'-'+name)
 q.snap(rider+'-first-person',':99')
 q.cmd('tp QAAttacker '+views['three-quarter']);subprocess.run(['/workspace/tools/x11/usr/bin/xdotool','keydown','Shift_L'],env={**os.environ,'DISPLAY':':99','LD_LIBRARY_PATH':'/workspace/tools/x11/usr/lib/x86_64-linux-gnu'},check=True);time.sleep(.7);q.snap(rider+'-crouch');subprocess.run(['/workspace/tools/x11/usr/bin/xdotool','keyup','Shift_L'],env={**os.environ,'DISPLAY':':99','LD_LIBRARY_PATH':'/workspace/tools/x11/usr/lib/x86_64-linux-gnu'},check=True)
 # Use held key rather than a sticky sneaking command, then take an actual swing frame.
 env={**os.environ,'DISPLAY':':99','LD_LIBRARY_PATH':'/workspace/tools/x11/usr/lib/x86_64-linux-gnu'}
 subprocess.run(['/workspace/tools/x11/usr/bin/xdotool','mousedown','1'],env=env,check=True);time.sleep(.15);q.snap(rider+'-swing');subprocess.run(['/workspace/tools/x11/usr/bin/xdotool','mouseup','1'],env=env,check=True)
 r=q.cast(rider+('_complete' if rider=='decade' else '_survive'));q.snap(rider+'-elevated');(q.OUT/(rider+'-native-state.json')).write_text(json.dumps(r,indent=2)+'\n');q.key_f();time.sleep(.4)
 print('NATIVE_MODEL_VIEWS',rider,flush=True)
print('NATIVE_MODEL_CAPTURE_COMPLETE',flush=True)
