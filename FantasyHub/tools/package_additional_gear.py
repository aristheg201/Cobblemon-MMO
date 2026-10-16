#!/usr/bin/env python3
"""Convert the three explicitly supplied October16 additions; no source-inventory rescan."""
import argparse,base64,hashlib,json,shutil,zipfile
from pathlib import Path
import yaml

PARTS={'HELMET':'leather_helmet','CHESTPLATE':'leather_chestplate','LEGGINGS':'leather_leggings','BOOTS':'leather_boots'}

def build(root,out,pack):
    models=[];sprites=[];items={};rules={};source_map={}
    def write(path,data):
        p=out/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    def copy(src,dst):
        p=out/dst;p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,p)
    def texture(src,name):
        copy(src,f'assets/fantasyhub/textures/{name}.png');sprites.append({'type':'minecraft:single','resource':f'fantasyhub:{name}','sprite':f'fantasyhub:{name}'})
    def model(name,data):
        write(f'assets/fantasyhub/models/item/additions/{name}.json',data);models.append('fantasyhub:item/additions/'+name)
    def gear(collection,display,folder,layer1,layer2,icons,hat=None,kabuto=False):
        for layer,src in [(1,layer1),(2,layer2)]:copy(folder/src,f'assets/fantasyhub/textures/armor/additions/{collection}_layer_{layer}.png')
        for part,icon in icons.items():
            name=collection+'_'+part.lower();iid=('KABUTO_' if kabuto else 'ADDED_'+collection.upper()+'_')+part
            carrier='fantasyhub:kabuto_hat' if kabuto and part=='HELMET' else 'fantasyhub:dragonborn_hat' if part=='HELMET' else 'minecraft:'+PARTS[part]
            # No RPG stats were supplied for the new art packs. Preserve vanilla
            # leather-equivalent defense rather than invent endgame bonuses.
            defense={'HELMET':1,'CHESTPLATE':3,'LEGGINGS':2,'BOOTS':1}[part]
            items[iid]={'type':'armor','material':carrier,'name':display+' '+{'HELMET':'Helmet','CHESTPLATE':'Chestplate','LEGGINGS':'Leggings','BOOTS':'Boots'}[part],'rarity':'common','stats':{'armor':{'value':defense,'decimals':0}}}
            rule={'armor-texture':'fantasyhub:additions/'+collection,'item-model':'fantasyhub:item/additions/'+name,'unbreakable':True}
            if kabuto:rule['required-class']=['KABUTO']
            rules[iid]=rule;source_map[iid]=str(folder.relative_to(root))+'/'+icon
            texture(folder/icon,'additions/'+name)
            data={'parent':'minecraft:item/generated','textures':{'layer0':'fantasyhub:additions/'+name}}
            if hat and part=='HELMET':
                src=json.loads((folder/hat).read_text());src.pop('parent',None);src.pop('format_version',None)
                texfile=(folder/hat).with_suffix('.png');tex='additions/'+collection+'_hat';texture(texfile,tex)
                src['textures']={key:'fantasyhub:'+tex for key in src['textures']}
                src.setdefault('textures',{})['particle']='fantasyhub:'+tex
                data=src
            if kabuto and part=='HELMET':data={'parent':'builtin/entity','display':kabuto_display}
            model(name,data)
    kab=root/'1-kabuto-2-/kabuto';bb=json.loads((kab/'KABUTO.bbmodel').read_text());kabuto_display=bb['display']
    # 25-degree Euler rotations in this Java1.21.11 authoring file cannot be
    # rounded into Java1.21.1's restricted element rotations. Keep all vertices,
    # pivots and original face UVs in a native dynamic item renderer instead.
    authored=[]
    for e in bb['elements']:
        if not e.get('export',True):continue
        authored.append({k:e[k] for k in ('from','to','origin','rotation','faces') if k in e})
    write('assets/fantasyhub/authored/kabuto_hat.json',{'elements':authored,'texture_size':[64,64]})
    raw=base64.b64decode(bb['textures'][0]['source'].split(',',1)[1]);p=out/'assets/fantasyhub/textures/additions/kabuto_hat.png';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
    gear('kabuto','Kabuto',kab,'kabuto armor/KABUTO_ Armor.png','kabuto armor/KABUTO_Leggings.png',{p:'icon kabuto/icon kabuto_'+{'HELMET':'Hat','CHESTPLATE':'armor','LEGGINGS':'Leggings','BOOTS':'boots'}[p]+'.png' for p in PARTS},kabuto=True)
    wol=root/'2-iconGrandmaster-Wolven_boots'
    gear('grandmaster_wolven','Grandmaster Wolven',wol,'Grandmaster Wolven Armor.png','Grandmaster Wolven_Leggings.png',{'CHESTPLATE':'icon Grandmaster Wolven _Armor.png','LEGGINGS':'icon Grandmaster Wolven_Leggings.png','BOOTS':'iconGrandmaster Wolven_boots.png'})
    sky=root/'3-Skyrim-Armor'
    specs=[('iron','Skyrim Iron Armor','Skyrim Iron Armor1.png','Skyrim Iron Armor_Leggings1.png','Skyrim Iron _Hat.json',['icon Skyrim Iron Armor_Hat.png','iconSkyrim Iron Armor.png','icon Skyrim Iron _Leggings.png','icon Skyrim Iron _boots.png']),('ebony','Skyrim Ebony Armor','Skyrim Ebony Armor1.png','Skyrim Ebony_Leggings1.png','Skyrim Ebony Armor_hat.json',['icon Skyrim Ebony _Hat.png','icon Skyrim Ebony _Armor.png','icon Skyrim Ebony _Leggings.png','icon Skyrim Ebony _boots.png']),('daedric','Skyrim Daedric Armor','Skyrim Daedric Armor1.png','Skyrim Daedric Leggings1.png','Skyrim Daedric_hat.json',['icon Skyrim Daedric_Hat.png','icon Skyrim Daedric_armor.png','icon Skyrim Daedric_Leggings.png','icon Skyrim Daedric_boots.png']),('dragonbone','Skyrim Dragonbone','Skyrim Dragonbone1.png','Skyrim Dragonbone_Leggings1.png','Skyrim Dragonbone_Hat.json',['icon Skyrim Dragonbone_Hat.png','icon Skyrim Dragonbone_armor.png','icon Skyrim Dragonbone_Leggings.png','icon Skyrim Dragonbone _boots.png'])]
    for key,folder,l1,l2,hat,icons in specs:gear('skyrim_'+key,'Skyrim '+key.title(),sky/folder,l1,l2,dict(zip(PARTS,icons)),hat)
    from kabuto_reference_models import equipment_models,form_geometry
    for name,data in equipment_models().items():model(name,data)
    write('assets/fantasyhub/authored/kabuto_forms.json',form_geometry())
    for iid,carrier,name,modelname in [('KABUTO_ZECTER_BELT','fantasyhub:zecter_belt','Kabuto Zecter Belt','zecter_belt'),('KABUTO_KUNAI_GUN','fantasyhub:kunai_gun','Kabuto Kunai Gun','kunai_gun')]:
        items[iid]={'type':'accessory' if 'BELT' in iid else 'sword','material':carrier,'name':name,'rarity':'common','stats':{'mana-regeneration':{'value':.5,'decimals':1},'skill-damage':{'value':5,'decimals':0}} if 'BELT' in iid else {'attack-damage':{'value':6,'decimals':0}}}
        rules[iid]={'required-class':['KABUTO'],'item-model':'fantasyhub:item/additions/'+modelname,'unbreakable':True}
    model('kabuto_helmet_icon',{'parent':'minecraft:item/generated','textures':{'layer0':'fantasyhub:additions/kabuto_helmet'}})
    write('assets/fantasyhub/additions-models.json',models)
    atlas=out/'assets/minecraft/atlases/blocks.json';j=json.loads(atlas.read_text());j['sources']=[s for s in j['sources'] if s not in sprites]+sprites;write('assets/minecraft/atlases/blocks.json',j)
    with zipfile.ZipFile(pack) as z:files={n:z.read(n) for n in z.namelist()}
    files={n:b for n,b in files.items() if not n.startswith('kabuto/')}
    from additional_gear_sources import migrate
    migrated=migrate(root.parent/'user-packs',files,items,rules,source_map)
    for path,data in {'SVFrameItems/items/additions.yml':items,'SVFrameItems/rules/additions.yml':rules}.items():files[path]=yaml.safe_dump(data,allow_unicode=True,sort_keys=False).encode()
    for p in (Path(__file__).resolve().parents[1]/'core/src/main/resources/default/kabuto').rglob('*.yml'):files[str(p.relative_to(Path(__file__).resolve().parents[1]/'core/src/main/resources/default/kabuto'))]=p.read_bytes()
    manifest={'schema':1,'revision':'source-graphs-kabuto-1','files':[{'path':n,'sha256':hashlib.sha256(b).hexdigest()} for n,b in files.items() if n not in ('manifest.json','source-choices.json')]}
    files['manifest.json']=json.dumps(manifest).encode();choices=json.loads(files['source-choices.json']);choices['October16-additions']=source_map;files['source-choices.json']=json.dumps(choices).encode()
    with zipfile.ZipFile(pack,'w',zipfile.ZIP_DEFLATED) as z:
        for n,b in files.items():z.writestr(n,b)
    index={str(p.relative_to(out)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (out/'assets').rglob('*') if p.is_file() and p.name!='asset-index.json'};write('assets/fantasyhub/asset-index.json',index)
    from finalize_equipment_compat import finalize
    finalize(Path(__file__).resolve().parents[1],pack)
    print('Added',len(items),'items;',len(models),'item models. Original authored bytes retained. Wolven deliberately has3pieces.')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--references',type=Path,required=True);p.add_argument('--client-output',type=Path,default=Path(__file__).resolve().parents[1]/'client/src/imported/resources');p.add_argument('--source-pack',type=Path,default=Path(__file__).resolve().parents[1]/'core/src/imported/resources/default/source-pack.zip');a=p.parse_args();build(a.references,a.client_output,a.source_pack)
