"""Run against the opt-in local dedicated server + two real clients.

Screenshots are unmodified X11 Minecraft frames. This is equipment/renderer QA,
not a claim that the new classes or their abilities have been implemented.
"""
from pathlib import Path
import sys,os,subprocess,time,json,datetime
sys.path.insert(0,'/workspace/scratch')
from expanded_qa import command
out=Path('/workspace/integration/docs/qa/rider-models/runtime');out.mkdir(parents=True,exist_ok=True)
records=json.loads((out/'commands.json').read_text()) if (out/'commands.json').exists() else []
def cmd(value):
 result=command(value);records.append({'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'command':value,'result':result});return result
def photo(name):
 time.sleep(1.35)
 subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','x11grab','-video_size','1280x720','-i',':101','-frames:v','1','-update','1',str(out/(name+'.png'))],check=True)
 (out/'commands.json').write_text(json.dumps(records,indent=2,ensure_ascii=False)+'\n')
forms=sys.argv[1:] or ['zolda','agito_ground','agito_storm','agito_flame','agito_trinity','agito_shining']
for form in forms:
 result=cmd('execute as QAPlayer run fantasyhubqa rider_gear_'+form)
 if result:raise RuntimeError('Native QA equipment command failed: '+result)
 for angle,pose in {'front':'201.25 65.2 2.5 159.44 14.5','rear':'199.75 65.2 -1.5 -20.56 14.5','left':'202.5 65.2 0.5 90 14.5','right':'198.5 65.2 0.5 -90 14.5'}.items():
  cmd('tp QAAttacker '+pose);photo(form+'-'+angle)
 native=Path('/workspace/runtime-server/qa/rider-models')/(form+'.json')
 (out/(form+'.json')).write_bytes(native.read_bytes());print('Captured real equipped',form,'front/rear/both sides',flush=True)
