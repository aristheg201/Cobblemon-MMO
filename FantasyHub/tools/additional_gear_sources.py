"""Reconcile only the specifically requested Wolven/Skyrim gear with retained source definitions."""
import yaml

def migrate(original,files,items,rules,origins):
    source=original/'config2/MMOItems/item/armor.yml'
    if not source.is_file():raise FileNotFoundError('Exact retained armor source required: '+str(source))
    armor=yaml.load(source.read_text(),Loader=yaml.CSafeLoader)
    source_sets=yaml.load((original/'config2/MMOItems/item-sets.yml').read_text(),Loader=yaml.CSafeLoader)
    def read(name):return yaml.load(files[name],Loader=yaml.CSafeLoader) if name in files else {}
    sets=read('SVFrameItems/source/sets.yml');rarities=read('SVFrameItems/source/rarities.yml');dragon_rules=read('SVFrameItems/rules/dragonborn.yml')
    migrated={};policies={'lore','unbreakable','disable-enchanting','required-class','custom-model-data','dye-color','hide-dye','disable-droping','disable-death-drop','disable-crafting','disable-smelting','disable-smithing','disable-repairing'}
    metadata={'material','name','tier','set','custom-nbt','displayed-type','revision-id','upgrade'}|policies
    for old in list(items):
        if not old.startswith('ADDED_'):continue
        native=items.pop(old);policy=rules.pop(old);origins.pop(old,None);part=old.rsplit('_',1)[1]
        if old.startswith('ADDED_GRANDMASTER_WOLVEN_'):ids=['WITCHER_GRANDMASTER_WOLVEN_'+part]
        elif old.startswith('ADDED_SKYRIM_IRON_'):ids=['SVFRAME_DRAGONBORN_ARMOR_'+('HAT' if part=='HELMET' else part)]
        else:
            collection=old.removeprefix('ADDED_').removesuffix('_'+part)
            ids=['SVFRAME_'+collection+'_ARMOR_'+('HAT' if part=='HELMET' else part)]
            if part=='HELMET':ids.append('SVFRAME_'+collection+'_ARMOR_HELMET')
        for iid in ids:
            base=armor[iid]['base'];target=dict(native)
            target['name']=base['name'];tier='source_'+base['tier'].lower();target['rarity']=tier;rarities[tier]={'name':base['tier'],'weight':1,'priority':0,'stat-multiplier':1.0}
            # Every gameplay stat in these exact source rows is scalar. Reject a
            # newly discovered non-scalar field instead of silently losing it.
            stats={}
            for key,value in base.items():
                if key in metadata:continue
                if not isinstance(value,(int,float)) or isinstance(value,bool):raise ValueError(f'{iid}: unmigrated source field {key}={value!r}')
                stats[key]={'value':value,'decimals':6}
            target['stats']=stats
            rule={k:base[k] for k in policies if k in base}
            if 'required-class' in rule:rule['required-class']=sorted(set(str(v).upper() for v in rule['required-class']))
            if isinstance(rule.get('dye-color'),str):
                r,g,b=map(int,rule['dye-color'].split());rule['dye-color']=(r<<16)|(g<<8)|b
            # Keep flavor/gameplay lore; importer diagnostics stay in source docs.
            if 'lore' in rule:rule['lore']=[s for s in rule['lore'] if not any(k in s.lower() for k in ('&8model:','bản paper','import từ witcher'))]
            rule['armor-texture']=policy['armor-texture'];rule['item-model']=policy['item-model']
            if 'set' in base:
                target['set']=base['set'];definition=source_sets[base['set']]
                sets.setdefault(base['set'],{'name':definition['name'],'pieces':[],'bonuses':definition['bonuses']})
                if iid not in sets[base['set']]['pieces']:sets[base['set']]['pieces'].append(iid)
            if old.startswith('ADDED_SKYRIM_IRON_'):
                # Its original native definitions/stats already exist. Only the
                # newly supplied presentation is selected; no duplicate Iron set.
                dragon_rules[iid]={**dragon_rules[iid],'armor-texture':rule['armor-texture'],'item-model':rule['item-model']}
                if part=='HELMET':
                    alt='SVFRAME_DRAGONBORN_ARMOR_HELMET';dragon_rules[alt]={**dragon_rules[alt],'armor-texture':rule['armor-texture'],'item-model':rule['item-model']}
                continue
            items[iid]=target;rules[iid]=rule;migrated[iid]={'source':'config2/MMOItems/item/armor.yml','source_id':iid,'model':rule['item-model'],'source_stats':{k:base[k] for k in stats},'upgrade_omitted':'user explicitly excluded equipment upgrade progression'}
    for name,data in [('SVFrameItems/source/sets.yml',sets),('SVFrameItems/source/rarities.yml',rarities),('SVFrameItems/rules/dragonborn.yml',dragon_rules)]:files[name]=yaml.safe_dump(data,allow_unicode=True,sort_keys=False).encode()
    origins['October16-source-gear']=migrated
    return migrated
