#!/usr/bin/env python3
"""Reproducible original modern black plated suit. No source skin pixels are sampled."""
from PIL import Image, ImageDraw
from pathlib import Path
import json, hashlib
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'client/src/imported/resources/assets/fantasyhub'
DARK=(12,15,19,255); PANEL=(28,33,39,255); EDGE=(55,62,69,255); SEAM=(5,7,10,255)
base=Image.new('RGBA',(64,64)); d=ImageDraw.Draw(base)
# Vanilla 64px biped UV rectangles; every face is newly authored.
parts={'head':[(0,0,32,16)],'chest':[(16,16,40,32),(40,16,56,32),(32,48,48,64)],'legs':[(0,16,16,32),(16,48,32,64)],'feet':[]}
for name,rects in parts.items():
 for x0,y0,x1,y1 in rects:
  d.rectangle((x0,y0,x1-1,y1-1),fill=DARK)
  for y in range(y0,y1):
   for x in range(x0,x1):
    value=((x*7+y*11)%5)-2;base.putpixel((x,y),(DARK[0]+value,DARK[1]+value,DARK[2]+value,255))
# Cowl: sculpted brow, angled white eye slits, graphite cheek guards, exposed jaw.
d.polygon([(8,8),(15,8),(14,10),(9,10)],fill=PANEL);d.polygon([(8,10),(11,11),(10,12),(8,11)],fill=(225,240,248,255));d.polygon([(12,11),(15,10),(15,11),(13,12)],fill=(225,240,248,255))
d.polygon([(10,13),(13,13),(13,15),(10,15)],fill=(146,116,97,255));d.line((10,15,13,15),fill=(76,64,59,255));d.line((11,12,12,12),fill=EDGE)
# Ribbed breast plates, articulated abdomen and black bat emblem.
d.polygon([(20,20),(23,20),(23,24),(20,23)],fill=PANEL);d.polygon([(24,20),(27,20),(27,23),(24,24)],fill=PANEL)
d.line((20,20,23,20),fill=EDGE);d.line((24,20,27,20),fill=EDGE)
bat=[(20,21),(22,22),(23,21),(23,22),(24,22),(24,21),(25,22),(27,21),(26,23),(25,23),(24,24),(23,23),(22,23)]
d.polygon(bat,fill=(2,3,4,255))
for y in (25,27):d.rectangle((21,y,26,y+1),fill=PANEL);d.line((21,y,26,y),fill=(42,47,52,255))
# Tactical graphite belt, six hard pouches, small brass central fastener.
d.rectangle((16,29,39,31),fill=(24,26,27,255))
for x in (17,20,23,26,29,32,35):d.rectangle((x,29,x+1,31),fill=(57,55,47,255))
d.rectangle((23,29,24,30),fill=(120,108,76,255))
# Shoulders/gauntlets and front knees / greaves, with a repeated side seam rather than gray tights.
for x,y in [(44,20),(36,52)]:
 d.rectangle((x,y,x+3,y+3),fill=PANEL);d.line((x,y,x+3,y),fill=EDGE);d.rectangle((x,y+6,x+3,y+10),fill=(23,29,35,255));d.line((x,y+6,x+3,y+6),fill=EDGE)
for x,y in [(4,20),(20,52)]:
 d.polygon([(x,y),(x+3,y),(x+3,y+4),(x+2,y+5),(x,y+4)],fill=PANEL)
 d.rectangle((x,y+5,x+3,y+7),fill=(43,49,55,255));d.rectangle((x,y+8,x+3,y+11),fill=PANEL);d.line((x,y+8,x+3,y+8),fill=EDGE)
# Detailed Animations retains vanilla outer jacket/sleeve/pants UVs on slightly larger bones.
# Cover those same surfaces so a player's colored skin overlay cannot protrude through armor.
for source,dest in [((16,16,40,32),(16,32)),((40,16,56,32),(40,32)),((32,48,48,64),(48,48)),((0,16,16,32),(0,32)),((16,48,32,64),(0,48))]:
 base.paste(base.crop(source),dest)
parts['chest'] += [(16,32,40,48),(40,32,56,48),(48,48,64,64)]
parts['legs'] += [(0,32,16,48),(0,48,16,64)]
paths=[]
def save(im,path):
 p=OUT/path;p.parent.mkdir(parents=True,exist_ok=True);im.save(p);paths.append(p)
