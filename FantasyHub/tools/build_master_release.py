#!/usr/bin/env python3
"""Build the complete source bundle with Java 21 and the checked-in Gradle wrappers."""
from pathlib import Path
import argparse,os,subprocess,json,hashlib
p=argparse.ArgumentParser();p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);p.add_argument('--offline',action='store_true');a=p.parse_args();root=a.root.resolve()
java=Path(os.environ['JAVA_HOME'])/'bin/java' if 'JAVA_HOME' in os.environ else 'java'
version=subprocess.run([str(java),'-version'],capture_output=True,text=True,check=True)
if 'version "21' not in version.stderr:raise SystemExit('Set JAVA_HOME to Java 21 before building.')
steps=[('SVFrameLib','FantasyHub',['build']),('SVFrameMMO','FantasyHub',['build','nativeCoreSmoke']),('SVFrameItems','FantasyHub',['build','nativeCoreSmoke']),('SVFrameMobs','SVFrameMobs',['build']),('FantasyHub','FantasyHub',['build']),('SVFrameMMOCobblemon','SVFrameMMOCobblemon',['build','nativeIntegrationSmoke'])]
logs=root/'release-build-logs';logs.mkdir(exist_ok=True)
for module,wrapper,tasks in steps:
 directory=root/module
 if not directory.is_dir() and module in ['SVFrameItems','SVFrameMobs']:directory=root.parent/('items' if module=='SVFrameItems' else 'mobs')/module
 wrapper_dir=directory if wrapper==module else root/wrapper
 cmd=['bash',str(wrapper_dir/'gradlew'),'-p',str(directory),'--no-daemon','--max-workers=2']
 if a.offline:cmd.append('--offline')
 cmd+=tasks
 print('Building',module,flush=True)
 try:
  with (logs/(module+'.log')).open('w') as f:subprocess.run(cmd,cwd=root,stdout=f,stderr=subprocess.STDOUT,check=True)
 except subprocess.CalledProcessError:
  print('\n'.join((logs/(module+'.log')).read_text().splitlines()[-80:]),flush=True)
  raise
print('Complete build passed. JARs are in each module build/libs; no ShadowCrates dependency.',flush=True)
