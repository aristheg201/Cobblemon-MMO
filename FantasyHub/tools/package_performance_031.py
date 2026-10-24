#!/usr/bin/env python3
"""Package committed source, exact tested JARs and curated, credential-free QA evidence."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,zipfile,xml.etree.ElementTree as ET,io
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--qa',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[2];out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
version='0.3.1';prefix='FantasyHubPerformance-0.3.1-CANDIDATE';baseline='91e337b3ef022b5d7a2ddbfea8df1a4bab4951d6'
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
assert not subprocess.check_output(['git','status','--porcelain'],cwd=root,text=True).strip(),'Commit source before packaging'
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
for directory in [*(root/'FantasyHub'/m/'build/test-results/test' for m in ['core','client','worldboss','skript']),root/'SVFrameMMOCobblemon/build/test-results/test']:
 files=list(directory.glob('TEST-*.xml'));assert files,directory
 for file in files:
  row=ET.parse(file).getroot().attrib;assert int(row.get('failures',0))==int(row.get('errors',0))==0,file
artifacts=[(root/'FantasyHub/core/build/libs/FantasyHubCore-0.3.1.jar','server/FantasyHubBridge-0.3.1.jar',Path('/workspace/runtime-master-server/mods/FantasyHubBridge-0.3.1.jar')),(root/'FantasyHub/client/build/libs/FantasyHubClient-0.3.1.jar','client/FantasyHubClient-0.3.1.jar',Path('/workspace/runtime-master-client/mods/FantasyHubClient-0.3.1.jar')),(root/'SVFrameMMOCobblemon/build/libs/SVFrameMMO-Cobblemon-Integration-0.1.21.jar','server/SVFrameMMO-Cobblemon-Integration-0.1.21.jar',Path('/workspace/runtime-master-server/mods/SVFrameMMO-Cobblemon-Integration-0.1.21.jar'))]
for src,name,runtime in artifacts:
 assert src.exists() and runtime.exists() and digest(src)==digest(runtime),(src,'not the tested runtime artifact')
 target=out/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,target)
embedded=[]
for src,_,_ in artifacts[:2]:
 with zipfile.ZipFile(src) as z:
  names=[n for n in z.namelist() if '/jars/' in n and 'FantasyHubProtocol' in n];assert len(names)==1
  raw=z.read(names[0]);embedded.append(hashlib.sha256(raw).hexdigest())
  with zipfile.ZipFile(io.BytesIO(raw)) as nested:assert json.loads(nested.read('fabric.mod.json'))['version']==version
assert embedded[0]==embedded[1],'Mismatched protocol components'
shutil.copytree(root/'docs/perf-031',out/'docs',dirs_exist_ok=True)
config=out/'config-reference';config.mkdir(exist_ok=True)
(config/'sync.json').write_text(json.dumps(dict(workers=2,queueCapacity=64,maxCapturesPerTick=16,maxGuiCapturesPerTick=2,mainBudgetMicros=1500,catalogPageSize=8,maxPacketsPerTick=32,maxDispatchBytesPerTick=131072,vitalsIntervalTicks=5,combatIntervalTicks=5,extensionIntervalTicks=5,balanceRefreshTicks=20),indent=2)+'\n')
sourcezip=out/(prefix+'-source.zip');qazip=out/(prefix+'-QA.zip')
modules={name:root/name for name in ['FantasyHub','SVFrameLib','SVFrameMMO','SVFrameItems','SVFrameMobs','SVFrameMMOCobblemon']}
for name,base in [('SVFrameItems','items'),('SVFrameMobs','mobs')]:
 if not modules[name].is_dir():modules[name]=root.parent/base/name
excluded={'.git','.gradle','build','dist','validated','__pycache__','.idea','node_modules'}
with zipfile.ZipFile(sourcezip,'w',zipfile.ZIP_DEFLATED,6) as z:
 for module,directory in modules.items():
  assert directory.is_dir(),directory
  for file in sorted(directory.rglob('*')):
   if not file.is_file():continue
   rel=file.relative_to(directory)
   if any(part in excluded for part in rel.parts):continue
   allowed={'src','tools','gradle'}|({'core','client','protocol','worldboss','skript','qa'} if module=='FantasyHub' else set())
   if rel.parts[0] not in allowed and len(rel.parts)>1:continue
   if file.name in {'.env','server.properties','ops.json','usercache.json','velocity.toml'} or file.suffix in {'.log','.pyc','.class'}:continue
   z.write(file,Path('source')/module/rel)
 for file in (root/'docs/perf-031').glob('*'):z.write(file,Path('source/docs/perf-031')/file.name)
 for file in [root/'.gitattributes',root/'.github/workflows/fantasyhub-performance.yml',root/'.github/workflows/package-three-jars.yml']:
  if file.exists():z.write(file,Path('source')/file.relative_to(root))
 z.writestr('source/SOURCE-COMMIT.txt',f'{commit}\nBaseline: {baseline}\nCode, assets and QA helpers are source-built; historical binary hotfixes are not inputs.\n')
with zipfile.ZipFile(qazip,'w',zipfile.ZIP_DEFLATED,6) as z:
 for file in sorted(a.qa.rglob('*')):
  if file.is_file():z.write(file,Path('qa')/file.relative_to(a.qa))
 for module,directory in [(m,root/'FantasyHub'/m) for m in ['core','client','worldboss','skript']]+[('integration',root/'SVFrameMMOCobblemon')]:
  for file in (directory/'build/test-results/test').glob('TEST-*.xml'):z.write(file,Path('automated')/module/file.name)
manifest={'release':prefix,'status':'CANDIDATE: measured performance and tested scope are documented; not blanket production certification','sourceCommit':commit,'baselineCommit':baseline,'minecraft':'1.21.1','java':21,'loader':'>=0.18.4','cobblemon':'1.8.2','protocol':5,'embeddedProtocolSha256':embedded[0],'dependencyChanges':['SVFrameMMO Cobblemon Integration 0.1.20 -> 0.1.21: bounded presentation catalog API'],'assetRevision':'heroes-commerce-v10','ci':'https://github.com/aristheg201/Cobblemon-MMO/actions/runs/40079579140','artifacts':{str(file.relative_to(out)):{'sha256':digest(file),'bytes':file.stat().st_size} for file in sorted(out.rglob('*')) if file.is_file() and file.name not in {'manifest.json','SHA256SUMS',prefix+'-release.zip'}}}
(out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
release=out/(prefix+'-release.zip')
with zipfile.ZipFile(release,'w',zipfile.ZIP_DEFLATED,6) as z:
 for file in sorted(out.rglob('*')):
  if file.is_file() and file.suffix!='.zip' and file.name!='SHA256SUMS':z.write(file,file.relative_to(out))
(out/'SHA256SUMS').write_text(''.join(digest(file)+'  '+str(file.relative_to(out))+'\n' for file in sorted(out.rglob('*')) if file.is_file() and file.name!='SHA256SUMS'))
for file in [*(out/name for _,name,_ in artifacts),release,sourcezip,qazip]:print(file.name,file.stat().st_size,digest(file))
