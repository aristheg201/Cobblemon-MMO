"""Distinct temporary projections traced from official forms 585,562,291; never wearable model shifts."""
from rider_reference_models import armor_skeleton,undersuit,box,plate,bevel_plate
from refine_card_riders import hull,mirrored,beam

def base():
    p=armor_skeleton();undersuit(p)
    return p

def kuuga():
    p=base()
    p['head']=[hull('ultimate_black_helmet',[(-8,6.9,7.4,1),(-6.5,8.1,8.2,.8),(-1.4,7.9,7.6,.9),(.6,5.9,6.3,.8)],'carbon'),bevel_plate('ultimate_gold_nose',[[-.45,-6.8],[.45,-6.8],[.4,-2.2],[0,-1.8],[-.4,-2.2]],-4.8,.5,'gold',.06),bevel_plate('ultimate_silver_jaw',[[-2.6,-2.1],[0,-1.7],[2.6,-2.1],[2,.15],[0,.6],[-2,.15]],-4.6,.5,'chrome',.1)]
    for sign in [-1,1]:
        p['head'] += [bevel_plate('ultimate_red_eye_'+str(sign),mirrored([[.6,-6.6],[2.4,-7],[3.8,-5.9],[3.7,-3.3],[2.2,-2.2],[.6,-2.6]],sign),-4.7,.5,'lens_red',.07),bevel_plate('ultimate_outer_gold_horn_'+str(sign),mirrored([[.25,-6.5],[1.5,-8.5],[3.3,-10.6],[4.1,-14],[4.5,-11.1],[3.8,-8],[1.3,-6.5]],sign),-4.7,.7,'gold',.07),plate('ultimate_inner_gold_horn_'+str(sign),mirrored([[.4,-8.3],[1.1,-10.8],[1.4,-12],[2,-9.3],[1.5,-7.4]],sign),-4.45,.6,'gold')]
        xy=mirrored([[.2,.8],[3.3,.3],[4.1,2.7],[3.5,5],[.3,4.8]],sign)
        p['body'] += [bevel_plate('ultimate_gold_pectoral_outline_'+str(sign),xy,-3.05,.8,'gold',.07),bevel_plate('ultimate_black_pectoral_'+str(sign),[[x*.94,y*.91+.16] for x,y in xy],-3.2,.55,'carbon',.09)]
        for row in range(2):
            p['body'] += [bevel_plate('ultimate_gold_abdomen_'+str(sign)+'_'+str(row),mirrored([[.1,5.4+row*1.65],[3,5+row*1.65],[2.4,6.8+row*1.65],[.2,7+row*1.65]],sign),-2.65,.5,'gold',.08),bevel_plate('ultimate_black_abdomen_'+str(sign)+'_'+str(row),mirrored([[.3,5.65+row*1.65],[2.6,5.3+row*1.65],[2.15,6.55+row*1.65],[.3,6.7+row*1.65]],sign),-2.8,.35,'carbon',.06)]
    for side in ['left','right']:
        sign=1 if side=='left' else -1;center=1 if sign==1 else -1
        p[side+'_arm'] += [bevel_plate('ultimate_pointed_pauldron',[[center-2.3,-1.7],[center+2.3,-1.7],[center+sign*4,-4.9],[center+sign*3.5,.9],[center-2,1.2]],-2.7,.8,'carbon',.08),box('ultimate_gold_cuff',[center-2,7.8,-2.5],[4,.4,5],'gold'),plate('ultimate_blade_vambrace',[[center-1.8,5.2],[center+sign*3.5,3.8],[center+sign*4.4,1.5],[center+sign*3.3,7.9],[center-1.6,8.3]],-2.6,.7,'carbon')]
        p[side+'_leg'] += [bevel_plate('ultimate_black_knee',[[-1.9,4.5],[1.9,4.5],[1.6,6.7],[0,7.1],[-1.6,6.7]],-2.55,.5,'carbon',.07),box('ultimate_gold_ankle',[-2,10.2,-2.3],[4,.4,4.6],'gold')]
    p['body'] += [box('ultimate_golden_arcle',[-2.2,9,-3.15],[4.4,1.9,.9],'gold'),box('ultimate_black_arcle_core',[-.9,9.2,-3.4],[1.8,1.5,.3],'black'),box('ultimate_rear_gold_belt',[-4,9.5,2.1],[8,.55,.4],'gold')]
    return p

