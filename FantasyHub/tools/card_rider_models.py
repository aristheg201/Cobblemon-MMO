"""Ryuki/Knight/Decade: original bone-local models based on the supplied eighteen references.

Survive and Complete deliberately retain their base armor silhouettes. Alternate weapons,
contract monsters and card projections carry the changed state. No photographs become textures.
"""
import math
from rider_reference_models import box,plate,bevel_plate,side_plate,armor_skeleton,undersuit

def b(name,low,high,material):
    return {'name':name,'from':low,'to':high,'material':material}

MATERIALS={'magenta':[178,22,113],'magenta_edge':[230,54,155],'navy':[18,40,68],'navy_edge':[37,69,102], 'dragon_red':[171,28,31],'dragon_edge':[220,49,38]}

def append_belt(p,name,items):
    for part in items[name]:
        a,c=part['from'],part['to'];s=.57
        p['body'].append(box('belt_'+part['name'],[(a[0]-8)*s,10.3-(c[1]-8)*s,-2.9+(a[2]-8)*s],[(c[i]-a[i])*s for i in range(3)],part['material']))
    p['body'] += [box('waist_rear_strap',[-4.3,9.6,2.1],[8.6,1.6,.35],'carbon'),box('waist_rear_clasp',[-1.25,9.4,2.4],[2.5,1.9,.4],'steel')]
    for sign in [-1,1]:p['body'].append(box('waist_side_strap_'+str(sign),[sign*4.15-.17,9.6,-2.2],[.34,1.6,4.6],'steel'))

def belt(deck_color,emblem):
    p=[b('silver_mount',[1,5,7],[15,11,9],'steel'),b('black_strap_left',[-1,6,7],[2,10,9],'carbon'),b('black_strap_right',[14,6,7],[17,10,9],'carbon'),b('card_deck_shadow',[4.2,4.5,6.2],[11.8,11.5,7.1],'black'),b('card_deck_frame',[4.5,4.8,5.8],[11.5,11.2,6.4],'chrome'),b('colored_card_deck',[5.1,5.3,5.5],[10.9,10.7,5.9],deck_color),b('card_insert_slot',[5.5,10.1,5.3],[10.5,10.5,5.6],'black')]
    for x in [2,12.7]:
        p += [b('belt_buckle_hinge_'+str(x),[x,6.1,6.4],[x+1.3,9.9,7.2],'chrome'),b('belt_red_latch_'+str(x),[x+.25,6.7,6.1],[x+1.05,7.5,6.5],'red')]
    if emblem=='dragon':
        p += [b('dragon_emblem_head',[7.3,7.6,5.1],[9.2,9,5.6],'gold'),b('dragon_emblem_neck',[6.8,6.3,5.1],[7.7,8.1,5.6],'gold'),b('dragon_emblem_tail',[7.2,5.9,5.1],[9.5,6.5,5.6],'gold'),b('dragon_emblem_wing',[5.7,8.1,5.1],[7.8,8.8,5.6],'gold')]
    elif emblem=='bat':
        for sign in [-1,1]:
            p += [b('bat_emblem_wing_'+str(sign),[8+sign*1.3-.8,8.1,5.1],[8+sign*1.3+.8,8.8,5.6],'gold'),b('bat_emblem_tip_'+str(sign),[8+sign*1.8-.3,7,5.1],[8+sign*1.8+.3,8.6,5.6],'gold')]
        p.append(b('bat_emblem_body',[7.6,6.5,5.1],[8.4,9.3,5.6],'gold'))
    return p

