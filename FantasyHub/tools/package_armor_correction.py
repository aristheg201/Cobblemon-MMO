#!/usr/bin/env python3
"""Package a committed local armor correction and private reproducible source inputs.

Keeps historical QA in its original delivery. Does not invoke CI or change sharing.
"""
from pathlib import Path
import argparse,hashlib,json,subprocess,zipfile,shutil
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--build',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
p.add_argument('--items-source',type=Path,required=True);p.add_argument('--mobs-source',type=Path,required=True)
a=p.parse_args();repo=Path(__file__).resolve().parents[2];a.output.mkdir(parents=True,exist_ok=True)
build=json.loads((a.build/'build-manifest.json').read_text());commit=subprocess.check_output(['git','-C',str(repo),'rev-parse','HEAD'],text=True).strip()
if build['dirty'] or build['commit']!=commit:raise SystemExit('A clean matching local build is required')
for row in build['artifacts']:
 if hashlib.sha256((a.build/row['file']).read_bytes()).hexdigest()!=row['sha256']:raise SystemExit('Artifact mismatch: '+row['file'])
update=a.output/'Kamen-Riders-0.1.13-Armor-Update.zip';source=a.output/'Kamen-Riders-0.1.13-Source-Blockbench-Current-QA.zip'
with zipfile.ZipFile(update,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for row in build['artifacts']:
  if row['file'].startswith('FantasyHub'):z.write(a.build/row['file'],('client/' if 'Client' in row['file'] else 'server/')+row['file'])
 for rel in ['docs/KAMEN-RIDERS-0.1.13.md','docs/qa/armor-reference-v13/REVIEW.md']:
  z.write(repo/rel,Path(rel).name)
 z.write(a.build/'build-manifest.json','build-manifest.json')
 for path in (repo/'FantasyHub/core/src/main/resources/default/riders').rglob('*.yml'):z.write(path,'default-config-reference/'+str(path.relative_to(repo/'FantasyHub/core/src/main/resources/default/riders')))
 z.writestr('INSTALL.vi.txt','Tat server va game. SERVER: thay FantasyHubBridge cu bang server/FantasyHubBridge-0.1.13.jar. CLIENT: moi nguoi thay FantasyHubClient cu bang client/FantasyHubClient-0.1.13.jar. Khong de hai ban cung mod. Giu SVFrameLib/MMO va cac dependency dung ban0.1.12 da giao; hash trong build-manifest. Khong cai kem bundled FantasyHubCore. Giu world/data/config rieng.\nRyuki Survive tu doi giap khi bat skill, bam lai tro ve thuong. Decade Complete giu modelDecade. F bat/tat Henshin/skillbar. Asset da nam trongClient, khong can packRiderroi.\nBuild local Java21/Fabric/Minecraft1.21.1, khong GitHubActions. Doc bao cao ve anh Minecraft that va gioi han danh gia thammy.\n')
seen=set()
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 def add(path,name):
  if name not in seen:seen.add(name);z.write(path,name)
 for raw in subprocess.check_output(['git','-C',str(repo),'ls-files','-z']).split(b'\0'):
  if not raw:continue
  rel=raw.decode();path=repo/rel
  if rel.startswith('docs/qa/') and not rel.startswith('docs/qa/armor-reference-v13/'):continue
  if path.is_file():add(path,'source/integration/'+rel)
 for rel in ['FantasyHub/core/src/imported/resources','FantasyHub/client/src/imported/resources']:
  for path in (repo/rel).rglob('*'):
   if path.is_file():add(path,'source/integration/'+str(path.relative_to(repo)))
 for name,root in [('SVFrameItems',a.items_source),('SVFrameMobs',a.mobs_source)]:
  for path in root.rglob('*'):
   if path.is_file() and not set(path.relative_to(root).parts)&{'.git','.gradle','build','dist','validated','logs','run','__pycache__'}:add(path,'source/integration/'+name+'/'+str(path.relative_to(root)))
  jar=Path('/workspace/runtime-server/mods')/(name+'-0.1.2-fabric.jar');add(jar,'source/integration/'+name+'/build/libs/'+jar.name)
 for row in build['artifacts']:
  if row['file'].startswith('SVFrame'):
   name='SVFrameLib' if 'Lib-' in row['file'] else 'SVFrameMMO';add(a.build/row['file'],'source/integration/'+name+'/build/libs/'+row['file'])
 for path in a.build.rglob('*'):
  if path.is_file():add(path,'verified-local-build/'+str(path.relative_to(a.build)))
 z.writestr('SOURCE-README.txt','Private source bundle: complete tracked implementation,44 editableBlockbench projects, original pixel atlas, imported legacy inputs, unchangedItems/Mobs sources and required local dependencyJARs. Historical native QA remains in previous0.1.12 delivery; current0.1.13 native evidence is in docs/qa/armor-reference-v13. Do not publish private imported packs or user photographs. No credentials, worlds, build caches or repository metadata.\nBuild: source/integration/FantasyHub/tools/build_local.sh with JDK21 and Gradle8.8. Maven/Loom cache or network access required. Models are exported by actualBlockbench5.2.2; source scripts are included. No GitHubActions.\n')
for path in [update,source]:
 if path.stat().st_size>104857600:raise SystemExit('Split the source evidence before upload: '+str(path))
 with zipfile.ZipFile(path) as z:
  if z.testzip():raise SystemExit('ZIP verification failed')
manifest={'commit':commit,'branch':subprocess.check_output(['git','-C',str(repo),'branch','--show-current'],text=True).strip(),'build':'local','tests':sum(t['tests'] for t in build['tests']),'files':[{'file':f.name,'bytes':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in [update,source]],'artifacts':build['artifacts']}
(a.output/'delivery-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
shutil.copy2(repo/'docs/KAMEN-RIDERS-0.1.13.md',a.output/'KAMEN-RIDERS-0.1.13.md')
print(json.dumps(manifest,indent=2))
