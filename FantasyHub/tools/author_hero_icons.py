#!/usr/bin/env python3
"""Editable integer-pixel HUD glyphs; these are interface art, never purported 3D models."""
import json,math
from pathlib import Path
from PIL import Image,ImageDraw
root=Path(__file__).resolve().parents[1];out=root/'client/src/main/resources/assets/fantasyhub';source=root/'assets-source/ui';source.mkdir(parents=True,exist_ok=True)
W='#e7f5ff';D='#192a42';G='#ffd46a';palette={'SPIDER_MAN':'#ff5c70','SORCERER_SUPREME':'#ff983f','IRON_MAN':'#75e1ff','THOR':'#9dbfff','FLASH':'#ffce48','BATMAN':'#9aadc8'}
def line(p,c=W,w=2):return ['line',p,c,w]
def poly(p,c=W):return ['poly',p,c]
def rect(x,y,w,h,c=W):return ['rect',[x,y,x+w-1,y+h-1],c]
def oval(x,y,w,h,c=W):return ['oval',[x,y,x+w-1,y+h-1],c]
def bolt(c):return [poly([(18,2),(7,18),(14,18),(10,30),(27,12),(19,12),(24,2)],c)]
def web(c):
 ops=[]
 for i in range(8):
  a=i*math.pi/4;ops+=[line([(16,16),(round(16+13*math.cos(a)),round(16+13*math.sin(a)))],c)]
 for r in [5,10]:ops+=[line([(round(16+r*math.cos(i*math.pi/4)),round(16+r*math.sin(i*math.pi/4))) for i in range(9)],W,1)]
 return ops
