#!/usr/bin/env python3
"""Original 32px pixel glyphs for native skill IDs; no item/armor screenshots.
Stores editable drawing recipes alongside game PNGs. Pillow only rasterizes
integer-coordinate primitives, with no smoothing or photo input.
"""
from pathlib import Path
import json
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'client/src/main/resources/assets/fantasyhub';SOURCE=ROOT/'assets-source/ui';SOURCE.mkdir(parents=True,exist_ok=True)
C={'ryuki':'#ff713e','knight':'#73baff','decade':'#f446ab','agito':'#efbf57','zolda':'#78d48b','kabuto':'#ec4b57'}
WHITE='#e5edf6';DARK='#1b2a3d';GOLD='#f9d26b';BLUE='#63daf5';RED='#ff525d'
def rect(x,y,w,h,c):return ['rect',[x,y,x+w-1,y+h-1],c]
def line(points,c,w=2):return ['line',points,c,w]
def poly(points,c):return ['poly',points,c]
def oval(x,y,w,h,c):return ['oval',[x,y,x+w-1,y+h-1],c]
def sword(c):return [poly([(6,24),(23,5),(26,4),(26,8),(10,26)],WHITE),line([(9,20),(14,25)],c,3),line([(7,25),(4,28)],GOLD,3)]
def flame(c):return [poly([(15,3),(16,11),(22,7),(25,17),(25,23),(20,28),(11,28),(6,23),(7,16),(12,10),(12,18),(15,14)],c),poly([(16,16),(20,23),(17,26),(12,24)],GOLD)]
def shield(c):return [poly([(6,6),(16,3),(26,6),(25,19),(21,25),(16,29),(10,25),(7,19)],c),poly([(9,9),(16,6),(23,9),(22,18),(16,25),(10,18)],DARK),line([(16,9),(16,21)],WHITE,2)]
def bat(c):return [poly([(2,8),(9,12),(13,10),(16,5),(19,10),(23,12),(30,8),(26,21),(22,17),(19,23),(16,29),(13,23),(10,17),(6,21)],c),line([(16,12),(16,23)],WHITE,2)]
def dragon(c):return [poly([(3,9),(10,10),(13,4),(17,7),(25,3),(24,11),(29,13),(22,18),(18,17),(20,22),(15,28),(8,27),(12,24),(13,20),(8,17),(11,13)],c),rect(19,11,4,2,WHITE),line([(13,21),(15,24),(11,26)],GOLD,2)]
def kick(c):return [poly([(7,4),(14,5),(17,14),(23,20),(29,21),(27,26),(19,27),(12,22),(10,17)],WHITE),poly([(7,4),(14,5),(17,14),(11,17),(8,13)],c),line([(3,20),(8,25)],c,2),line([(3,25),(6,28)],GOLD,2)]
def missile(c,x=16,y=4):return [poly([(x,y),(x+3,y+6),(x+3,y+16),(x,y+19),(x-3,y+16),(x-3,y+6)],WHITE),rect(x-2,y+9,4,7,c),poly([(x-3,y+16),(x-6,y+21),(x-2,y+19),(x,y+25),(x+2,y+19),(x+6,y+21),(x+3,y+16)],c)]
def clock(c):return [oval(5,6,23,23,c),oval(8,9,17,17,DARK),rect(13,2,7,3,WHITE),line([(16,12),(16,18),(22,18)],WHITE,2),line([(4,11),(1,11)],BLUE),line([(4,22),(1,22)],BLUE)]
def card(c,x=8,y=3,w=16,h=26):return [rect(x,y,w,h,c),rect(x+2,y+2,w-4,h-4,DARK),rect(x+4,y+4,w-8,2,WHITE),rect(x+4,y+h-6,w-8,2,WHITE)]
def phantom(c,x=16,y=6):return [oval(x-4,y,8,8,c),poly([(x-5,y+10),(x+5,y+10),(x+8,y+23),(x+3,y+21),(x,y+25),(x-3,y+21),(x-8,y+23)],c),rect(x-2,y+3,4,2,WHITE)]
def gun(c):return [rect(4,10,24,8,c),rect(7,8,14,3,WHITE),poly([(12,17),(18,17),(15,27),(10,26)],WHITE),rect(26,11,4,5,WHITE),line([(29,5),(29,8)],GOLD)]
def wind(c):return [line([(3,8),(23,8),(27,11),(24,14),(15,14)],c),line([(2,17),(19,17),(22,20),(19,23),(10,23)],WHITE),line([(4,27),(26,27)],c)]
def twin_swords(c):return sword(c)+[poly([(6,4),(9,5),(26,24),(23,26),(6,9)],c),line([(20,24),(25,20)],WHITE,3)]
def henshin(c):return [rect(2,12,28,9,WHITE),rect(9,7,14,19,c),rect(12,10,8,13,DARK),oval(13,12,6,8,GOLD),rect(3,14,3,5,DARK),rect(26,14,3,5,DARK)]
def trinity(c):return [poly([(16,3),(22,14),(16,18),(10,14)],GOLD),poly([(4,24),(9,14),(15,19),(15,28)],RED),poly([(28,24),(23,14),(17,19),(17,28)],BLUE),oval(13,14,7,7,WHITE)]
def cannon(c):return [rect(3,5,9,20,c),rect(20,5,9,20,c),rect(5,4,5,6,WHITE),rect(22,4,5,6,WHITE),line([(11,26),(16,29),(21,26)],GOLD)]
recipes={};manifest={}
def add(id,ops,variant=False):
 key=id.lower()+('_survive' if variant else '');recipes[key]=ops;row=manifest.setdefault(id,{'normal':id.lower()});
 if variant:row['survive']=key
