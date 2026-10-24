#!/usr/bin/env python3
"""Sequential native A/B runner. Disposable runtime only; no builds during measured phases."""
from pathlib import Path
import os,sys,json,time,subprocess,shutil,hashlib
runtime=Path(os.environ['FANTASY_RUNTIME']).resolve()
fixture=Path(os.environ['FANTASY_FIXTURE']).resolve()
evidence=Path(os.environ['FANTASY_EVIDENCE']).resolve();evidence.mkdir(parents=True,exist_ok=True)
launch=json.loads(Path(os.environ['FANTASY_LAUNCH_JSON']).read_text())
baseline=Path(os.environ['FANTASY_BASELINE_JAR']).resolve();candidate=Path(os.environ['FANTASY_CANDIDATE_JAR']).resolve()
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from run_runtime_qa import command as rcon
props=dict(line.split('=',1) for line in (runtime/'server.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
def cmd(c):return rcon('127.0.0.1',int(props.get('rcon.port','25575')),props['rcon.password'],c)
def alive(pid):
 try:return Path(f'/proc/{pid}/stat').read_text().split(') ')[1].split()[0]!='Z'
 except FileNotFoundError:return False
def stop(pid=None):
 try:cmd('stop')
 except Exception:pass
 for _ in range(60):
  if pid and not alive(pid):return
  try:cmd('list')
  except Exception:
   if not pid:return
  time.sleep(1)
 raise RuntimeError('QA server did not stop cleanly; fixture not touched')
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert runtime!=fixture and (fixture/'config').is_dir() and (fixture/props.get('level-name','world')).is_dir()
# This exact runtime must already be marked as a QA fixture by its launch contract.
assert 'runtime' in runtime.name and os.environ.get('SVFRAME_RUNTIME_QA')=='1'
stop()
for phase in sys.argv[1:]:
 path=evidence/phase
 if path.exists():raise RuntimeError(f'Refusing to overwrite evidence {path}')
 path.mkdir()
 for name in ['config',props.get('level-name','world')]:
  shutil.rmtree(runtime/name,ignore_errors=True);shutil.copytree(fixture/name,runtime/name)
 for old in (runtime/'mods').glob('FantasyHubBridge*.jar'):old.unlink()
 jar=baseline if phase.startswith('baseline') else candidate
 target=runtime/'mods'/('FantasyHubBridge-'+('0.3.0' if phase.startswith('baseline') else '0.3.1')+'.jar');shutil.copy2(jar,target)
 integration=os.environ.get('FANTASY_BASELINE_INTEGRATION' if phase.startswith('baseline') else 'FANTASY_CANDIDATE_INTEGRATION')
 if integration:
  for old in (runtime/'mods').glob('SVFrameMMO-Cobblemon-Integration-*.jar'):old.unlink()
  src=Path(integration);shutil.copy2(src,runtime/'mods'/src.name)
 args=['-Xmx4G' if a.startswith('-Xmx') else a for a in launch]
 args.insert(1,f'-XX:StartFlightRecording=filename={path}/runtime.jfr,settings=profile,dumponexit=true,maxsize=256m')
 proc=subprocess.Popen(args,cwd=runtime,env=dict(os.environ,SVFRAME_RUNTIME_QA='1'),stdout=(path/'server.log').open('w'),stderr=subprocess.STDOUT,start_new_session=True)
 (path/'server.pid').write_text(str(proc.pid))
 (path/'environment.json').write_text(json.dumps({'jar':jar.name,'sha256':digest(jar),'jvm':args[:5],'serverPropertiesSha256':digest(runtime/'server.properties'),'mods':{p.name:digest(p) for p in sorted((runtime/'mods').glob('*.jar'))}},indent=2))
 deadline=time.monotonic()+180
 while True:
  if proc.poll() is not None:raise RuntimeError(f'{phase} server exited: {proc.returncode}')
  try:
   if 'There are ' in cmd('list'):break
  except Exception:pass
  if time.monotonic()>deadline:raise RuntimeError(f'{phase} startup timed out')
  time.sleep(1)
 result=subprocess.run([sys.executable,str(Path(__file__).with_name('run_matrix.py')),phase],env=os.environ,stdout=(path/'controller.log').open('w'),stderr=subprocess.STDOUT)
 stop(proc.pid);proc.wait(timeout=10)
 if result.returncode:raise RuntimeError(f'{phase} matrix failed; inspect controller.log')
 print(phase,'completed',flush=True)
