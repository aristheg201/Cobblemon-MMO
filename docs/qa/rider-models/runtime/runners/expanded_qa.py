import importlib.util,json,sys,datetime
from pathlib import Path
spec=importlib.util.spec_from_file_location('qa','/workspace/integration/FantasyHub/tools/run_runtime_qa.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
p=dict(l.split('=',1) for l in Path('/workspace/runtime-server/server.properties').read_text().splitlines() if '=' in l and not l.startswith('#'))
def command(cmd):return m.command('127.0.0.1',int(p.get('rcon.port','25575')),p['rcon.password'],cmd)
def snapshot():
 s=command('fantasyhub inspect QAPlayer');d=json.loads(s[s.index('{'):]);return {k:d[k] for k in ['classId','level','skillPoints','skills','loadout','casting'] if k in d}
if __name__=='__main__':
 for cmd in sys.argv[1:]:
  if cmd.startswith('snapshot:'):
   result=snapshot();kind=cmd.partition(':')[2]
  else:result=command(cmd);kind=cmd
  record={'time':datetime.datetime.now(datetime.timezone.utc).isoformat(),'action':kind,'result':result}
  with Path('/workspace/qa-evidence/expanded-skills/runtime-commands.jsonl').open('a') as f:f.write(json.dumps(record,ensure_ascii=False)+'\n')
  print(kind,'=>',json.dumps(result,ensure_ascii=False)[:1600])