for part in ('head','chest','legs','feet'):
 im=Image.new('RGBA',(64,64))
 regions=parts['legs'] if part=='feet' else parts[part]
 for rect in regions:
  x0,y0,x1,y1=rect
  if part=='legs':y1-=4
  if part=='feet':y0=y1-4
  im.paste(base.crop((x0,y0,x1,y1)),(x0,y0))
 save(im,Path('textures/armor/heroes')/f'batman_{part}.png')
lights=Image.new('RGBA',(64,64));ld=ImageDraw.Draw(lights)
ld.polygon([(8,10),(11,11),(10,12),(8,11)],fill=(225,240,248,255));ld.polygon([(12,11),(15,10),(15,11),(13,12)],fill=(225,240,248,255));save(lights,Path('textures/armor/heroes/batman_lights.png'))
cape=Image.new('RGBA',(64,32));cd=ImageDraw.Draw(cape)
cd.rectangle((0,0,63,31),fill=(8,10,13,255))
for x in range(2,64,8):cd.line((x,0,x+2,31),fill=(27,31,37,255),width=2)
for x in range(0,64):
 for y in range(27+int(4*abs(__import__('math').sin(x*__import__('math').pi/10))),32):cape.putpixel((x,y),(0,0,0,0))
save(cape,Path('textures/armor/heroes/batman_cape.png'));save(cape,Path('textures/armor/heroes/batman_glider.png'))
# Inventory silhouettes use the same black plates and pale edge highlights.
for part in ('helmet','chestplate','leggings','boots'):
 im=Image.new('RGBA',(32,32));i=ImageDraw.Draw(im)
 if part=='helmet':
  i.polygon([(7,4),(11,8),(21,8),(25,4),(25,25),(21,28),(11,28),(7,25)],fill=DARK,outline=EDGE);i.line((10,15,14,17),fill='white',width=2);i.line((18,17,22,15),fill='white',width=2);i.rectangle((13,23,19,26),fill=(146,116,97,255))
 elif part=='chestplate':
  i.polygon([(7,7),(12,5),(16,9),(20,5),(25,7),(29,18),(24,20),(23,28),(9,28),(8,20),(3,18)],fill=PANEL,outline=EDGE)
  i.polygon([(9,12),(13,15),(15,12),(16,14),(17,12),(19,15),(23,12),(21,18),(18,18),(16,21),(14,18),(11,18)],fill=(2,3,4,255));i.line((10,25,22,25),fill=(115,103,71,255),width=2)
 elif part=='leggings':
  i.polygon([(8,5),(24,5),(24,28),(18,28),(17,15),(15,15),(14,28),(8,28)],fill=PANEL,outline=EDGE);i.rectangle((8,6,24,8),fill=(77,71,54,255));i.line((10,20,13,20),fill=EDGE,width=2);i.line((19,20,22,20),fill=EDGE,width=2)
 else:
  for x in (6,19):i.polygon([(x,7),(x+6,7),(x+6,23),(x+8,24),(x+8,28),(x-2,28),(x-2,24),(x,22)],fill=PANEL,outline=EDGE)
 save(im,Path('textures/item/heroes')/f'hero_batman_{part}.png')
manifest={'design':'Original modern black armored Batman; graphite plates, black bat emblem, white eye slits, black scalloped cape. Not Beyond or Classic.','generator':'tools/author_black_batman.py','sourcePixels':'none; authored UV faces and polygons','files':[{'path':str(p.relative_to(OUT)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths]}
(OUT/'black-batman-assets.json').write_text(json.dumps(manifest,indent=2)+'\n')
# The aggregate import ledger must describe the currently shipped files, not replaced source skins.
ledger=OUT/'heroes-assets.json'
if ledger.exists():
 data=json.loads(ledger.read_text());replaced={'assets/fantasyhub/'+str(p.relative_to(OUT)) for p in paths}
 data['files']=[f for f in data['files'] if f['target'] not in replaced]
 data['files'] += [{'sourceArchive':None,'source':'tools/author_black_batman.py (original procedural artwork)','target':'assets/fantasyhub/'+str(p.relative_to(OUT)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths]
 ledger.write_text(json.dumps(data,indent=2)+'\n')
print('Authored',len(paths),'black armored suit/cape/icon textures')
