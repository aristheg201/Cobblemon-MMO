#!/usr/bin/env python3
"""Internal native adaptation. Reads textures/mesh data only; never installs or nests a Forge JAR."""
import argparse, hashlib, json, zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--fisk',type=Path,required=True);p.add_argument('--jmct',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1];out=root/'client/src/imported/resources/assets/fantasyhub';manifest=[]
fisk=zipfile.ZipFile(a.fisk);jmct=zipfile.ZipFile(a.jmct)
def copy(z,source,target):
 data=z.read(source)
 if len(data)>8_000_000:raise ValueError(source)
 path=out/target;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
 manifest.append({'sourceArchive':Path(z.filename).name,'source':source,'target':'assets/fantasyhub/'+target,'sha256':hashlib.sha256(data).hexdigest()})
heroes={'spider_man':('spider_man','layer1','layer2','layer1'), 'sorcerer_supreme':('doctor_strange','layer1','pants_robes','boots'),'iron_man':('iron_man','layer1','layer2','layer1'),'flash':('the_flash','layer1','layer2','layer1'),'batman':('batman_dceu','layer1','layer2','layer1')}
for hero,(source,chest,legs,boots) in heroes.items():
 for slot,suffix in [('chest',chest),('legs',legs),('feet',boots),('head','layer1')]:copy(fisk,f'assets/fiskheroes/textures/heroes/{source}_{suffix}.png',f'textures/armor/heroes/{hero}_{slot}.png')
 for i,part in enumerate(['helmet','chestplate','leggings','boots']):
  if hero=='sorcerer_supreme' and i==0:continue
  source_path=f'assets/fiskheroes/textures/items/heroes/{source}_{i}.png'
  copy(fisk,source_path,f'textures/item/heroes/hero_{hero}_{part}.png')
 for part in ['helmet','chestplate','leggings','boots']:
  if hero=='sorcerer_supreme' and part=='helmet':continue
  path=out/f'models/item/heroes/hero_{hero}_{part}.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'fantasyhub:item/heroes/hero_{hero}_{part}'}},indent=2)+'\n')
 if hero in ['sorcerer_supreme','batman']:copy(fisk,f'assets/fiskheroes/textures/heroes/{source}_cape.png',f'textures/armor/heroes/{hero}_cape.png')
for slot,suffix in [('chest','layer1'),('legs','layer2'),('feet','layer1')]:copy(jmct,f'assets/jmctheroes/textures/heroes/thor/thor_infinity_{suffix}.png',f'textures/armor/heroes/thor_{slot}.png')
# The Infinity variant is unmasked and capeless, following its actual renderer definition.
for i,part in [(1,'chestplate'),(2,'leggings'),(3,'boots')]:
 copy(jmct,f'assets/jmctheroes/textures/items/heroes/thor_infinity_{i}.png',f'textures/item/heroes/hero_thor_{part}.png')
 path=out/f'models/item/heroes/hero_thor_{part}.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'fantasyhub:item/heroes/hero_thor_{part}'}},indent=2)+'\n')
for slot in ['layer1','layer2']:copy(fisk,f'assets/fiskheroes/textures/heroes/iron_man_lights_{slot}.png',f'textures/armor/heroes/iron_man_lights_{slot}.png')
for name in ['cape_default','cape_batman']:
 copy(fisk,f'assets/fiskheroes/models/mesh/{name}.mesh.json',f'geo/heroes/{name}.json')
copy(fisk,'assets/fiskheroes/models/shapes/tao_mandala.json','geo/heroes/tao_mandala.json')
for name in ['mjolnir','stormbreaker']:
 import io
 table=zipfile.ZipFile(io.BytesIO(jmct.read(f'assets/jmctheroes/models/tabula/{name}.tbl')))
 data=json.loads(table.read('model.json'));path=out/f'geo/heroes/{name}.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n')
 copy(jmct,f'assets/jmctheroes/textures/heroes/thor/{name}.png',f'textures/item/heroes/{name}.png')
 manifest.append({'sourceArchive':a.jmct.name,'source':f'assets/jmctheroes/models/tabula/{name}.tbl!model.json','target':f'assets/fantasyhub/geo/heroes/{name}.json','author':data.get('authorName'),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
(out/'heroes-assets.json').write_text(json.dumps({'distribution':'INTERNAL_ONLY','binaryForgeRuntimeBundled':False,'sources':[{'project':'FiskHeroes 2.4.0','url':'https://modrinth.com/mod/fiskheroes'},{'project':"JMCT’s Heropack 3.1.0",'url':'https://www.curseforge.com/minecraft/customization/jmcts-heropack/files/4625355'}],'files':manifest},indent=2)+'\n')
models=sorted('fantasyhub:'+str(f.relative_to(out/'models')).replace('.json','') for f in (out/'models/item/heroes').glob('*.json'))
(out/'heroes-models.json').write_text(json.dumps(models,indent=2)+'\n')
print('Adapted',len(manifest),'verified source entries;',len(models),'native item models; no executable reference code or nested JARs.')
