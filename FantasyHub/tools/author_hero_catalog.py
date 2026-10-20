#!/usr/bin/env python3
"""Declarative transcription of the user's six-class brief, with explicit balance policy."""
from pathlib import Path
import json

def phase(kind,scale,hits=1,start=0,interval=0):return dict(kind=kind,scale=scale,hits=hits,start=start,interval=interval)
def skill(key,en,vi,cooldown,cost,phases=(),**values):
    return dict(key=key,name=en,name_vi=vi,cooldown=cooldown,cost=cost,range=values.pop('range',10),radius=values.pop('radius',0),duration=values.pop('duration',0),recast=values.pop('recast',0),upkeep=values.pop('upkeep',0),cast_time=values.pop('cast_time',0),ultimate=values.pop('ultimate',False),phases=list(phases),effects=values)
P='PHYSICAL';M='MAGIC';TP='TRUE_PHYSICAL';TM='TRUE_MAGIC';HP='MAX_HEALTH'
heroes=[dict(id='SPIDER_MAN',name='Spider-Man',name_vi='Người Nhện',resource='STAMINA',resource_name='Stamina',resource_name_vi='Thể lực',maximum=100,suit='fiskheroes:spider_man',passives=['Brand New Reflexes','Wall Crawl'],regen_idle=8,regen_combat=4,abilities=[
 skill('WEB_BLITZ','Web Blitz','Lướt Tơ Trảm',8,15,[phase(P,1.5,3,0,.15)],range=8,max_targets=3,bleed=.15,bleed_duration=3,slow=.3,slow_duration=3),
 skill('WEB_SWING','Web Swing Acrobatics','Đu Tơ Nhào Lộn',12,20,[phase(P,2.2,start=.8)],range=12,radius=4,duration=.8,knockup=.6,control_immunity=1),
 skill('WEB_SHOOTER_BARRAGE','Web-Shooter Barrage','Mưa Tơ Áp Suất',10,25,[phase(P,.3,8,0,.2)],duration=1.5,slow_stack=.05,max_stacks=8,blindness=2.5),
 skill('COCOON_SLAM','Web Cocoon Slam','Trói Kén Quật Đất',15,30,[phase(P,.5),phase(P,2.8,start=-1)],radius=3,recast=3,stun=2),
 skill('AIR_COMBO','Air Combo Assault','Chuỗi Đòn Không Trung',18,35,[phase(P,.7,5,0,.36)],range=4,duration=1.8,air_lock=1.8),
 skill('WEB_SHIELD','Web Shield Counter','Kén Tơ Phản Đòn',20,20,duration=2,reduction=.9,invisibility=1,root=2,counter=1),
 skill('WORLD_WEB_TRAP','Brand New World Web-Trap','Đại Mạng Nhện',60,60,[phase(P,.8,6,3,.12),phase(TP,3,start=3.8)],radius=6,duration=4,root=3,blindness=3,knockback=5,ultimate=True)
]),dict(id='SORCERER_SUPREME',name='Sorcerer Supreme',name_vi='Phù Thủy Tối Thượng',resource='MANA',resource_name='Mana',resource_name_vi='Ma lực',maximum=500,suit='fiskheroes:doctor_strange',passives=['Cloak of Levitation','Astral Intuition'],regen_idle=25,regen_combat=12.5,abilities=[
 skill('ELDRITCH_WHIP','Eldritch Whip','Ruy-Băng Ma Thuật',6,40,[phase(M,1.8)],range=10,recast=2,pull=1,slow=.4,slow_duration=2),
 skill('TAO_MANDALAS','Tao Mandalas','Khiên Ma Trận',12,60,duration=3,reduction=.7,cone=120,reflect=.8),
 skill('MIRROR_DIMENSION','Mirror Dimension','Không Gian Gương',16,90,[phase(M,.4,3,0,1)],radius=5,duration=3,projectile_reflect=1,mobility_lock=1),
 skill('ASTRAL_PROJECTION','Astral Projection','Tách Hồn Thần Thức',25,80,duration=3.5,invulnerable=1,mana_refund=.15),
 skill('SLING_RING','Sling Ring Portal','Cổng Không Gian',20,100,[phase(M,1.5)],range=20,duration=6,stun=1.5),
 skill('IMAGES_OF_IKONN','Images of Ikonn','Bội Nhân Ma Thuật',22,110,[phase(M,2)],radius=6,clones=8,silence=2.5,root=2.5),
 skill('EYE_OF_AGAMOTTO','Eye of Agamotto & Crimson Bands','Mắt Agamotto',70,200,[phase(M,1,2,2.5,1),phase(TM,4,start=4.5)],radius=7.5,duration=5,freeze=2.5,knockup=.6,ultimate=True)
]),dict(id='IRON_MAN',name='Iron Man',name_vi='Người Sắt',resource='MANA',resource_name='Arc Power',resource_name_vi='Năng lượng Arc',maximum=100,suit='fiskheroes:iron_man',passives=['Thruster Flight','Arc Reactor Overdrive'],regen_idle=10,regen_combat=5,abilities=[
 skill('REPULSOR_BLAST','Repulsor Blast','Xung Năng Lòng Bàn Tay',3,10,[phase(P,1.2)],range=15,knockback=3,flight_recoil=2),
 skill('MICRO_MISSILES','Micro-Missile Barrage','Thảm Tên Lửa Mini',9,25,[phase(P,.25,12,0,.1)],radius=4,slow=.4,slow_duration=2.5),
 skill('TARGETING_LASERS','Smart-Targeting Lasers','Laser Ngắm Bắn',11,20,[phase(P,1.6),phase(HP,.05/3,3,1,1)],range=10,armor_reduction=.3,armor_duration=4),
 skill('THRUSTER_DASH','Thruster Dash & Repulsor Shield','Lướt Đẩy Và Khiên Xung',13,15,range=6,duration=3,cleanse_slow=1,shield=.15,reduction=.6),
 skill('UNIBEAM','Unibeam Assault','Pháo Lực Ngực',18,45,[phase(P,3.8,start=.8)],range=18,cast_time=.8,side_knockback=1),
 skill('HOUSE_PARTY','House Party Protocol','Giao Thức Giáp Hỗ Trợ',30,50,[phase(P,.5,8,1,1),phase(P,.5,8,1,1)],duration=8,drones=2,reduction=.1),
 skill('ORBITAL_STRIKE','Satellite Orbital Strike','Oanh Tạc Vệ Tinh',65,80,[phase(TP,4.5,start=2)],radius=5,duration=3,root=2,silence=2,shield_destroy=1,emp=3,ultimate=True)
]),dict(id='THOR',name='Thor',name_vi='Lôi Thần Thor',resource='MANA',resource_name='Lightning Force',resource_name_vi='Lôi lực',maximum=100,suit='external:thor',passives=['Asgardian Flight & Worthy Return','Lightning Armor'],regen_idle=4,regen_combat=0,abilities=[
 skill('MJOLNIR_THROW','Stormbreaker Throw','Ném Rìu Stormbreaker',7,15,[phase(P,1.6),phase(P,.7,start=.7)],range=14,radius=3,stun=1,return_slow=.3),
 skill('THUNDEROUS_SLAM','Thunderous Leap & Slam','Nhảy Vọt Dậm Sét',12,20,[phase(P,2.4,start=.8)],range=10,radius=4,knockup=.6),
 skill('LIGHTNING_SPIN','Lightning Spin','Lốc Xoáy Sét',10,15,[phase(P,.4,4,0,.5)],duration=2,projectile_block=1,speed=.5),
 skill('STORMBREAKER','Stormbreaker Strike','Nhát Cắt Bão Sét',14,25,[phase(P,2.2),phase(P,.3,3,1,1)],armor_penetration=.5,zone_duration=3),
 skill('BIFROST','Bifrost Summon','Cầu Vồng Bifrost',22,30,[phase(M,2)],range=12,radius=4,cleanse=1,shield=.2,knockback=4),
 skill('GOD_OF_THUNDER','God of Thunder Mode','Bộc Phát Sấm Sét',35,50,duration=10,damage_bonus=.3,attack_speed=.25,chain_scale=.5,chain_targets=3,throw_cooldown_reduction=.4),
 skill('BRING_ME_THANOS','Bring Me Thanos!','Sấm Sét Tối Thượng',65,80,[phase(TP,5,start=2),phase(P,.5,4,2.2,.5)],radius=6,duration=4,silence=2,slow=.7,ultimate=True)
]),dict(id='FLASH',name='The Flash',name_vi='Tia Chớp',resource='MANA',resource_name='Speed Force',resource_name_vi='Speed Force',maximum=100,suit='fiskheroes:the_flash',passives=['Speed Force Levels & Regeneration','Arrow & Projectile Catching'],regen_idle=4,regen_combat=0,abilities=[
 skill('SUPER_SPEED','Super-Speed Toggle','Tốc Độ Siêu Việt',1,0,upkeep=2,speed=1.5,jump=3),
 skill('SLOW_MOTION','Slow-Motion Vision','Tầm Nhìn Chậm',15,20,radius=15,duration=3,slow=.9,mining_fatigue=4),
 skill('LIGHTNING_THROW','Lightning Throw','Phóng Tia Sét',8,15,[phase(P,1.7),phase(P,1.02,2,start=.1)],range=12,stun=1.5,chain_targets=2),
 skill('WIND_FUNNEL','Wind Funnel','Lốc Xoáy Chân Không',12,25,[phase(P,.3,6,0,.3)],radius=5,duration=1.8,pull=1,knockup=.6),
 skill('MOLECULAR_PHASING','Molecular Phasing','Xuyên Thể Phân Tử',18,30,duration=2,phase=1,physical_immunity=1),
 skill('SPEED_SIPHON','Speed Siphon','Hút Tốc Độ',20,25,duration=5,cleanse=1,speed_steal=.3),
 skill('INFINITE_MASS_PUNCH','Infinite Mass Punch','Cú Đấm Khối Lượng Vô Hạn',60,70,[phase(TP,5,start=2.5)],radius=7.5,duration=3,slow=.8,freeze=1,knockback=5,ultimate=True)
]),dict(id='BATMAN',name='Batman',name_vi='Người Dơi',resource='STAMINA',resource_name='Stamina',resource_name_vi='Thể lực',maximum=100,suit='fiskheroes:batman_dceu',passives=['Freeflow Combat & Counter Instinct','Glider Cape & Smoke Escape'],regen_idle=8,regen_combat=4,abilities=[
 skill('BATARANG','Batarang Throw','Phi Batarang',5,10,[phase(P,.4,3,0,.1),phase(P,1.5,start=-1)],range=10,recast=2,radius=2.5,slow=.3,interrupt=1),
 skill('BATCLAW','Batclaw Pull & Slam','Móc Dơi Quật Ngược',9,15,[phase(P,1.2)],range=12,pull=1,stun=1,armor_reduction=.3,armor_duration=3),
 skill('SMOKE_TAKEDOWN','Smoke Bomb & Silent Takedown','Bom Khói Hạ Gục',16,20,radius=4,duration=3,blindness=3,silence=3,invisibility=3,critical_scale=2.5),
 skill('DETECTIVE_VISION','Detective Vision','Tầm Nhìn Thám Tử',20,25,radius=20,duration=6,glowing=6,vulnerability=.2),
 skill('DISRUPTOR','Disruptor Jammer','Thiết Bị Phá Sóng',18,20,range=10,silence=2.5,shield_destroy=1),
 skill('CAPE_COMBO','Cape Sweep & Stun Combo','Áo Choàng Liên Hoàn',12,25,[phase(P,.5,4,.3,.2)],range=4,cone=180,stun=1.5,knockup=.6),
 skill('BATWING_STRIKE','Batwing Orbital Strike','Oanh Tạc Phi Thuyền Đêm',60,50,[phase(TP,4.2,start=2)],radius=5,duration=3,slow=.7,silence=2,stun=2,knockback=5,ultimate=True)
])]
passive_values={
'SPIDER_MAN':dict(evasion=.2,instinct_max=5,instinct_duration=6,instinct_speed=.05,instinct_critical=.04,wall_range=.8,wall_rise=.15),
'SORCERER_SUPREME':dict(emergency_threshold=.3,emergency_shield=.2,emergency_duration=5,emergency_cooldown=60,emergency_knockback=4,mystic_max=5,mystic_cooldown_reduction=.3,mystic_burst=1),
'IRON_MAN':dict(flight_upkeep=1.5,heat_per_skill=15,heat_max=100,overdrive_duration=6,overdrive_speed=.25,overdrive_cooldown_reduction=.5,repulsor_attack=.6,repulsor_radius=2),
'THOR':dict(static_max=5,static_armor=.03,static_lightning=.04,static_counter=.3,force_per_hit=8,force_hit_cooldown=.5,force_per_block=4,return_damage=.7,return_slow=.3),
'FLASH':dict(speed_max=5,speed_per_level=.15,punch_per_level=.1,health_regen_multiplier=4,projectile_catch_level=4,projectile_catch_radius=3,moving_gain=8,speed_level_seconds=1.5,movement_cap_blocks_second=12),
'BATMAN':dict(combo_max=10,combo_attack_speed=.03,combo_critical_damage=.05,combo_expiry=5,parry=.15,parry_stun=.5,escape_threshold=.25,escape_duration=2.5,escape_speed=.5,escape_cooldown=60)
}
for h in heroes:
 h['passive_values']=passive_values[h['id']]
 if h['id']=='THOR':h['suit']='jmctheroes:thor_infinity'
 for i,s in enumerate(h['abilities']):s.update(id=h['id']+'_'+s['key'],slot=i+1)
data=dict(schema=1,heroes=heroes,balance=dict(pvp_multiplier=.65,regular_cast_hp_cap=.25,ultimate_cast_hp_cap=.4,ultimate_attack_budget=3,pvp_control_cap_ticks=30,control_window_ticks=200,pvp_silence_cap_ticks=40,pvp_slow_cap=.65,max_targets=12,ultimate_max_targets=20,cooldown_reduction_cap=.5,max_displacement=5,friendly_fire=False),assumptions=['Area NxN means square side N; radial range is N/2 for matching circular visual bounds.','Mirror Dimension damages once per second for three ticks; Agamotto bands run two seconds after 2.5s freeze.','Thor final storm has four 0.5x bolts over two seconds by default.','Flash funnel lasts 1.8 seconds with six 0.3s damage pulses.','Iron Man laser burn is 5% max HP total spread over three seconds.','Batarang recast window is two seconds; all recasts share the first activation budget.','Independent drone damage is two drones, each 0.5x per second for eight seconds.'])
if __name__=='__main__':
 out=Path(__file__).resolve().parents[1]/'core/src/main/resources/default/heroes.json';out.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n');print(len(heroes),'classes;',sum(len(h['abilities']) for h in heroes),'actives')