def shield(c):return [poly([(5,6),(16,2),(27,6),(25,20),(16,30),(7,20)],c),poly([(9,9),(16,6),(23,9),(21,19),(16,24),(11,19)],D)]
def fist(c):return [rect(7,13,20,12,c),rect(4,16,5,7,c),rect(8,6,4,9,W),rect(13,4,4,11,W),rect(18,5,4,10,W),rect(23,7,4,8,W),line([(9,27),(22,27)],W,3)]
def ring(c):return [oval(3,3,26,26,c),oval(6,6,20,20,D),line([(16,6),(26,16),(16,26),(6,16),(16,6)],W)]
def missile(c,x=16):return [poly([(x,2),(x+3,8),(x+3,21),(x,24),(x-3,21),(x-3,8)],W),rect(x-2,10,5,10,c),poly([(x-3,18),(x-6,26),(x-2,24),(x,30),(x+2,24),(x+6,26),(x+3,18)],c)]
def bat(c):return [poly([(2,7),(9,12),(13,9),(16,4),(19,9),(23,12),(30,7),(26,23),(22,18),(19,24),(16,29),(13,24),(10,18),(6,23)],c)]
def hammer(c):return [rect(7,4,19,10,W),rect(5,6,23,6,c),line([(16,15),(16,29)],G,4),line([(12,29),(20,29)],W)]
def axe(c):return [line([(16,6),(16,29)],'#b78956',3),rect(6,5,10,7,c),poly([(16,5),(26,2),(30,8),(27,16),(20,14),(16,10)],W)]
def wind(c):return [line([(2,7),(24,7),(28,11),(24,14),(11,14)],c),line([(3,18),(19,18),(23,22),(19,26),(8,26)],W),line([(4,30),(27,30)],c)]
def ghost(c,x=16):return [oval(x-4,3,9,9,c),poly([(x-5,14),(x+5,14),(x+8,28),(x+3,25),(x,29),(x-3,25),(x-8,28)],c)]
recipes={};catalog=json.loads((root/'core/src/main/resources/default/heroes.json').read_text())
for hero in catalog['heroes']:
 c=palette[hero['id']]
 drawings={
 'SPIDER_MAN':[web(c)+[line([(3,28),(11,22)],G)],web(c)+[line([(2,28),(16,4),(29,27)],G,3)],web(c)+[oval(22,2,8,8,G)],web(c)+[oval(10,8,12,19,c),line([(12,12),(19,12)],W)],fist(c)+[line([(2,28),(2,19),(5,22)],c)],shield(c)+[line([(10,16),(16,10),(22,16),(16,22),(10,16)],W)],web(c)+fist(c)],
 'SORCERER_SUPREME':[[line([(3,22),(7,6),(13,4),(23,8),(28,19),(20,26),(9,21)],c,3)],ring(c)+[rect(14,9,4,14,c)],ring(c)+[line([(2,8),(30,24)],W),line([(2,24),(30,8)],W)],ghost(c)+[oval(2,14,7,7,W)],ring(c)+[rect(14,1,4,30,W)],ghost(c,7)+ghost(c,25)+ghost(W,16),ring(c)+[poly([(5,16),(13,11),(20,11),(28,16),(20,21),(13,21)],G),oval(13,12,8,8,c)]],
 'IRON_MAN':[[oval(7,7,19,19,c),oval(11,11,11,11,W),line([(2,3),(7,7)],G),line([(26,26),(30,30)],G)]],
 'THOR':[axe(c)+[line([(2,22),(8,18)],c)],axe(c)+[line([(2,26),(16,30),(30,26)],c)],wind(c)+axe(c),axe(c)+[line([(1,26),(9,21)],G,3)],ring(c)+[line([(5,29),(16,2),(27,29)],W,3)],bolt(c)+axe(c),bolt(G)+[line([(3,28),(16,21),(29,28)],W,3)]],
 'FLASH':[bolt(c)+[line([(3,8),(11,8)],W),line([(2,24),(8,24)],W)],ring(c)+[line([(16,7),(16,16),(23,20)],W,3)],bolt(c)+[line([(1,29),(8,29),(11,25)],W)],wind(c)+[poly([(13,5),(18,5),(20,29),(11,29)],c)],ghost(c)+[line([(4,4),(4,28)],W),line([(28,4),(28,28)],W)],bolt(c)+[line([(2,25),(16,25),(13,22),(16,25),(13,28)],W)],fist(c)+bolt(G)],
 'BATMAN':[bat(c),[line([(5,27),(23,7),(27,7),(28,12),(24,16)],W,3),rect(3,23,9,6,c)],bat(c)+[oval(4,16,12,10,W),oval(15,18,13,10,D)],ring(c)+[line([(16,2),(16,30)],c),line([(2,16),(30,16)],c)],bat(c)+bolt(W),bat(c)+fist(W),bat(c)+[line([(3,27),(3,22),(9,22),(9,27)],G),line([(23,27),(23,22),(29,22),(29,27)],G)]]
 }[hero['id']]
 if hero['id']=='IRON_MAN':drawings += [missile(c,8)+missile(G,24),[line([(3,3),(29,29)],c,4),line([(3,29),(29,3)],W,3)],shield(c)+[line([(1,22),(9,22)],W),line([(1,27),(8,27)],c)],bolt(c)+[rect(11,11,10,10,W)],ghost(c,7)+ghost(c,25),[rect(5,3,22,6,c),rect(13,8,6,21,W),line([(2,29),(30,29)],G)]]
 for a,ops in zip(hero['abilities'],drawings,strict=True):recipes[a['id'].lower()]=ops
manifest={}
for key,ops in recipes.items():
 assert all(isinstance(op,list) and op[0] in {"line","rect","oval","poly"} for op in ops), key
 im=Image.new('RGBA',(32,32));draw=ImageDraw.Draw(im)
 for op,coords,c,*rest in ops:
  if op=='line':draw.line(coords,fill=c,width=rest[0] if rest else 2,joint='curve')
  elif op=='rect':draw.rectangle(coords,fill=c)
  elif op=='oval':draw.ellipse(coords,fill=c)
  else:draw.polygon(coords,fill=c)
 path=out/f'textures/gui/skills/{key}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path);manifest[key.upper()]=key
(source/'hero-skill-icons.json').write_text(json.dumps({'size':32,'palette':palette,'recipes':recipes},indent=2)+'\n');(out/'hero-skill-icons.json').write_text(json.dumps(manifest,indent=2)+'\n');print('42 original native superhero HUD glyphs authored.')
