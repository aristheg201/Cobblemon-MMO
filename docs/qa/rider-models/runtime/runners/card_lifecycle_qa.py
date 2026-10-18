from pathlib import Path
import sys,json,time,subprocess,os,re
sys.path.insert(0,'/workspace/scratch');import card_master_skill_qa as q
q.OUT=Path('/workspace/integration/docs/qa/rider-models/runtime/final-lifecycle');q.OUT.mkdir(exist_ok=True)
results=[]
def store(name,data,checks):
 data['checks']=checks;(q.OUT/(name+'.json')).write_text(json.dumps(data,indent=2)+'\n');results.append({'scenario':name,**checks});(q.OUT/'results.json').write_text(json.dumps(results,indent=2)+'\n');print(name,json.dumps(checks),flush=True);assert all(checks.values()),(name,checks)
def state():
 raw=q.cmd('fantasyhub inspect QAPlayer');return json.loads(raw[raw.index('{'):])
def status():
 q.cmd('execute as QAPlayer run fantasyhubqa rider_game_status');return q.report('status')
def attr(name):
 raw=q.cmd('attribute QAPlayer minecraft:generic.'+name+' get');return float(re.findall(r'[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?',raw)[-1])
def key(name):
 subprocess.run(['/workspace/tools/x11/usr/bin/xdotool','key',name],env={**os.environ,'DISPLAY':':99','LD_LIBRARY_PATH':'/workspace/tools/x11/usr/lib/x86_64-linux-gnu'},check=True)
# Three real toggle cycles: permanent attribute identifiers must never accumulate.
for rider,attribute,multiplier in [('ryuki','movement_speed',1.2),('knight','attack_speed',2.0)]:
 q.prepare(rider);base=attr(attribute);samples=[]
 for i in range(3):
  on=q.cast(rider+'_survive');active=attr(attribute);off=q.cast(rider+'_survive');restored=attr(attribute);samples.append({'on':on,'off':off,'activeAttribute':active,'restoredAttribute':restored})
 store(rider+'-three-toggle-cycles',{'base':base,'samples':samples},{'allOnAccepted':all(s['on']['castResult'] and s['on']['settled']['elevated'] for s in samples),'allOffAccepted':all(s['off']['castResult'] and not s['off']['settled']['elevated'] for s in samples),'noDuplicateModifiers':all(abs(s['activeAttribute']/base-multiplier)<.005 for s in samples),'baselineRestored':all(abs(s['restoredAttribute']-base)<1e-6 for s in samples)})
# Dimension transition reconstructs a human session instead of leaking Survive modifiers.
q.prepare('ryuki',True);before=state();q.cmd('gamemode spectator QAPlayer');q.cmd('execute in minecraft:the_nether run tp QAPlayer 0.5 128 0.5');after=status();q.snap('dimension-human',':99');dimension=q.cmd('data get entity QAPlayer Dimension');q.cmd('execute in minecraft:overworld run tp QAPlayer 200.5 65 0.5 0 0');q.cmd('gamemode survival QAPlayer');returned=status()
store('dimension-state-reset',{'before':before,'after':after,'dimension':dimension,'returned':returned},{'enteredNether':'the_nether' in dimension,'humanInNewDimension':not after['settled']['active'],'humanAfterReturn':not returned['settled']['active'],'sameClass':returned['settled']['class']=='RYUKI','cleanActors':returned['settled']['summon']=='' and returned['settled']['projectiles']==0})
# Real death/respawn, preserving only normal class/loadout data.
q.prepare('knight');base=attr('attack_speed');q.cast('knight_survive');before=state();keep=q.cmd('gamerule keepInventory');instant=q.cmd('gamerule doImmediateRespawn');q.cmd('gamerule keepInventory true');q.cmd('gamerule doImmediateRespawn true');q.cmd('kill QAPlayer');time.sleep(2)
try:
 after=status();afterstate=state();speed=attr('attack_speed');q.snap('death-respawn-human',':99')
 store('death-respawn-no-leak',{'before':before,'after':after,'profile':afterstate,'attackSpeed':speed,'baseAttackSpeed':base},{'human':not after['settled']['active'],'sameClass':afterstate['classId']=='KNIGHT','levelPreserved':afterstate['level']==before['level'],'loadoutPreserved':afterstate['loadout']==before['loadout'],'baselineSpeed':abs(speed-base)<1e-6,'cleanActors':after['settled']['summon']=='' and after['settled']['projectiles']==0})
finally:
 q.cmd('gamerule keepInventory '+('true' if 'true' in keep else 'false'));q.cmd('gamerule doImmediateRespawn '+('true' if 'true' in instant else 'false'))
# Disconnect while a real dragon is alive, inspect native entity removal before reconnect.
q.prepare('ryuki');r=q.cast('ryuki_advent',False);summon=r['immediate']['summon'];assert summon,r
q.cmd('kick QAPlayer Rider lifecycle QA');time.sleep(.35);entity=q.cmd('data get entity '+summon);online=q.cmd('list');time.sleep(1)
for y in [446,589,535]:
 subprocess.run(['/workspace/tools/x11/usr/bin/xdotool','mousemove','640',str(y),'click','1'],env={**os.environ,'DISPLAY':':99','LD_LIBRARY_PATH':'/workspace/tools/x11/usr/lib/x86_64-linux-gnu'},check=True);time.sleep(.8)
end=time.monotonic()+35
while time.monotonic()<end:
 if 'QAPlayer' in q.cmd('list'):break
 time.sleep(.5)
else:raise RuntimeError('Actual client reconnect timeout')
after=status();store('logout-during-live-summon',{'cast':r,'entityAfterDisconnect':entity,'onlineAfterDisconnect':online,'afterReconnect':after},{'liveSummonBeforeLogout':bool(summon),'actorRemoved':'No entity' in entity or 'not found' in entity,'loggedOut':'QAPlayer' not in online,'humanAfterReconnect':not after['settled']['active'],'cleanActors':after['settled']['summon']=='' and after['settled']['projectiles']==0,'sameClass':after['settled']['class']=='RYUKI'})
# The real client reloads resources while the server-owned form remains intact.
q.prepare('decade',True);key('F3+t');time.sleep(1);q.snap('resource-reload-progress',':99');time.sleep(12);after=status();q.snap('resource-reload-complete',':99');store('client-resource-reload',{'after':after,'online':q.cmd('list')},{'stillConnected':'QAPlayer' in q.cmd('list'),'completeStillActive':after['settled']['active'] and after['settled']['elevated'],'sameDecadeModel':after['settled']['form']=='decade'})
print('NATIVE_LIFECYCLE',len(results),flush=True)