for family,c in C.items():add(family.upper()+'_HENSHIN',henshin(c))
r=C['ryuki'];k=C['knight'];d=C['decade'];a=C['agito'];z=C['zolda'];b=C['kabuto']
for id,ops in {
 'RYUKI_STRIKE_VENT':flame(r)+[rect(8,17,3,8,WHITE),rect(13,17,3,8,WHITE),rect(18,17,3,8,WHITE)],
 'RYUKI_SWORD_VENT':sword(r)+[line([(3,14),(12,5)],r),line([(18,28),(29,16)],r)],
 'RYUKI_GUARD_VENT':shield(r)+[rect(2,10,3,14,WHITE),rect(27,10,3,14,WHITE)],
 'RYUKI_ADVENT':dragon(r), 'RYUKI_SURVIVE':flame(r)+[line([(7,8),(11,15),(16,7),(21,15),(26,8)],WHITE,2)],
 'RYUKI_SHOOT_VENT':missile(r,9,2)+missile(GOLD,23,1), 'RYUKI_FINAL_VENT':flame(r)+kick(r),
 'KNIGHT_SWORD_VENT':sword(k), 'KNIGHT_NASTY_VENT':bat(k)+[line([(3,24),(5,27),(9,28)],WHITE),line([(29,24),(27,27),(23,28)],WHITE)],
 'KNIGHT_TRICK_VENT':phantom('#4c689c',6,3)+phantom('#4c689c',26,3)+phantom(k,16,4),
 'KNIGHT_GUARD_VENT':shield(k)+bat(k), 'KNIGHT_SURVIVE':bat(k)+[line([(6,7),(16,2),(26,7)],GOLD,2)],
 'KNIGHT_BLAST_VENT':wind(k)+sword(k), 'KNIGHT_FINAL_VENT':bat('#406399')+kick(k),
 'DECADE_SLASH_BLAST':gun(d)+sword(d), 'DECADE_ILLUSION':card('#863c77',2,5,10,21)+card('#863c77',20,5,10,21)+card(d,10,2,12,27),
 'DECADE_CLOCK_UP':clock(d)+[rect(6,7,2,15,d),rect(25,7,2,15,d)],
 'DECADE_HEISEI_SPIRIT':trinity(d)+[rect(13,13,6,7,d)],
 'DECADE_COMPLETE':[rect(3,5,26,23,WHITE),rect(5,7,22,19,DARK),rect(7,9,5,5,d),rect(14,9,5,5,BLUE),rect(21,9,5,5,GOLD),rect(7,16,5,5,GOLD),rect(14,16,5,5,d),rect(21,16,5,5,RED),rect(8,24,17,2,d)],
 'DECADE_ULTIMATE_SUMMON':phantom(GOLD,7,3)+phantom('#afbcdc',25,3)+phantom(d,16,4),
 'DECADE_DIMENSION_KICK':card(d,21,2,9,14)+card(d,12,8,10,17)+card(d,2,14,12,17)+kick(d),
 'AGITO_GROUND_SMASH':[oval(3,16,26,12,a),rect(8,4,15,15,WHITE),line([(4,25),(10,22),(16,28),(20,22),(28,25)],DARK)],
 'AGITO_FLAME_SABER':flame(RED)+sword(a), 'AGITO_STORM_HALBERD':wind(BLUE)+[line([(6,29),(24,4)],WHITE,3),poly([(15,5),(28,3),(26,15),(23,8)],BLUE)],
 'AGITO_TRINITY':trinity(a), 'AGITO_RIDER_KICK':kick(a)+[line([(6,10),(4,15),(5,19)],a)], 'AGITO_SHINING':twin_swords(a)+[line([(16,1),(16,7)],GOLD),line([(13,4),(19,4)],GOLD)],
 'ZOLDA_MAGNA_SHOT':gun(z), 'ZOLDA_STRIKE_VENT':[rect(3,9,11,15,z),rect(18,9,11,15,z),rect(5,6,7,5,WHITE),rect(20,6,7,5,WHITE),line([(14,26),(16,29),(18,26)],GOLD)],
 'ZOLDA_SHOOT_VENT':missile(z), 'ZOLDA_GIGA_CANNONS':cannon(z), 'ZOLDA_GUARD_VENT':shield(z)+[rect(11,9,10,4,WHITE)], 'ZOLDA_FINAL_VENT':cannon(z)+[poly([(6,3),(10,8),(16,6),(22,8),(26,3),(24,14),(16,19),(8,14)],GOLD)],
 'KABUTO_CAST_OFF':[rect(10,9,12,15,b),line([(5,4),(9,8)],WHITE),line([(23,8),(27,4)],WHITE),line([(4,24),(8,21)],GOLD),line([(24,21),(28,24)],GOLD)],
 'KABUTO_CLOCK_UP':clock(b), 'KABUTO_RIDER_KICK':kick(b), 'KABUTO_KUNAI_MODE':gun(b)+[line([(3,25),(11,25),(15,29)],GOLD,2)],
 'KABUTO_KUNAI_STRIKE':sword(b)+[rect(8,20,8,3,b)], 'KABUTO_HYPER_FORM':bat(GOLD)+[poly([(16,2),(20,9),(16,17),(12,9)],b)]
}.items():add(id,ops)
for id,ops in {
 'RYUKI_STRIKE_VENT':missile(r,6,2)+missile(GOLD,16,0)+missile('#ed90ff',26,2),
 'RYUKI_SWORD_VENT':sword('#d98fff')+[line([(4,14),(12,5),(26,4)],'#d98fff',3),line([(18,28),(28,15)],r,3)],
 'RYUKI_FINAL_VENT':dragon(GOLD)+[line([(3,25),(29,25)],r,3),line([(2,29),(25,29)],WHITE,2)],
 'KNIGHT_SWORD_VENT':twin_swords(k), 'KNIGHT_FINAL_VENT':bat(k)+[line([(2,25),(21,25)],WHITE,3),line([(6,29),(28,29)],k,2)]
}.items():add(id,ops,True)
for key,ops in recipes.items():
 im=Image.new('RGBA',(32,32));draw=ImageDraw.Draw(im)
 for op,coords,color,*rest in ops:
  if op=='rect':draw.rectangle(coords,fill=color)
  elif op=='oval':draw.ellipse(coords,fill=color)
  elif op=='poly':draw.polygon(coords,fill=color)
  else:draw.line(coords,fill=color,width=rest[0] if rest else 2,joint='curve')
 target=OUT/'textures/gui/skills'/f'{key}.png';target.parent.mkdir(parents=True,exist_ok=True);im.save(target)
(SOURCE/'rider-skill-icons.json').write_text(json.dumps({'size':32,'palette':C,'recipes':recipes},indent=2)+'\n')
(OUT/'rider-skill-icons.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Authored',len(recipes),'pixel icons for',len(manifest),'native skills')