def blade():
    p=base()
    p['head']=[hull('king_silver_helmet',[(-8,6.3,7.4,.9),(-5.8,8,8,.8),(-1,7.2,7.2,.8)],'steel'),plate('king_gold_median_blade',[[-.9,-6],[0,-14.8],[.9,-6],[.7,-1.2],[-.7,-1.2]],-4.7,.6,'gold'),bevel_plate('king_gold_jaw',[[-2.5,-3.7],[2.5,-3.7],[1.9,-.3],[0,.5],[-1.9,-.3]],-4.7,.55,'gold',.08)]
    for sign in [-1,1]:
        p['head'] += [bevel_plate('king_red_round_eye_'+str(sign),mirrored([[.7,-6.7],[2,-7],[3,-6],[3,-4.1],[1.9,-3.6],[.7,-4.1]],sign),-4.9,.45,'lens_red',.08),plate('king_silver_side_lance_'+str(sign),mirrored([[2.7,-3],[3.4,-8],[3.7,-11.8],[4.1,-9],[3.9,-1]],sign),-4.4,.65,'knight_metal')]
        p['body'] += [bevel_plate('king_gold_upper_cuirass_'+str(sign),mirrored([[.1,1],[3.9,.3],[4.3,4.7],[2.7,6],[.15,4.9]],sign),-3.1,.9,'gold',.09),box('king_silver_lower_rib_'+str(sign),[sign*2.5-1,6,-2.8],[2.1,1.1,.65],'steel')]
    p['body'] += [bevel_plate('king_spade_chest_frame',[[0,2.3],[3.1,5.8],[2.1,7.1],[-2.1,7.1],[-3.1,5.8]],-4,.4,'gold',.04),plate('king_blue_spade_emblem',[[0,2.8],[2.6,5.7],[1.8,6.7],[-1.8,6.7],[-2.6,5.7]],-4.2,.25,'navy'),box('king_bladedriver',[-2.6,9,-3.1],[5.2,2.1,.9],'chrome'),plate('king_driver_diamond',[[0,9.15],[1.1,10],[0,10.95],[-1.1,10]],-4,.3,'gold')]
    for side in ['left','right']:
        center=1 if side=='left' else -1;sign=1 if side=='left' else -1
        p[side+'_arm'] += [bevel_plate('king_tall_gold_pauldron',[[center-2.6,-1.4],[center+2.6,-1.4],[center+sign*3.1,-4.2],[center+sign*4,.7],[center-2.2,1.2]],-2.8,.9,'gold',.08),box('king_blue_shoulder_grille',[center-1.8,-.7,-3.1],[3.6,.75,.2],'navy')]
        for i in range(3):p[side+'_arm'].append(box('king_gold_vambrace_band_'+str(i),[center-2,5+i*1.2,-2.5],[4,.65,5],'gold'))
        for i in [4.4,7.3]:p[side+'_leg'].append(bevel_plate('king_engraved_gold_guard_'+str(i),[[-1.9,i],[1.9,i],[1.7,i+2.2],[0,i+2.6],[-1.7,i+2.2]],-2.8,.6,'gold',.12))
        p[side+'_leg'].append(box('king_silver_boot',[-2.1,10.2,-3.1],[4.2,1.8,5.3],'steel'))
    # King's long Rouzer is part of the projection only.
    p['right_arm'] += [box('king_rouzer_grip',[-1.55,8.7,-.5],[1.1,5,1.1],'carbon'),bevel_plate('king_rouzer_gold_guard',[[-4,13],[-1,11],[2,13],[1,15],[-3,15]],-1.1,.8,'gold',.06),plate('king_rouzer_silver_blade',[[-2.4,14],[.4,14],[.1,34],[-1,38],[-2.1,34]],-1,.65,'steel'),box('king_rouzer_gold_spine',[-1.3,15,-1.2],[.6,20,.25],'gold')]
    return p

