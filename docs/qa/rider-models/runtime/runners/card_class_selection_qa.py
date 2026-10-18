from pathlib import Path
import sys,json,time,subprocess,os,re
sys.path.insert(0,'/workspace/scratch');import card_master_skill_qa as q
q.OUT=Path('/workspace/integration/docs/qa/rider-models/runtime/class-selection');q.OUT.mkdir(exist_ok=True)
env={**os.environ,'DISPLAY':':99','LD_LIBRARY_PATH':'/workspace/tools/x11/usr/lib/x86_64-linux-gnu'}
def click(x,y):subprocess.run(['/workspace/tools/x11/usr/bin/xdotool','mousemove',str(x),str(y),'click','1'],env=env,check=True);time.sleep(.55)
def key(k):subprocess.run(['/workspace/tools/x11/usr/bin/xdotool','key',k],env=env,check=True);time.sleep(.4)
def parse(raw):return json.loads(raw[raw.index('{'):])
def state():return parse(q.cmd('fantasyhub inspect QAPlayer'))
def economy(scenario='inspect'):return parse(q.cmd('execute as QAPlayer run fantasyhubeconomyqa '+scenario))
key('Escape');results=[];s=state();rows=[x for x in s['classRoster'] if x['id'] in ['RYUKI','KNIGHT','DECADE']];assert all(x['selectable'] and x['unlocked'] for x in rows);(q.OUT/'after-migration-roster.json').write_text(json.dumps(rows,indent=2)+'\n')
for rider in ['RYUKI','KNIGHT','DECADE']:
 before=economy('reset_cooldown');s=state();rows=sorted(s['classRoster'],key=lambda x:re.sub('[§&][0-9A-FK-ORa-fk-or]','',x['name']).lower());current=next(i for i,x in enumerate(rows) if x['id']==s['classId']);target=next(i for i,x in enumerate(rows) if x['id']==rider)
 q.cmd('execute as QAPlayer run fantasyhub classes');time.sleep(.9)
 delta=target//5-current//5
 for i in range(abs(delta)):click(180 if delta>0 else 96,570)
 click(238,168+(target%5)*66);q.snap(rider.lower()+'-menu-selected',':99');click(840,570);q.snap(rider.lower()+'-menu-confirm',':99');click(840,570)
 deadline=time.monotonic()+12
 while time.monotonic()<deadline:
  after=state()
  if after['classId']==rider and not after['classSwitch']['busy']:break
  time.sleep(.3)
 else:raise RuntimeError('Real GUI class selection did not commit '+rider)
 balance=economy();checks={'guiClassChanged':after['classId']==rider,'chargedExactlyConfiguredFee':float(before['beastcoin'])-float(balance['beastcoin'])==3000,'otherCurrenciesPreserved':before['huntercoin']==balance['huntercoin'] and before['cobbledollars']==balance['cobbledollars'],'transactionCommitted':not after['classSwitch']['busy']}
 (q.OUT/(rider.lower()+'-real-menu-transaction.json')).write_text(json.dumps({'before':before,'after':balance,'profile':after,'checks':checks},indent=2)+'\n');results.append({'rider':rider,**checks});(q.OUT/'results.json').write_text(json.dumps(results,indent=2)+'\n');print(rider,checks,flush=True);assert all(checks.values());key('Escape');time.sleep(.8)
print('NATIVE_CLASS_SELECTION',len(results),flush=True)
