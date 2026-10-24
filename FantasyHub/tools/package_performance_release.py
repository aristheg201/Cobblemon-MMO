#!/usr/bin/env python3
"""Package the source-built paired candidate and explicitly curated native QA evidence."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,zipfile,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--qa',type=Path,required=True);args=p.parse_args()
root=Path(__file__).resolve().parents[2];out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
version='0.3.0';prefix='FantasyHubPerformance-0.3.0-CANDIDATE'
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
assert not subprocess.check_output(['git','status','--porcelain'],cwd=root,text=True).strip(),'Commit source before packaging'
for module in ['core','client','worldboss','skript']:
 for f in (root/'FantasyHub'/module/'build/test-results/test').glob('TEST-*.xml'):
  row=ET.parse(f).getroot();assert int(row.attrib.get('failures',0))==int(row.attrib.get('errors',0))==0,f
for src,dest in [(root/'FantasyHub/core/build/libs/FantasyHubCore-0.3.0.jar',out/'server/FantasyHubBridge-0.3.0.jar'),(root/'FantasyHub/client/build/libs/FantasyHubClient-0.3.0.jar',out/'client/FantasyHubClient-0.3.0.jar')]:
 dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dest)
 for runtime in [Path('/workspace/runtime-master-server/mods/FantasyHubBridge-0.3.0.jar') if 'core' in str(src) else Path('/workspace/runtime-master-client/mods/FantasyHubClient-0.3.0.jar')]:
  if runtime.exists():assert runtime.read_bytes()==dest.read_bytes(),'Runtime JAR differs from delivery'
shutil.copytree(root/'docs/perf-030',out/'docs',dirs_exist_ok=True)
ref=out/'config-reference';ref.mkdir(exist_ok=True);shutil.copy2(root/'FantasyHub/core/src/main/resources/default/commerce.json',ref/'commerce.json')
(ref/'sync.json').write_text(json.dumps(dict(workers=2,queueCapacity=64,maxCapturesPerTick=16,maxGuiCapturesPerTick=2,mainBudgetMicros=2000,vitalsIntervalTicks=5,combatIntervalTicks=5,extensionIntervalTicks=5,balanceRefreshTicks=20),indent=2)+'\n')
sourcezip=out/(prefix+'-source.zip')
modules={n:root/n for n in ['FantasyHub','SVFrameLib','SVFrameMMO','SVFrameItems','SVFrameMobs','SVFrameMMOCobblemon']}
for name,base in [('SVFrameItems','items'),('SVFrameMobs','mobs')]:
 if not modules[name].is_dir():modules[name]=root.parent/base/name
excluded={'.git','.gradle','build','dist','validated','__pycache__','.idea','node_modules'}
with zipfile.ZipFile(sourcezip,'w',zipfile.ZIP_DEFLATED,6) as z:
 for module,directory in modules.items():
  for f in directory.rglob('*'):
   if not f.is_file():continue
   rel=f.relative_to(directory)
   if any(part in excluded for part in rel.parts):continue
   allowed={'src','tools','gradle'}|({'core','client','protocol','worldboss','skript','qa'} if module=='FantasyHub' else set())
   if rel.parts[0] not in allowed and len(rel.parts)>1:continue
   if f.name in {'.env','server.properties','ops.json','usercache.json','velocity.toml'} or f.suffix in {'.log','.pyc','.class'}:continue
   z.write(f,Path('source')/module/rel)
 for f in (root/'docs/perf-030').glob('*'):z.write(f,Path('source/docs/perf-030')/f.name)
 z.writestr('source/SOURCE-COMMIT.txt',commit+'\nBaseline: 9dd9b12\n')
patch=out/'source-commits.patch';patch.write_bytes(subprocess.check_output(['git','format-patch','--stdout','9dd9b12..HEAD'],cwd=root))
qazip=out/(prefix+'-QA.zip')
with zipfile.ZipFile(qazip,'w',zipfile.ZIP_DEFLATED,6) as z:
 for f in args.qa.rglob('*'):
  if f.is_file():z.write(f,Path('qa')/f.relative_to(args.qa))
 for module in ['core','client','worldboss','skript']:
  for f in (root/'FantasyHub'/module/'build/test-results/test').glob('TEST-*.xml'):z.write(f,Path('automated')/module/f.name)
def digest(f):return hashlib.sha256(f.read_bytes()).hexdigest()
manifest={'release':prefix,'status':'CANDIDATE — complete production acceptance NOT met','sourceCommit':commit,'baselineCommit':'9dd9b12','minecraft':'1.21.1','java':21,'loader':'>=0.18.4','cobblemon':'1.8.2','protocol':4,'protocolDistribution':'identical embedded 0.3.0 in Bridge and Client; no standalone install','assetRevision':'heroes-commerce-v10','dependencyChanges':[],'importantLimitations':['20-player whole-server P95 remains 66.19ms > 50ms target','isolated large tick spikes remain','headless native-server load is not 20 human graphical clients','Tough As Nails temperature fill dominates remaining sampled main-thread CPU','run fantasyshop reload after native class definition/name/icon changes'],'artifacts':{str(f.relative_to(out)):{'sha256':digest(f),'bytes':f.stat().st_size} for f in sorted(out.rglob('*')) if f.is_file() and f.name not in {'manifest.json','SHA256SUMS',prefix+'-release.zip'}}}
(out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
release=out/(prefix+'-release.zip')
with zipfile.ZipFile(release,'w',zipfile.ZIP_DEFLATED,6) as z:
 for f in sorted(out.rglob('*')):
  if f.is_file() and f.suffix!='.zip' and f.name!='SHA256SUMS':z.write(f,f.relative_to(out))
(out/'SHA256SUMS').write_text(''.join(digest(f)+'  '+str(f.relative_to(out))+'\n' for f in sorted(out.rglob('*')) if f.is_file() and f.name!='SHA256SUMS'))
print(json.dumps({str(f.relative_to(out)):{'bytes':f.stat().st_size,'sha256':digest(f)} for f in [out/'server/FantasyHubBridge-0.3.0.jar',out/'client/FantasyHubClient-0.3.0.jar',release,sourcezip,qazip]},indent=2))
