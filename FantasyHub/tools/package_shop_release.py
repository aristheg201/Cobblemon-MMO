#!/usr/bin/env python3
"""Package the scoped, source-built shop update and supplied native QA evidence."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,zipfile
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--qa',type=Path,required=True);p.add_argument('--version',default='0.2.6');p.add_argument('--name',default='FantasyShop');p.add_argument('--baseline',default='ec47d750b9368f03710381dda3b5d284a7b252f7');a=p.parse_args();prefix=f'{a.name}-{a.version}'
root=Path(__file__).resolve().parents[2];out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
for source,target in [(f'FantasyHub/client/build/libs/FantasyHubClient-{a.version}.jar',f'client/FantasyHubClient-{a.version}.jar'),(f'FantasyHub/core/build/libs/FantasyHubCore-{a.version}.jar',f'server/FantasyHubBridge-{a.version}.jar'),('FantasyHub/core/src/main/resources/default/commerce.json','config-reference/commerce.json')]:
 dest=out/target;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(root/source,dest)
for locale in ['en_us','vi_vn']:
 dest=out/'language-assets/assets/fantasyhub/lang'/f'{locale}.json';dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(root/'FantasyHub/client/src/main/resources/assets/fantasyhub/lang'/f'{locale}.json',dest)
shutil.copytree(root/('docs/shop-'+a.version.replace('.','')),out/'docs',dirs_exist_ok=True)
modules={n:root/n for n in ['FantasyHub','SVFrameLib','SVFrameMMO','SVFrameItems','SVFrameMobs','SVFrameMMOCobblemon']}
for n,base in [('SVFrameItems','items'),('SVFrameMobs','mobs')]:
 if not modules[n].is_dir():modules[n]=root.parent/base/n
sourcezip=out/f'{prefix}-source.zip'
with zipfile.ZipFile(sourcezip,'w',zipfile.ZIP_DEFLATED,6) as z:
 for module,directory in modules.items():
  for f in directory.rglob('*'):
   if not f.is_file():continue
   rel=f.relative_to(directory)
   if any(n in {'.git','.gradle','build','dist','validated','__pycache__','.idea','node_modules'} for n in rel.parts):continue
   allowed={'src','tools','gradle'}|({'core','client','protocol','worldboss','skript'} if module=='FantasyHub' else set())
   if rel.parts[0] not in allowed and len(rel.parts)>1:continue
   if f.name in {'.env','server.properties','ops.json','usercache.json','velocity.toml'} or f.suffix in {'.log','.pyc'}:continue
   z.write(f,Path('source')/module/rel)
 for f in (root/('docs/shop-'+a.version.replace('.',''))).glob('*'):z.write(f,Path('source/docs')/('shop-'+a.version.replace('.',''))/f.name)
 for f in (root/'.github/workflows').glob('*'):
  if f.is_file():z.write(f,Path('source/.github/workflows')/f.name)
qa=out/f'{prefix}-QA.zip'
with zipfile.ZipFile(qa,'w',zipfile.ZIP_DEFLATED,6) as z:
 for f in a.qa.rglob('*'):
  if f.is_file() and f.suffix!='.pid' and f.name not in {'native-state.json','initial.png','options-navigation.png','class-initial-vi.png','class-initial-vi.json'}:z.write(f,Path('qa')/f.relative_to(a.qa))
 for module in ['client','core','worldboss','skript']:
  for f in (root/'FantasyHub'/module/'build/test-results/test').glob('TEST-*.xml'):z.write(f,Path('automated')/module/f.name)
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
def digest(f):return hashlib.sha256(f.read_bytes()).hexdigest()
manifest={'release':f'{a.name} {a.version}','sourceCommit':commit,'baselineCommit':a.baseline,'minecraft':'1.21.1','java':21,'cobblemon':'1.8.2','assetRevision':'heroes-commerce-v10','scope':['FantasyHubClient','FantasyHubBridge','bundled protocol'],'artifacts':{str(f.relative_to(out)):{'sha256':digest(f),'bytes':f.stat().st_size} for f in sorted(out.rglob('*')) if f.is_file() and f.name not in {'manifest.json','SHA256SUMS',f'{prefix}-release.zip'}}}
(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
with zipfile.ZipFile(out/f'{prefix}-release.zip','w',zipfile.ZIP_DEFLATED,6) as z:
 for f in sorted(out.rglob('*')):
  if f.is_file() and f.suffix!='.zip' and f.name!='SHA256SUMS':z.write(f,f.relative_to(out))
(out/'SHA256SUMS').write_text(''.join(digest(f)+'  '+str(f.relative_to(out))+'\n' for f in sorted(out.rglob('*')) if f.is_file() and f.name!='SHA256SUMS'))
print(json.dumps({f.name:{'sha256':digest(f),'bytes':f.stat().st_size} for f in out.glob('*.zip')},indent=2))