def equipment():
    ryuki=belt('dragon_red','dragon');knight=belt('navy','bat')
    decade=[b('driver_white_shell',[.7,4.8,6.7],[15.3,11.2,9],'white'),b('driver_black_border',[4,4.3,5.6],[12,11.7,7.3],'black'),b('driver_silver_ring',[4.5,4.7,5.1],[11.5,11.3,5.9],'steel'),b('driver_red_center',[5.8,6,4.8],[10.2,10,5.3],'red'),b('driver_black_center',[6.6,6.8,4.65],[9.4,9.2,5],'black')]
    for i,color in enumerate(['red','blue_edge','gold','green','magenta','navy','red_edge','chrome','blue']):
        angle=i*math.tau/9;x=8+math.cos(angle)*2.8;y=8+math.sin(angle)*2.8
        decade.append(b('driver_rider_symbol_'+str(i),[x-.3,y-.3,4.9],[x+.3,y+.3,5.15],color))
    saber=[b('drag_grip',[7,1,7],[9,8.5,9],'carbon'),b('drag_guard',[4.2,8.1,6.6],[11.8,9.4,9.4],'dragon_red'),b('dragon_head_socket',[6,9.3,6.3],[10,12,9.7],'dragon_red'),b('dragon_eye_left',[5.8,10.2,6.1],[6.4,11.1,6.5],'lens_green'),b('dragon_eye_right',[9.6,10.2,6.1],[10.2,11.1,6.5],'lens_green')]
    # Java legal rotated slabs form a thin, tapered cutting edge and red spine.
    for i in range(12):
        y=11.7+i*1.5;width=2.5*(1-i/14)
        saber += [b('drag_silver_edge_'+str(i),[8-width,y,7.55],[8+width*.35,y+1.55,8.45],'chrome'),b('drag_red_spine_'+str(i),[8+width*.35,y,7.45],[8+width*.6,y+1.55,8.55],'dragon_red')]
    dark=[b('dark_grip',[7,1,7],[9,8.5,9],'carbon'),b('dark_pommel',[6.5,.5,6.5],[9.5,1.7,9.5],'steel'),b('dark_guard_hub',[6.5,8,6.2],[9.5,10.8,9.8],'navy')]
    for sign in [-1,1]:
        dark += [b('dark_bat_guard_'+str(sign),[8+sign*3-2,8.5,6.6],[8+sign*3+2,10,9.4],'steel'),b('bat_guard_tip_'+str(sign),[8+sign*4-.7,9.1,6.7],[8+sign*4+.7,12.6,9.3],'navy')]
    for i in range(13):
        y=10.3+i*1.6;width=2.2*(1-i/14)
        dark += [b('dark_blade_'+str(i),[8-width,y,7.5],[8+width,y+1.65,8.5],'steel'),b('dark_blade_fuller_'+str(i),[7.75,y,7.4],[8.25,y+1.65,7.55],'navy')]
    gold=[{**x,'material':'gold' if x['material']=='steel' else 'navy_edge' if x['material']=='navy' else x['material']} for x in dark]
    # Ride Booker sword/gun share the same card storage, green lens and white shell.
    book=[b('booker_grip',[7,1.5,7],[9,8.8,9],'carbon'),b('booker_white_grip_rim',[6.5,1,6.5],[9.5,2.1,9.5],'white'),b('booker_black_reader',[4.5,7.5,6],[12,13,10],'carbon'),b('booker_magenta_side',[5,8,5.6],[11.5,12.5,6.2],'magenta'),b('booker_silver_card_rail',[4.5,12.2,5.5],[12,13,6.3],'chrome'),b('booker_green_lens',[10.7,9.6,5.3],[11.6,10.5,5.8],'lens_green')]
    sword=book.copy()
    for i in range(12):
        width=2*(1-i/13);y=12.8+i*1.55
        sword += [b('booker_blade_'+str(i),[8-width,y,7.6],[8+width,y+1.6,8.4],'chrome'),b('booker_blade_black_spine_'+str(i),[8+width-.3,y,7.5],[8+width,y+1.6,8.5],'carbon')]
    gun=book+[b('booker_gun_barrel',[-4,10.8,6.5],[5.5,13,9.5],'steel_dark'),b('booker_gun_white_shroud',[-3.5,10.5,6],[2.7,13.4,10],'white'),b('booker_muzzle',[-4.6,11,7],[-4,12.8,9],'black')]
    return {'ryuki_belt':ryuki,'knight_belt':knight,'decade_belt':decade,'drag_saber':saber,'dark_visor':dark,'dark_blade':gold,'ride_booker_sword':sword,'ride_booker_gun':gun}

