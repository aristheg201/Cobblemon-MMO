"""Transform the established Dragonborn source entries into native item definitions."""
import yaml


def append_source_files(inventory, references, files, origins):
    source_rows=[r for r in inventory['equipment'] if r['id'].startswith('SVFRAME_DRAGONBORN_')]
    sets=yaml.safe_load((references/'config2/MMOItems/item-sets.yml').read_text())
    templates=yaml.safe_load((references/'config2/MMOItems/upgrade-templates.yml').read_text())
    native_items={};rules={};native_sets={};rarities={};upgrades={}
    stats={'armor','armor-toughness','max-health','skill-damage','cooldown-reduction','damage-reduction','pve-damage-reduction','pvp-damage-reduction','knockback-resistance'}
    allowed=stats|{'material','name','displayed-type','lore','unbreakable','disable-enchanting','tier','set','required-class','custom-model-data','dye-color','hide-dye','disable-droping','disable-death-drop','disable-crafting','disable-smelting','disable-smithing','disable-repairing','custom-nbt','upgrade'}
    for row in source_rows:
        name=row['id'];base=row['definition']['base'];unknown=set(base)-allowed
        if unknown:raise ValueError(f'{name}: unsupported source fields {unknown}')
        tier='source_'+base['tier'].lower();rarities[tier]={'name':base['tier'],'weight':1,'priority':0,'stat-multiplier':1.0}
        template='source_'+base['upgrade']['template'];upgrades[template]={'max-level':base['upgrade']['max'],'base-success':base['upgrade']['success']/100,'success-decay':1,'destroy-on-fail':base['upgrade']['destroy'],'stat-multiplier-per-level':0}
        native_items[name]={'type':'armor','material':'fantasyhub:dragonborn_hat' if base['material']=='PAPER' else 'minecraft:'+base['material'].lower(),'name':base['name'],'rarity':tier,'stats':{key:{'value':base[key],'decimals':6} for key in stats if key in base},'set':base['set'],'upgrade-template':template}
        item_policy={k:base[k] for k in allowed-stats-{'upgrade','material','name','tier','set','custom-nbt','displayed-type'} if k in base}
        item_policy['required-class']=['DRAGONBORN'];item_policy['upgrade-stats']={k.upper().replace('-','_'):v for k,v in templates[base['upgrade']['template']].items()}
        if isinstance(base.get('dye-color'),str):
            r,g,b=map(int,base['dye-color'].split());item_policy['dye-color']=(r<<16)|(g<<8)|b
        collection='skyrim_iron' if '_ARMOR_' in name else 'ender_dragon'
        part='hat' if base['material']=='PAPER' else base['material'].split('_',1)[1].lower()
        item_policy['armor-texture']='fantasyhub:dragonborn/'+collection
        item_policy['item-model']='fantasyhub:item/dragonborn/'+collection+'_'+part
        rules[name]=item_policy
        source_set=sets[base['set']]
        native_sets.setdefault(base['set'],{'name':source_set['name'],'pieces':[],'bonuses':source_set['bonuses']})['pieces'].append(name)
    generated={'SVFrameItems/items/dragonborn.yml':native_items,'SVFrameItems/rules/dragonborn.yml':rules,'SVFrameItems/source/sets.yml':native_sets,'SVFrameItems/source/rarities.yml':rarities,'SVFrameItems/source/upgrades.yml':upgrades}
    for path,data in generated.items():files[path]=yaml.safe_dump(data,allow_unicode=True,sort_keys=False).encode();origins[path]=[r['source'] for r in source_rows]
    return len(native_items)


def package_assets(references, output):
    import shutil,json
    entries=[]; sprites=[]
    contents=references/'config1/ItemsAdder/contents'
    for collection,folder,namespace,texture_folder,prefix in [
        ('skyrim_iron','svframe_skyrim_armors','svframe','skyrim_iron/armor','skyrim_iron'),
        ('ender_dragon','ender_dragonset','ender_dragonset','armor','ender_dragon')]:
        source_assets=contents/folder/'resourcepack/assets'/namespace
        for layer in (1,2):
            source=source_assets/'textures'/texture_folder/f'{prefix}_armor_layer_{layer}.png'
            target=output/f'assets/fantasyhub/textures/armor/dragonborn/{collection}_layer_{layer}.png';target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
        parts=['helmet','chestplate','leggings','boots']+(['hat'] if collection=='skyrim_iron' else [])
        for part in parts:
            texture_name=f'{prefix}_{part}'+('_icon' if collection=='ender_dragon' else '')
            texture=source_assets/'textures'/texture_folder/f'{texture_name}.png'
            texture_target=output/'assets'/namespace/'textures'/texture_folder/texture.name;texture_target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(texture,texture_target)
            sprite=f'{namespace}:{texture_folder}/{texture_name}'
            sprites.append({'type':'minecraft:single','resource':sprite,'sprite':sprite})
            model_id=f'fantasyhub:item/dragonborn/{collection}_{part}';model_target=output/f'assets/fantasyhub/models/item/dragonborn/{collection}_{part}.json';model_target.parent.mkdir(parents=True,exist_ok=True)
            if part=='hat':
                model_source=source_assets/'models/skyrim_iron/armor/skyrim_iron_hat.json';model=json.loads(model_source.read_text());model_target.write_text(json.dumps(model))
            else:model_target.write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'{namespace}:{texture_folder}/{texture_name}'}}))
            entries.append(model_id)
    target=output/'assets/fantasyhub/dragonborn-models.json';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(entries,indent=2)+'\n')
    # The source textures live outside Minecraft's item/block directories;
    # register them explicitly in the atlas consumed by baked item models.
    atlas=output/'assets/minecraft/atlases/blocks.json';atlas.parent.mkdir(parents=True,exist_ok=True);atlas.write_text(json.dumps({'sources':sprites},indent=2)+'\n')
    default_hat=output/'assets/fantasyhub/models/item/dragonborn_hat.json';default_hat.write_text(json.dumps({'parent':'fantasyhub:item/dragonborn/skyrim_iron_hat'})+'\n')
    return entries
