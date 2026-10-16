"""Migrate the supplied original Hunter gear; upgrades are outside this selection."""
import json, re, shutil
from pathlib import Path
import yaml

def read_yaml(content):return yaml.load(content,Loader=getattr(yaml,"CSafeLoader",yaml.SafeLoader))

COLLECTIONS=('feline','griffin','ursine','kaer_morhen','wolven')
PARTS=('HELMET','CHESTPLATE','LEGGINGS','BOOTS')
BASE_IDS=tuple('SVFRAME_HUNTER_'+p for p in PARTS)+tuple('WITCHER_'+c.upper()+'_'+p for c in COLLECTIONS for p in PARTS)

def append_source_files(inventory,references,files,origins):
    source=references/'config2/MMOItems/item/armor.yml'
    definitions=read_yaml(source.read_text())
    items={};rules={};rarities={}
    stats={'armor','armor-toughness','max-health','skill-damage','weapon-damage','attack-speed','cooldown-reduction','damage-reduction','pve-damage-reduction','pvp-damage-reduction','knockback-resistance'}
    policies={'lore','unbreakable','disable-enchanting','required-class','custom-model-data','dye-color','hide-dye','disable-droping','disable-death-drop','disable-crafting','disable-smelting','disable-smithing','disable-repairing'}
    supported=stats|policies|{'material','name','tier','set','displayed-type','custom-nbt','revision-id','upgrade'}
    for name in BASE_IDS:
        base=definitions[name]['base'];unknown=set(base)-supported
        if unknown:raise ValueError(f'{name}: unmigrated fields {unknown}')
        tier='source_'+base['tier'].lower();rarities[tier]={'name':base['tier'],'weight':1,'priority':0,'stat-multiplier':1.0}
        items[name]={'type':'armor','material':'minecraft:'+('leather_helmet' if base['material']=='PAPER' else base['material'].lower()),'name':base['name'],'rarity':tier,'stats':{key:{'value':base[key],'decimals':6} for key in stats if key in base}}
        if 'set' in base:items[name]['set']=base['set']
        rule={k:base[k] for k in policies if k in base}
        if 'required-class' in rule:rule['required-class']=[c.upper() for c in rule['required-class']]
        if isinstance(rule.get('dye-color'),str):
            r,g,b=map(int,rule['dye-color'].split());rule['dye-color']=(r<<16)|(g<<8)|b
        collection='halfmoon' if name.startswith('SVFRAME_') else name.removeprefix('WITCHER_').rsplit('_',1)[0].lower()
        rule['armor-texture']='fantasyhub:hunter/'+collection
        rule['item-model']='fantasyhub:item/hunter/'+collection+'_'+name.rsplit('_',1)[1].lower()
        rules[name]=rule
    sources=read_yaml((references/'config2/MMOItems/item-sets.yml').read_text())
    sets={'SVFRAME_SET_HUNTER':{'name':sources['SVFRAME_SET_HUNTER']['name'],'pieces':list(BASE_IDS[:4]),'bonuses':sources['SVFRAME_SET_HUNTER']['bonuses']}}
    generated={'SVFrameItems/items/hunter.yml':items,'SVFrameItems/rules/hunter.yml':rules,'SVFrameItems/source/sets.yml':sets,'SVFrameItems/source/rarities.yml':rarities}
    for target,data in generated.items():
        previous=read_yaml(files[target]) if target in files else {}
        previous.update(data);files[target]=yaml.safe_dump(previous,allow_unicode=True,sort_keys=False).encode()
        origins[target]=[origins[target],str(source.relative_to(references))] if target in origins else str(source.relative_to(references))
    return len(items)

def package_assets(references,output):
    contents=references/'config1/ItemsAdder/contents'
    hunter=contents/'svframe_item_packs/resourcepack/assets/svframe'
    witcher=contents/'svframe_imported_mod_assets'
    sprites=[];models=[]
    def copy(source,target):target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
    # Keep every hat element, authored texture and animation metadata unchanged.
    hat=json.loads((hunter/'models/halfmoon/armor/hat.json').read_text())
    for texture in set(hat['textures'].values()):
        if texture.startswith('#'):continue
        ns,path=texture.split(':',1);source=hunter/'textures'/(path+'.png')
        copy(source,output/'assets'/ns/'textures'/(path+'.png'))
        if source.with_suffix('.png.mcmeta').exists():copy(source.with_suffix('.png.mcmeta'),output/'assets'/ns/'textures'/(path+'.png.mcmeta'))
        sprites.append({'type':'minecraft:single','resource':texture,'sprite':texture})
    for collection in ('halfmoon',)+COLLECTIONS:
        if collection=='halfmoon':
            for layer in (1,2):copy(hunter/f'textures/halfmoon/armor/halfmoonanimated_armor_{layer}.png',output/f'assets/fantasyhub/textures/armor/hunter/halfmoon_layer_{layer}.png')
        else:
            # Preserve authored Bedrock geometry as maintainable source. The
            # client reads all bones, cube pivots, rotations, UVs and inflation.
            copy(witcher/f'_REFERENCE_ONLY/witcher_rpg_geo/{collection}_armor.geo.json',output/f'assets/fantasyhub/geo/hunter/{collection}.geo.json')
            copy(witcher/f'resourcepack/assets/witcher_rpg/textures/armor/{collection}_armor.png',output/f'assets/fantasyhub/textures/armor/hunter/{collection}.png')
        for part in PARTS:
            model_id=f'fantasyhub:item/hunter/{collection}_{part.lower()}';models.append(model_id)
            target=output/f'assets/fantasyhub/models/item/hunter/{collection}_{part.lower()}.json';target.parent.mkdir(parents=True,exist_ok=True)
            if collection=='halfmoon' and part=='HELMET':model=hat
            else:
                if collection=='halfmoon':
                    source=hunter/f'textures/halfmoon/armor/halfmoonanimated_{part.lower()}.png';texture=f'fantasyhub:hunter/halfmoon_{part.lower()}'
                else:
                    suffix={'HELMET':'head','CHESTPLATE':'chest','LEGGINGS':'legs','BOOTS':'feet'}[part]
                    # Resolve the original ItemsAdder icon identity, not a guessed filename.
                    icon=('witcher' if collection=='kaer_morhen' else collection)+'_'+suffix
                    source=witcher/f'resourcepack/assets/witcher_rpg/textures/item/armor/{icon}.png';texture=f'fantasyhub:hunter/{collection}_{part.lower()}'
                copy(source,output/'assets/fantasyhub/textures/hunter'/f'{collection}_{part.lower()}.png')
                sprites.append({'type':'minecraft:single','resource':texture,'sprite':texture})
                model={'parent':'minecraft:item/generated','textures':{'layer0':texture}}
            target.write_text(json.dumps(model)+'\n')
    (output/'assets/fantasyhub/hunter-models.json').write_text(json.dumps(models,indent=2)+'\n')
    atlas=output/'assets/minecraft/atlases/blocks.json';data=json.loads(atlas.read_text()) if atlas.exists() else {'sources':[]}
    data['sources']=[s for s in data['sources'] if s not in sprites]+sprites
    atlas.write_text(json.dumps(data,indent=2)+'\n')
    return models
