#!/usr/bin/env python3
"""Build the original SVFrame demo resource pack; no downloaded/proprietary assets."""
from pathlib import Path
import json, struct, zlib, zipfile
root=Path(__file__).resolve().parents[1]
files={}
def add(name,obj): files[name]=json.dumps(obj,indent=2).encode()
add('pack.mcmeta',{'pack':{'pack_format':34,'description':'SVFrame native visual demo · Minecraft 1.21.1'}})
add('assets/minecraft/models/item/iron_sword.json',{'parent':'minecraft:item/handheld','textures':{'layer0':'minecraft:item/iron_sword'},'overrides':[{'predicate':{'custom_model_data':10001},'model':'svframe:item/soul_blade'}]})
add('assets/svframe/models/item/soul_blade.json',{'parent':'minecraft:item/handheld','textures':{'layer0':'svframe:item/soul_blade'}})
def chunk(key,data): return struct.pack('>I',len(data))+key+data+struct.pack('>I',zlib.crc32(key+data)&0xffffffff)
pixels=[]
for y in range(32):
 row=[]
 for x in range(32):
  color=(0,0,0,0)
  if 2<=y<=23 and abs(x-15)<=min(4,(y-1)//2): color=(205,158,255,255) if x in (13,14,15) else (90,38,155,255)
  if y in (23,24) and 7<=x<=23: color=(125,70,200,255)
  if 25<=y<=29 and 14<=x<=16: color=(55,35,85,255)
  if y==30 and 13<=x<=17: color=(200,140,255,255)
  row.extend(color)
 pixels.append(bytes([0])+bytes(row))
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,32,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(pixels)))+chunk(b'IEND',b'')
files['assets/svframe/textures/item/soul_blade.png']=png
output=root/'dist/SVFrameVisuals-1.21.1.zip';output.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
 for name,data in sorted(files.items()):
  info=zipfile.ZipInfo(name,(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,data)
print(output)