def common_body(p,color,trim):
    for side in ['left','right']:
        left=side=='left';ax=-1.2 if left else -3.2
        p[side+'_arm'] += [box(side+'_bicep',[ax,1.8,-2.2],[4.4,2.8,4.4],color),box(side+'_forearm',[ax-.1,5.1,-2.35],[4.6,3.3,4.7],color),box(side+'_wrist_cuff',[ax-.15,8.4,-2.4],[4.7,.8,4.8],trim),box(side+'_glove',[ax,9.1,-2.2],[4.4,1.4,4.4],'carbon'),box(side+'_back_bracer_ridge',[ax+.3,5.5,2.3],[3.8,2,.3],trim)]
        p[side+'_leg'] += [box(side+'_thigh',[-2.14,0,-2.14],[4.28,4.4,4.28],color),bevel_plate(side+'_knee',[[-1.65,4.5],[1.65,4.5],[1.9,5.6],[1.4,6.6],[-1.4,6.6],[-1.9,5.6]],-2.7,.55,trim,.12),box(side+'_shin',[-2.15,6.6,-2.25],[4.3,3.9,4.5],color),box(side+'_boot_toe',[-2.22,10.5,-3.1],[4.44,1.7,5.35],'carbon'),box(side+'_boot_sole',[-2.3,11.9,-3.2],[4.6,.4,5.55],'steel_dark'),box(side+'_boot_heel_panel',[-1.8,10.5,2.15],[3.6,1.35,.3],trim),box(side+'_back_knee_hinge',[-1.6,4.8,2.2],[3.2,1.3,.25],'steel_dark')]
    p['body'] += [box('back_underplate',[-4.1,.6,2.05],[8.2,6.7,.5],color),box('rear_spine_channel',[-.4,1.1,2.6],[.8,5.9,.25],'carbon')]
    for i in range(3):p['body'].append(box('rear_lumbar_segment_'+str(i),[-3.5,7.3+i*.65,2.18],[7,.35,.35],color))

def ryuki(items):
    p=armor_skeleton();undersuit(p,'carbon');common_body(p,'dragon_red','chrome')
    p['head'] += [bevel_plate('silver_dragon_helmet',[[-4,-8.25],[4,-8.25],[4.45,-6.5],[4.3,-1.2],[2.7,.5],[-2.7,.5],[-4.3,-1.2],[-4.45,-6.5]],-4.45,.75,'steel',.06),box('red_helmet_crown',[-3.8,-8.6,-3.7],[7.6,.6,7.4],'dragon_red'),bevel_plate('dragon_forehead_crest',[[-.5,-8.7],[.5,-8.7],[1.2,-7],[.6,-6.3],[-.6,-6.3],[-1.2,-7]],-5,.45,'chrome',.1),box('forehead_red_sensor',[-.25,-7.8,-5.5],[.5,.7,.25],'lens_red'),box('black_visor_recess',[-3.75,-6.4,-5.25],[7.5,4.2,.3],'black'),plate('silver_jaw',[[-3,-1.9],[3,-1.9],[2.4,.5],[-2.4,.5]],-4.8,.5,'chrome')]
    for i in range(6):p['head'].append(box('horizontal_visor_rail_'+str(i),[-3.9,-6.5+i*.7,-5.6],[7.8,.27,.4],'chrome'))
    for sign in [-1,1]:
        p['head'] += [box('helmet_red_side_'+str(sign),[sign*4.1-.18,-7.5,-2.6],[.36,5.8,6.4],'dragon_red'),box('helmet_silver_ear_'+str(sign),[sign*4.35-.2,-3.4,-1],[.4,2.1,2],'chrome')]
    p['head'] += [box('rear_silver_helmet_plate',[-3.8,-7.8,4.1],[7.6,6.6,.5],'steel'),box('rear_helmet_red_stripe',[-.55,-8.1,4.6],[1.1,7,.2],'dragon_red'),box('rear_nape_vent',[-2.6,-1.2,4.5],[5.2,.6,.3],'carbon')]
    for side in ['left','right']:
        left=side=='left';x=-1.5 if left else -4.1
        p[side+'_arm'] += [box(side+'_square_silver_pauldron',[x,-2.8,-2.8],[5.6,3.1,5.6],'steel'),box(side+'_pauldron_red_inset',[x+.4,-2.4,-3.2],[4.8,1.8,.5],'dragon_red'),box(side+'_rear_pauldron_panel',[x+.4,-2.3,2.8],[4.8,1.8,.35],'chrome')]
    p['body'] += [plate('left_red_pectoral',[[-4.5,.2],[-.5,.8],[-.5,4.8],[-3.7,5.4],[-4.5,3.8]],-3.2,.95,'dragon_red'),plate('right_red_pectoral',[[4.5,.2],[.5,.8],[.5,4.8],[3.7,5.4],[4.5,3.8]],-3.2,.95,'dragon_red'),plate('silver_front_harness',[[-4.3,.2],[0,2.3],[4.3,.2],[4.3,1.3],[0,3.5],[-4.3,1.3]],-3.65,.5,'chrome'),plate('silver_lower_harness',[[-4.3,4],[0,5.4],[4.3,4],[3.8,5.4],[0,6.9],[-3.8,5.4]],-3.5,.5,'steel')]
    for i in range(4):p['body'].append(box('abdominal_scale_'+str(i),[-2.6,6.1+i*.72,-2.8],[5.2,.45,.55],'dragon_red'))
    for sign in [-1,1]:p['body'].append(plate('rear_silver_harness_'+str(sign),[[sign*.6,.7],[sign*4,.2],[sign*4,2.3],[sign*1,4.7]],2.7,.4,'steel'))
    append_belt(p,'ryuki_belt',items);return p

