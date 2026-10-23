#!/usr/bin/env python3
"""Package verified source, compatible modules, examples and actual QA evidence; never include player data."""
from pathlib import Path
import argparse,shutil,zipfile,hashlib,json,subprocess,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--evidence',type=Path,required=True);a=p.parse_args();root=Path(__file__).resolve().parents[2];out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
modules={n:root/n for n in ['SVFrameLib','SVFrameMMO','SVFrameItems','SVFrameMobs','FantasyHub','SVFrameMMOCobblemon']}
for n,base in [('SVFrameItems','items'),('SVFrameMobs','mobs')]:
 if not modules[n].is_dir():modules[n]=root.parent/base/n
jars=[(modules['SVFrameLib']/'build/libs/SVFrameLib-1.7.2-fabric.jar','server/mods/SVFrameLib-1.7.2-fabric.jar'),(modules['SVFrameMMO']/'build/libs/SVFrameMMO-1.13.4-fabric.jar','server/mods/SVFrameMMO-1.13.4-fabric.jar'),(modules['SVFrameItems']/'build/libs/SVFrameItems-0.1.2-fabric.jar','server/mods/SVFrameItems-0.1.2-fabric.jar'),(modules['SVFrameMobs']/'build/libs/SVFrameMobs-0.1.2-fabric.jar','server/mods/SVFrameMobs-0.1.2-fabric.jar'),(root/'FantasyHub/core/build/libs/FantasyHubCore-0.2.5.jar','server/mods/FantasyHubBridge-0.2.5.jar'),(root/'FantasyHub/worldboss/build/libs/SVFrameWorldBoss-0.2.0.jar','server/mods/SVFrameWorldBoss-0.2.0.jar'),(root/'FantasyHub/skript/build/libs/SVFrameSkript-0.1.0.jar','server/mods/SVFrameSkript-0.1.0.jar'),(root/'SVFrameMMOCobblemon/build/libs/SVFrameMMO-Cobblemon-Integration-0.1.20.jar','server/mods/SVFrameMMO-Cobblemon-Integration-0.1.20.jar'),(root/'FantasyHub/client/build/libs/FantasyHubClient-0.2.5.jar','client/mods/FantasyHubClient-0.2.5.jar')]
for source,name in jars:
 target=out/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
for name in ['entity_texture_features-7.2.6-1.21-fabric.jar','entity_model_features-3.3.12-1.21-fabric.jar']:
 src=root.parent/'runtime-master-client/mods'/name
 if src.exists():shutil.copy2(src,out/'client/mods'/name)
(out/'docs').mkdir(exist_ok=True)
for source in (root/'docs/release-master').glob('*'):
 if source.is_file():shutil.copy2(source,out/'docs'/source.name)
# Complete authored resources are required build inputs, including the source pack and imported client models.
source_zip=out/'SVFrame-master-0.2.5-source.zip'
with zipfile.ZipFile(source_zip,'w',zipfile.ZIP_DEFLATED,6) as z:
 for module,directory in modules.items():
  for f in directory.rglob('*'):
   if not f.is_file():continue
   rel=f.relative_to(directory)
   if any(n in {'.git','.gradle','build','dist','validated','__pycache__','.idea','node_modules'} for n in rel.parts):continue
   allowed={'src','tools','gradle'}|({'core','client','protocol','worldboss','skript'} if module=='FantasyHub' else set())
   if rel.parts[0] not in allowed and len(rel.parts)>1:continue
   if f.name in {'.env','server.properties','ops.json','usercache.json','velocity.toml'}:continue
   if f.suffix in {'.log','.pyc'}:continue
   z.write(f,Path('source')/module/rel)
 for f in (root/'docs/release-master').glob('*'):
  if f.is_file():z.write(f,Path('source/docs/release-master')/f.name)
 for workflow in (root/'.github/workflows').glob('*'):
  if workflow.is_file():z.write(workflow,Path('source/.github/workflows')/workflow.name)
# Logs/screenshots/videos remain separate so installation downloads stay small.
qa_zip=out/'SVFrame-master-0.2.5-QA.zip'
with zipfile.ZipFile(qa_zip,'w',zipfile.ZIP_DEFLATED,6) as z:
 for f in a.evidence.rglob('*'):
  if f.is_file() and f.suffix not in {'.pid'}:z.write(f,Path('qa')/f.relative_to(a.evidence))
 for d in modules.values():
  for f in d.glob('**/build/test-results/test/TEST-*.xml'):z.write(f,Path('automated')/d.name/f.relative_to(d))
 for f in (root/'release-build-logs').glob('*.log'):z.write(f,Path('build-logs')/f.name)
records=[]
for d in modules.values():
 rows=[ET.parse(p).getroot() for p in d.glob('**/build/test-results/test/TEST-*.xml')];records.append({'module':d.name,'tests':sum(int(x.get('tests',0)) for x in rows),'failures':sum(int(x.get('failures',0))+int(x.get('errors',0)) for x in rows)})
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
manifest={'release':'FantasyHub 0.2.5 / WorldBoss 0.2.0','minecraft':'1.21.1','java':21,'cobblemon':'1.8.2+1.21.1','sourceCommit':commit,'assetRevision':'heroes-commerce-v9','automated':records,'dependencies':{'Cobblemon':'https://cdn.modrinth.com/data/MdwFAVRL/versions/GJ2a1b8O/Cobblemon-fabric-1.8.2%2B1.21.1.jar','BEconomy':'https://modrinth.com/mod/4Kma4Oms/version/CpGrhJed'},'artifacts':{str(p.relative_to(out)):{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(out.rglob('*')) if p.is_file() and p.name not in {'manifest.json','SHA256SUMS','SVFrame-master-0.2.5-release.zip'}}}
(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');(out/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(out))+'\n' for p in sorted(out.rglob('*')) if p.is_file() and p.name not in {'SHA256SUMS','SVFrame-master-0.2.5-release.zip'}))
with zipfile.ZipFile(out/'SVFrame-master-0.2.5-release.zip','w',zipfile.ZIP_DEFLATED,6) as z:
 for f in sorted(out.rglob('*')):
  if f.is_file() and f.suffix!='.zip':z.write(f,f.relative_to(out))
print(json.dumps({f.name:{'bytes':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in out.glob('*.zip')},indent=2))
