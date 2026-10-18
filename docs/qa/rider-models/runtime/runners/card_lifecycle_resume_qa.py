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
results=[r for r in json.loads((q.OUT/'results.json').read_text()) if r['scenario']!='death-respawn-no-leak']
r=json.loads((q.OUT/'death-respawn-no-leak.json').read_text());before=r['before'];afterstate=r['profile'];after=r['after'];speed=r['attackSpeed'];base=r['baseAttackSpeed']
q.key_f();time.sleep(.5);reactivated=status();activeprofile=state()
store('death-respawn-no-leak',{'before':before,'after':after,'profile':afterstate,'reactivated':reactivated,'activeProfile':activeprofile,'attackSpeed':speed,'baseAttackSpeed':base},{'humanAfterDeath':not after['settled']['active'],'sameClass':afterstate['classId']=='KNIGHT','levelPreserved':afterstate['level']==before['level'],'loadoutPreserved':afterstate['loadout']==before['loadout'],'baselineSpeed':abs(speed-base)<1e-6,'cleanActors':after['settled']['summon']=='' and after['settled']['projectiles']==0,'realFRestoresSevenSlots':len(activeprofile['casting']['slots'])==7 and reactivated['settled']['active']})
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