def knight(items):
    p=armor_skeleton();undersuit(p,'navy');common_body(p,'navy','steel')
    p['head'] += [box('knight_silver_crown',[-4.2,-8.4,-4.2],[8.4,8.5,8.4],'steel'),box('dark_upper_visor',[-3.7,-6.6,-4.5],[7.4,4.8,.35],'black'),bevel_plate('silver_chin',[[-3.5,-1.6],[-1.6,-.8],[0,-1.5],[1.6,-.8],[3.5,-1.6],[2.7,.5],[0,1.2],[-2.7,.5]],-4.65,.7,'chrome',.1),bevel_plate('gold_forehead_emblem',[[-.55,-7.2],[.55,-7.2],[.8,-6.4],[0,-5.6],[-.8,-6.4]],-4.8,.4,'gold',.15)]
    for i in range(9):p['head'].append(box('vertical_visor_rib_'+str(i),[-3.5+i*.83,-5.65,-4.92],[.3,4.1,.45],'chrome'))
    for sign in [-1,1]:
        poly=[[sign*2.6,-6.7],[sign*4.3,-7.8],[sign*4.9,-11.4],[sign*3.8,-9.7],[sign*3.2,-7.3]]
        p['head'].append(bevel_plate('bat_crown_horn_'+str(sign),poly,-4.25,.95,'chrome',.1))
        p['head'] += [box('rear_dark_shell_'+str(sign),[sign*2.2-1.6,-7.5,4.25],[3.2,5.9,.35],'navy'),box('silver_ear_hinge_'+str(sign),[sign*4.4-.2,-2.9,-.6],[.4,2.2,2.2],'steel_dark')]
    p['head'] += [box('rear_silver_midline',[-.55,-8.3,4.5],[1.1,7,.25],'chrome'),box('rear_nape_collar',[-3.6,-.6,4.35],[7.2,.65,.45],'steel')]
    p['body'] += [plate('silver_bat_chest',[[-4.5,.4],[-2.7,.1],[0,2],[2.7,.1],[4.5,.4],[3.7,5.2],[0,7.3],[-3.7,5.2]],-3.25,.8,'steel'),plate('navy_chest_center',[[-1.8,.7],[0,1.5],[1.8,.7],[1.2,4.2],[0,6.1],[-1.2,4.2]],-3.85,.3,'navy'),box('neck_dark_collar',[-2.8,-.7,-2.65],[5.6,1.5,5.3],'navy_edge')]
    for sign in [-1,1]:
        p['body'] += [plate('chest_navy_chevron_'+str(sign),[[sign*4.1,.6],[sign*3.4,.9],[sign*2.1,4.7],[sign*.25,6.5],[sign*.25,7],[sign*2.7,5.1]],-4.15,.22,'navy'),plate('rear_silver_bat_harness_'+str(sign),[[sign*.5,1],[sign*3.6,.4],[sign*4,4.7],[sign*.8,6.8]],2.7,.5,'steel')]
    for side in ['left','right']:
        left=side=='left';x=-1.6 if left else -4
        p[side+'_arm'] += [box(side+'_square_bat_pauldron',[x,-2.4,-2.85],[5.6,2.7,5.7],'steel'),plate(side+'_pauldron_bat_fin',[[x,-2.2],[x+2.7,-4.3],[x+5.6,-2.2],[x+4.7,.5],[x+1,.5]],-3.2,.65,'navy'),box(side+'_rear_pauldron_silver',[x+.3,-2,2.85],[5,1.7,.35],'steel')]
    append_belt(p,'knight_belt',items);return p

