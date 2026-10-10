#!/usr/bin/env python3
"""Build local presentation assets from the user's audited source folders; no downloads."""
import argparse, pathlib, json, zipfile, shutil, subprocess, tempfile, sys, hashlib
p=argparse.ArgumentParser();p.add_argument('--references',type=pathlib.Path,required=True);p.add_argument('--client-output',type=pathlib.Path);p.add_argument('--server-config',type=pathlib.Path)
a=p.parse_args();hub=pathlib.Path(__file__).resolve().parents[1];out=a.client_output or hub/'client/src/imported/resources';converter=hub.parent/'SVFrameLib/tools/import_blockbench_visuals.py';blueprints=a.references/'config2/ModelEngine/blueprints'
# Framework translations are included directly from their maintained sources by
# Gradle. Remove previous generated copies rather than shadowing newer messages.
for namespace in ['svframelib','svframemmo','svframemmo_cobblemon']:
 shutil.rmtree(out/'assets'/namespace/'lang',ignore_errors=True)
names=['vfx_soul_blade','vfx_death_strike_1','vfx_death_wings_1','vfx_earthquake_rupture_1','death_knight']+[f'vfx_death_wings_{i}' for i in range(2,8)]+[f'vfx_earthquake_rupture_{i}' for i in range(2,6)]
with tempfile.TemporaryDirectory(prefix='fantasyhub-assets-') as temporary:
 stage=pathlib.Path(temporary)/'source';stage.mkdir();built=pathlib.Path(temporary)/'built';choices={}
 for name in names:
  candidate=blueprints/'Extras'/(name+'.bbmodel');source=candidate if candidate.exists() else blueprints/(name+'.bbmodel');shutil.copy2(source,stage/source.name);choices[name]=str(source.relative_to(a.references))
 command=[sys.executable,str(converter),str(stage),'--output',str(built)]
 for name in names:command+=['--model',name]
 subprocess.run(command,check=True)
 with zipfile.ZipFile(built/'SVFrameReferenceVisuals-1.21.1.zip') as archive:files={n:archive.read(n) for n in archive.namelist() if n.startswith('assets/')}
 models=json.loads((built/'reference-models.json').read_text());paper=json.loads(files['assets/minecraft/models/item/paper.json']);tinted=[]
 for override in paper['overrides']:
  if 'vfx_earthquake_rupture_' in override['model']:
   tinted.append(override);namespace,path=override['model'].split(':',1);key=f'assets/{namespace}/models/{path}.json';model=json.loads(files[key])
   for element in model['elements']:
    for face in element['faces'].values():face['tintindex']=0
   files[key]=json.dumps(model).encode()
 files['assets/fantasyhub/models/item/visual_bone.json']=files.pop('assets/minecraft/models/item/paper.json')
 for name,bones in models.items():
  for bone in bones:bone['item']='fantasyhub:visual_bone'
 for obsolete in ['assets/minecraft/models/item/paper.json','assets/minecraft/models/item/leather_horse_armor.json']:
  (out/obsolete).unlink(missing_ok=True)
 for name,content in files.items():
  target=out/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(content)
 sources=a.references/'config1/ItemsAdder/contents/death_knight/resourcepack/assets'
 for source in sources.rglob('*'):
  if source.is_file() and (source.name=='sounds.json' or source.suffix=='.ogg' or (source.suffix=='.png' and source.name.startswith('icon_'))):
   target=out/'assets'/source.relative_to(sources);target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
 serverResources=hub/'core/src/imported/resources/default/presentation';serverResources.mkdir(parents=True,exist_ok=True);(serverResources/'reference-models.json').write_text(json.dumps(models,separators=(',',':')));shutil.copy2(built/'reference-anchors.json',serverResources/'visual-anchors.json')
 for name,relative in {'death_knight':'config2/MMOCore/classes/death_knight.yml','anti_mage_knight':'amk/MMOCore/classes/anti_mage_knight.yml'}.items():
  source=a.references/relative;target=serverResources/'classes'/(name+'.yml');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target);choices['class:'+name]=relative
 if a.server_config:
  target=a.server_config/'visual-models';target.mkdir(parents=True,exist_ok=True);(target/'reference-models.json').write_text(json.dumps(models,separators=(',',':')));shutil.copy2(built/'reference-anchors.json',a.server_config/'visual-anchors.json')
 (out/'assets/fantasyhub/source-choices.json').parent.mkdir(parents=True,exist_ok=True);(out/'assets/fantasyhub/source-choices.json').write_text(json.dumps(choices,indent=2)+'\n')
 index={str(path.relative_to(out)):hashlib.sha256(path.read_bytes()).hexdigest() for path in (out/'assets').rglob('*') if path.is_file() and path.name!='asset-index.json'}
 (out/'assets/fantasyhub/asset-index.json').write_text(json.dumps(index,sort_keys=True,indent=2)+'\n')
print('Packaged',len(names),'authored blueprints and',len(models),'model/animation IDs. Source material remains local.')