def kiva():
    p=base()
    p['head']=[hull('emperor_black_helmet',[(-8,6.5,7.3,.8),(-5.8,8,8.3,.9),(-1.2,7.4,7.4,.9)],'carbon'),plate('emperor_fanged_black_jaw',[[-2.6,-2.4],[-1,-1.8],[0,-2.8],[1,-1.8],[2.6,-2.4],[1.6,.5],[-1.6,.5]],-4.8,.4,'black')]
    for sign in [-1,1]:
        p['head'] += [bevel_plate('emperor_red_bat_eye_'+str(sign),mirrored([[.3,-6.8],[2.2,-7.4],[3.5,-9.4],[3.6,-5],[2.2,-2.5],[1,-1.9],[1.5,-3.5],[.4,-3]],sign),-4.7,.6,'lens_red',.06),plate('emperor_silver_eye_edge_'+str(sign),mirrored([[3.1,-8],[3.7,-10.8],[4,-7.7],[3.8,-3.1],[2.8,-1.1],[1.8,-.7],[2.7,-2.2]],sign),-4.65,.65,'chrome'),bevel_plate('emperor_gold_crescent_'+str(sign),mirrored([[.05,-6.4],[1.4,-7.1],[2.3,-9.2],[2.6,-12.1],[1.5,-10.9],[1.2,-8.4],[.1,-7.6]],sign),-5,.65,'gold',.06)]
        p['body'] += [bevel_plate('emperor_gold_flank_'+str(sign),mirrored([[2.4,.8],[4.1,2],[3.6,7.8],[2.4,8.8],[1.8,5]],sign),-2.9,.8,'gold',.09),bevel_plate('emperor_red_bat_cuirass_'+str(sign),mirrored([[0,1.5],[3.2,.2],[4.2,3],[2.6,3.7],[2.4,6.5],[.2,8]],sign),-3.5,.75,'red',.07)]
        # Real tapered cape, with its own sway bone. It remains behind the projection.
        cape=plate('emperor_cape_'+str(sign),mirrored([[.1,.6],[4.2,.3],[5.5,14],[7.5,19.5],[1.8,18.6]],sign),2.6,.25,'red')
        cape['animation']={'bone':'cape_'+str(sign),'pivot':[0,1,2.6],'axis':'y','amplitude':sign*6,'period':1.7,'phase':0};p['body'].append(cape)
    for i in range(3):p['body'].append(bevel_plate('emperor_green_chest_gem_'+str(i),[[-.45,2.5+i*1.8],[0,2.15+i*1.8],[.45,2.5+i*1.8],[0,3.15+i*1.8]],-4.35,.3,'lens_green',.15))
    for side in ['left','right']:
        center=1 if side=='left' else -1;sign=1 if side=='left' else -1
        p[side+'_arm'] += [bevel_plate('emperor_gold_bat_pauldron',[[center-2.4,-1.9],[center+2.4,-1.9],[center+sign*3.2,-4.5],[center+sign*3.8,-1],[center+2,1],[center-2,1]],-2.8,.9,'gold',.09),box('emperor_golden_vambrace',[center-2,5,-2.4],[4,3.3,4.8],'gold'),box('emperor_silver_wrist',[center-2.1,8.2,-2.5],[4.2,.75,5],'chrome')]
        p[side+'_leg'] += [bevel_plate('emperor_gold_thigh',[[-1.9,.6],[1.9,.6],[1.7,4.5],[0,5.1],[-1.7,4.5]],-2.5,.55,'gold',.08),bevel_plate('emperor_gold_shin',[[-1.85,6],[1.85,6],[1.5,10.6],[-1.5,10.6]],-2.7,.6,'gold',.09),box('emperor_silver_toecap',[-2.1,10.6,-3.5],[4.2,1.4,5.7],'chrome')]
    p['body'] += [box('emperor_red_belt',[-4.2,9.1,-2.8],[8.4,1.4,.6],'red'),plate('emperor_kivat_gold_wings',[[-2.9,9],[0,9.6],[2.9,9],[2.2,10.6],[0,11],[-2.2,10.6]],-3.9,.6,'gold'),box('emperor_kivat_body',[-.6,9.3,-4],[1.2,1.5,.45],'steel')]
    return p

def extend(data):
    data['forms'].update({'kuuga_ultimate':kuuga(),'blade_king':blade(),'kiva_emperor':kiva()})
    data['references']['ultimate_projection_sources']=['https://www.kamen-rider-official.com/zukan/forms/'+str(i) for i in [585,562,291]]