def decade(items):
    p=armor_skeleton();undersuit(p,'carbon');common_body(p,'carbon','white')
    p['head'] += [box('magenta_helmet',[-4.25,-8.5,-4.25],[8.5,8.5,8.5],'magenta'),box('rear_black_helmet',[-3.65,-7.9,4.3],[7.3,7.1,.35],'carbon'),box('rear_magenta_center',[-.5,-8.5,4.65],[1,8.2,.3],'magenta_edge'),bevel_plate('green_left_compound_eye',[[-3.9,-6.65],[-.65,-6.4],[-.5,-2.3],[-3.4,-2.2],[-4.2,-3.1]],-4.7,.65,'lens_green',.1),bevel_plate('green_right_compound_eye',[[3.9,-6.65],[.65,-6.4],[.5,-2.3],[3.4,-2.2],[4.2,-3.1]],-4.7,.65,'lens_green',.1),box('black_mouth_recess',[-2.8,-1.9,-4.65],[5.6,1.6,.4],'black'),box('silver_mouth_bar',[-2.7,-.8,-5],[5.4,.4,.3],'chrome'),box('forehead_yellow_jewel',[-.35,-8.2,-5.15],[.7,.6,.4],'lens_amber')]
    # Seven contiguous black barcode fins, not detached antenna rods.
    for i in range(7):
        x=-3.6+i*1.2;top=-9.7+(abs(i-3)*.4)
        p['head'] += [box('black_barcode_face_'+str(i),[x-.16,-8.3,-5.42],[.32,8.25,.32],'black'),box('barcode_crown_'+str(i),[x-.16,top,-4.6],[.32,-8.3-top,8.8],'black')]
    p['body'] += [box('magenta_left_chest',[-4.35,.2,-2.65],[1.7,8.4,5.3],'magenta'),box('magenta_right_chest',[2.65,.2,-2.65],[1.7,8.4,5.3],'magenta'),box('black_chest_plate',[-2.8,.6,-3.2],[5.6,6.8,.5],'carbon'),plate('white_cross_band',[[-4.4,4.6],[4.5,.3],[4.5,1.25],[-4.4,5.55]],-3.8,.35,'white'),plate('black_cross_band',[[-4.4,5],[4.5,.7],[4.5,1],[-4.4,5.3]],-4.2,.15,'black'),box('white_vertical_chest_band',[.65,.6,-3.6],[.7,5.6,.35],'white'),box('rear_white_midline',[-.35,.5,2.8],[.7,6,.25],'white')]
    for sign in [-1,1]:
        p['body'] += [box('rear_magenta_edge_'+str(sign),[sign*3.3-.7,.6,2.6],[1.4,7.8,.35],'magenta'),plate('rear_white_shoulder_band_'+str(sign),[[sign*.5,3.7],[sign*4,.6],[sign*4,1.4],[sign*.5,4.5]],2.95,.2,'white')]
    for side in ['left','right']:
        left=side=='left';x=-1.55 if left else -4.05;ax=-1.2 if left else -3.2
        p[side+'_arm'] += [box(side+'_square_magenta_pauldron',[x,-2.5,-2.7],[5.6,3.4,5.4],'magenta'),box(side+'_white_shoulder_trim',[x,-2.6,-3.05],[5.6,.65,.4],'white'),box(side+'_black_shoulder_band',[x,-1.6,-3.1],[5.6,.55,.45],'black'),box(side+'_rear_shoulder_white',[x,-2.35,2.75],[5.6,.7,.3],'white'),box(side+'_magenta_forearm',[ax-.1,5,-2.5],[4.6,2.9,.3],'magenta'),box(side+'_white_glove_shell',[ax-.05,8.5,-2.5],[4.5,1.4,.3],'white')]
        for i in [-1,1]:p[side+'_leg'].append(box(side+'_magenta_leg_line_'+str(i),[i*1.7-.3,.3,-2.35],[.6,9.8,.3],'magenta'))
    append_belt(p,'decade_belt',items);return p

