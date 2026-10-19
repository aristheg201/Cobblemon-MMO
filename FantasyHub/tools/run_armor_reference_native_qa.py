#!/usr/bin/env python3
"""Capture actual armor rendering and check visual form state on two local clients.
Uses the CLI and safeguards of run_card_rider_native_qa.py.
Requires an isolated test world and opt-in QA mode on both clients and server.
"""
from pathlib import Path
import sys,json,time,subprocess,os,hashlib
# Reuse the existing opt-in native RCON/player/report harness and CLI.
import run_card_rider_native_qa as q
results=[]
def key(name,display=':99',action='key'):
 subprocess.run([q.args.xdotool,action,name],env={**os.environ,'DISPLAY':display},check=True)
def status():q.cmd('execute as QAPlayer run fantasyhubqa rider_game_status');return q.report('status')
def store(name,data,checks):
 data['checks']=checks;(q.OUT/(name+'.json')).write_text(json.dumps(data,indent=2)+'\n');results.append({'scenario':name,**checks});(q.OUT/'results.json').write_text(json.dumps(results,indent=2)+'\n');print(name,json.dumps(checks),flush=True);assert all(checks.values()),checks
until=time.monotonic()+120
while time.monotonic()<until:
 try:online=q.cmd('list')
 except (ConnectionError,OSError):time.sleep(1);continue
 if 'QAPlayer' in online and 'QAAttacker' in online:break
 time.sleep(1)
else:raise RuntimeError('Real clients failed to join')
# Wait for the local clients to finish entering the world before sending keys.
time.sleep(3)
q.cmd('gamemode spectator QAAttacker');q.cmd('time set noon');q.cmd('gamerule doDaylightCycle false');q.cmd('weather clear');q.cmd('effect clear QAAttacker');key('F1',':101')
views={'front':'200.5 64.55 4.1 180 0','rear':'200.5 64.55 4.1 180 0','left':'204.1 64.55 .5 90 0','right':'196.9 64.55 .5 -90 0','three-quarter':'203.2 64.7 3.2 135 2','helmet-front':'200.5 65.28 2.05 180 0','helmet-side':'202.05 65.28 .5 90 0','helmet-rear':'200.5 65.28 2.05 180 0','belt':'200.5 64.24 1.45 180 0','grip':'202.5 64.7 2.1 128 7'}
def photos(label):
 for name,view in views.items():
  q.cmd('tp QAPlayer 200.5 65 .5 '+('180' if name in ['rear','helmet-rear'] else '0')+' 0');q.cmd('tp QAAttacker '+view);time.sleep(1.5);q.snap(label+'-'+name)
 q.cmd('tp QAPlayer 200.5 65 .5 0 0');time.sleep(1);q.snap(label+'-first-person',':99');q.cmd('tp QAAttacker '+views['three-quarter'])
 key('Shift_L',action='keydown');time.sleep(.6);q.snap(label+'-crouch');key('Shift_L',action='keyup')
 key('1',action='mousedown');time.sleep(.15);q.snap(label+'-swing');key('1',action='mouseup')
 # Capture the complete silhouette during real movement, after the crouch pose resets.
 key('w',action='keydown');time.sleep(.35);q.snap(label+'-walk');key('w',action='keyup');q.cmd('tp QAPlayer 200.5 65 .5 0 0')
q.prepare('ryuki');q.cmd('kill @e[tag=rider_qa_target]');q.cmd('effect clear QAPlayer');time.sleep(4);before=status();inv=q.cmd('data get entity QAPlayer Inventory');photos('ryuki-normal');after=status();store('normal-ryuki-model',{'before':before,'after':after},{'realHenshin':after['settled']['active'],'normalModel':after['settled']['form']=='ryuki','inventoryPreserved':q.cmd('data get entity QAPlayer Inventory')==inv})
r=q.cast('ryuki_survive');q.cmd('effect clear QAPlayer');time.sleep(.5);photos('ryuki-survive');after=status();store('survive-model-is-distinct',{'cast':r,'after':after},{'castAccepted':r['castResult'],'surviveModel':after['settled']['form']=='ryuki_survive','stanceElevated':after['settled']['elevated'],'inventoryPreserved':q.cmd('data get entity QAPlayer Inventory')==inv})
r=q.cast('ryuki_survive');store('survive-reverts-to-normal',r,{'accepted':r['castResult'],'normalModelRestored':r['settled']['form']=='ryuki','stanceOff':not r['settled']['elevated'],'inventoryPreserved':r['inventoryBefore']==r['inventoryAfter']})
q.prepare('decade');q.cmd('kill @e[tag=rider_qa_target]');q.cmd('effect clear QAPlayer');time.sleep(4);photos('decade');inv=q.cmd('data get entity QAPlayer Inventory');r=q.cast('decade_complete');q.snap('decade-complete-same-armor');store('decade-complete-no-model-change',r,{'accepted':r['castResult'],'sameModel':r['settled']['form']=='decade','elevated':r['settled']['elevated'],'inventoryPreserved':q.cmd('data get entity QAPlayer Inventory')==inv})
q.key_f();after=status();q.snap('F-human',':99');store('F-clears-armor',after,{'inactive':not after['settled']['active'],'noModel':after['settled']['form']==''})
# Focused gameplay regression after the visual routing change. Existing damage/cost values stay unchanged.
q.cmd('time set midnight');q.cmd('effect give QAAttacker minecraft:night_vision 600 0 true');q.cmd('tp QAAttacker 207.5 66 3.5 109 -10')
for skill in ['strike_vent','sword_vent','final_vent']:
 q.prepare('ryuki',True);r=q.cast('ryuki_'+skill);store('survive-gameplay-'+skill,r,{'accepted':r['castResult'],'realHits':bool(r['hits']),'correctModel':r['settled']['form']=='ryuki_survive','inventoryPreserved':r['inventoryBefore']==r['inventoryAfter'],'projectilesCleaned':r['settled']['projectiles']==0})
q.prepare('decade',True);r=q.cast('decade_dimension_kick');store('decade-gameplay-dimension',r,{'accepted':r['castResult'],'realHits':bool(r['hits']),'sameDecadeModel':r['settled']['form']=='decade','inventoryPreserved':r['inventoryBefore']==r['inventoryAfter']})
print('ARMOR_NATIVE_V13',len(results),'PASS',flush=True)
