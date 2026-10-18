"""Package original, editable Rider equipment without rewriting server customizations."""
import json,hashlib,zipfile
from pathlib import Path
import yaml

ROOT=Path(__file__).resolve().parents[1]
DEFAULT=ROOT/'core/src/main/resources/default/riders'
FORMS=['zolda','agito_ground','agito_storm','agito_flame','agito_trinity','agito_shining','ryuki','knight','decade']
NAMES={'zolda':'Zolda','agito_ground':'Agito Ground','agito_storm':'Agito Storm','agito_flame':'Agito Flame','agito_trinity':'Agito Trinity','agito_shining':'Agito Shining','ryuki':'Ryuki','knight':'Knight','decade':'Decade'}

def main():
 items={};rules={};english={};vietnamese={}
 for form in FORMS:
  for slot,material,armor in [('helmet','leather_helmet',2),('chestplate','leather_chestplate',6),('leggings','leather_leggings',4),('boots','leather_boots',2)]:
   ident=form+'_'+slot
   items[ident.upper()]={'type':'armor','material':'minecraft:'+material,'name':NAMES[form]+' '+slot.title(),'rarity':'common','stats':{'armor':{'value':armor,'decimals':0}}}
   rules[ident.upper()]={'armor-texture':'fantasyhub:riders/'+form,'item-model':'fantasyhub:item/riders/'+ident,'unbreakable':True,'disable-enchanting':False}
   english['item.fantasyhub.'+ident]=NAMES[form]+' '+slot.title()
   vietnamese['item.fantasyhub.'+ident]={'helmet':'Mũ','chestplate':'Giáp ngực','leggings':'Giáp chân','boots':'Ủng'}[slot]+' '+NAMES[form]
 for ident,name,kind,material,vi in [
  ('zolda_magna_visor','Magna Visor','bow','crossbow','Magna Visor'),
  ('zolda_giga_launcher','Giga Launcher','bow','crossbow','Giga Launcher'),
  ('agito_flame_saber','Flame Saber','sword','iron_sword','Kiếm Flame Saber'),
  ('agito_storm_halberd','Storm Halberd','sword','iron_sword','Thương Storm Halberd'),
  ('agito_shining_caliber','Shining Caliber','sword','iron_sword','Song kiếm Shining Caliber'),
  ('ryuki_drag_saber','Drag Saber','sword','iron_sword','Kiếm Drag Saber'),
  ('knight_dark_visor','Dark Visor','sword','iron_sword','Kiếm Dark Visor'),
  ('knight_dark_blade','Dark Blade','sword','iron_sword','Song kiếm Dark Blade'),
  ('decade_ride_booker_sword','Ride Booker Sword','sword','iron_sword','Kiếm Ride Booker'),
  ('decade_ride_booker_gun','Ride Booker Gun','bow','crossbow','Súng Ride Booker')]:
  model=ident.removeprefix('zolda_').removeprefix('agito_').removeprefix('ryuki_').removeprefix('knight_').removeprefix('decade_')
  items[ident.upper()]={'type':kind,'material':'minecraft:'+material,'name':name,'rarity':'common','stats':{'attack-damage':{'value':6,'decimals':0}}}
  rules[ident.upper()]={'item-model':'fantasyhub:item/riders/'+model,'unbreakable':True,'disable-enchanting':False}
  english['item.fantasyhub.'+ident]=name;vietnamese['item.fantasyhub.'+ident]=vi
 for ident,name,vi in [('agito_belt','Alter Ring','Đai Alter Ring Agito'),('zolda_belt','Zolda V-Buckle / Card Deck','Đai V-Buckle / Bộ thẻ Zolda'),('ryuki_belt','Ryuki V-Buckle / Card Deck','Đai V-Buckle Ryuki'),('knight_belt','Knight V-Buckle / Card Deck','Đai V-Buckle Knight'),('decade_belt','Decadriver','Đai Decadriver')]:
  items[ident.upper()]={'type':'accessory','material':'fantasyhub:'+ident,'name':name,'rarity':'common','stats':{}}
  rules[ident.upper()]={'item-model':'fantasyhub:item/riders/'+ident,'unbreakable':True,'disable-enchanting':False}
  english['item.fantasyhub.'+ident]=name;vietnamese['item.fantasyhub.'+ident]=vi
 for relative,data in [('SVFrameItems/items/riders.yml',items),('SVFrameItems/rules/riders.yml',rules)]:
  target=DEFAULT/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(yaml.safe_dump(data,sort_keys=False,allow_unicode=True))
 for lang,entries in [('en_us',english),('vi_vn',vietnamese)]:
  target=ROOT/'client/src/main/resources/assets/fantasyhub/lang'/f'{lang}.json';current=json.loads(target.read_text());current.update(entries);target.write_text(json.dumps(current,indent=2,ensure_ascii=False)+'\n')
 pack=ROOT/'core/src/imported/resources/default/source-pack.zip'
 with zipfile.ZipFile(pack) as z:files={n:z.read(n) for n in z.namelist()}
 for target in DEFAULT.rglob('*.yml'):files[str(target.relative_to(DEFAULT))]=target.read_bytes()
 # Native additions remain explicit in the editable class-switch allowlist.
 policy_path='FantasyHub/class-switch.yml'
 policy=yaml.safe_load(files[policy_path]);policy['additional-native-classes']=list(dict.fromkeys(policy.get('additional-native-classes',[])+['AGITO','ZOLDA','RYUKI','KNIGHT','DECADE']))
 files[policy_path]=yaml.safe_dump(policy,sort_keys=False,allow_unicode=True).encode()
 manifest=json.loads(files['manifest.json']);manifest['revision']='rider-equipment-0.1.11'
 manifest['files']=[{'path':n,'sha256':hashlib.sha256(b).hexdigest()} for n,b in files.items() if n not in ('manifest.json','source-choices.json')]
 files['manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
 with zipfile.ZipFile(pack,'w',zipfile.ZIP_DEFLATED) as z:
  for name,data in files.items():z.writestr(name,data)
 print('Packaged',len(items),'real equipment definitions; class gameplay is a separate runtime requirement')

if __name__=='__main__':main()