def dragon(name,mechanical=False):
    p=armor_skeleton();body=p['body'];red='dragon_red';silver='chrome'
    # Long segmented serpentine body curls behind the rider; every segment has
    # belly plates and a dorsal crest, rather than a red untextured cylinder.
    for i in range(14):
        angle=i*.30;x=math.sin(angle)*12;y=-10+math.cos(angle)*5;z=7+i*1.5
        body += [box(name+'_segment_'+str(i),[x-2.1,y-2.1,z],[4.2,4.2,2.5],red),box(name+'_belly_'+str(i),[x-1.8,y+1.6,z-.1],[3.6,.7,2.7],silver),plate(name+'_dorsal_spine_'+str(i),[[x-1,y-1],[x,y-4],[x+1,y-1]],z+.2,1,'gold' if mechanical else silver)]
    body += [box('dragon_head',[-3.1,-13.2,2.5],[6.2,4.5,6.5],red),box('dragon_silver_jaw',[-2.8,-9.2,-.5],[5.6,1.1,8],'steel'),box('dragon_black_mouth',[-2.6,-10.2,-.7],[5.2,.8,1],'black'),box('dragon_nose',[-2.1,-12.1,-1.5],[4.2,2,5],red)]
    for sign in [-1,1]:
        body += [box('dragon_eye_'+str(sign),[sign*2.7-.2,-12.6,2.2],[.4,1,1.7],'lens_green'),plate('dragon_horn_'+str(sign),[[sign*1.8,-12],[sign*3,-14.5],[sign*4.8,-16.1],[sign*3.8,-12.6]],5.1,1,'gold'),box('dragon_foreclaw_'+str(sign),[sign*4-.7,-7,7],[1.4,3,1.4],'steel')]
        for j in range(3):body.append(box('dragon_tooth_'+str(sign)+'_'+str(j),[sign*2.4-.2,-10.6,j*1.4],[.4,1.1,.6],'chrome'))
    if mechanical:
        for sign in [-1,1]:body += [box('dragranzer_wing_'+str(sign),[sign*6-3,-10,9],[6,1,9],'gold'),box('dragranzer_cannon_'+str(sign),[sign*5-1,-11,0],[2,2,9],'steel')]
    return p

def darkwing():
    p=armor_skeleton();bdy=p['body']
    bdy += [box('bat_torso',[-2.5,-7,-1.5],[5,7,3],'navy'),box('bat_head',[-2,-11,-2],[4,4,4],'steel_dark'),box('bat_red_eyes',[-1.4,-10,-2.4],[2.8,.65,.4],'lens_red'),box('silver_bat_belly',[-1.6,-5,-2],[3.2,4,.5],'steel')]
    for sign in [-1,1]:
        xy=[[sign*1.7,-7],[sign*7,-11.5],[sign*16,-8],[sign*13,-2],[sign*10,-4],[sign*7,1],[sign*5,-2],[sign*2.5,2]]
        bdy += [bevel_plate('bat_membrane_'+str(sign),xy,-.6,.5,'navy',.04),plate('bat_wing_silver_leading_edge_'+str(sign),[[sign*1.5,-7.2],[sign*7,-12],[sign*16.3,-8.2],[sign*15.8,-7.6],[sign*7,-11.1],[sign*2,-6.6]],-.9,.45,'steel'),plate('bat_ear_'+str(sign),[[sign*.8,-10.5],[sign*2,-14],[sign*2.2,-10]],-.8,1,'navy')]
        for j in range(3):
            bdy.append(plate('bat_wing_rib_'+str(sign)+'_'+str(j),[[sign*7,-11],[sign*(13-j*3),(-2 if j==0 else 1 if j==1 else 2)],[sign*(12.7-j*3),(-2 if j==0 else 1 if j==1 else 2)],[sign*6.7,-11]],-1,.2,'steel_dark'))
    return p

def extend(data):
    data['materials'].update(MATERIALS);items=equipment();data['items'].update(items)
    data['forms'].update({'ryuki':ryuki(items),'knight':knight(items),'decade':decade(items),'dragreder':dragon('dragreder'),'dragranzer':dragon('dragranzer',True),'darkwing':darkwing()})
    from refine_card_riders import refine
    refine(data)
    data['references']['card_riders']={'archives':['Download.zip','Download2.zip'],'notes':'18 source photographs/game screenshots; no original model files. Base armor remains unchanged in Survive/Complete by user request.','ryuki':['oardefault.jpg','1792297107510.png','1792297128561.png'],'knight':['KR-Knight%26WingKnight.webp','Knight_Profile.webp','images (2).jpeg'],'decade':['1792296897858.png','1792297097386.png'],'monsters':['2015102194749.jpg','41mcTQZ+i1L.jpg','KRRy-Dragranzer.webp','images (1).jpeg','item_0000001578_07.jpg']}
