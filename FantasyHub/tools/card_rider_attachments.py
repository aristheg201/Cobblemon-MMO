"""Reference-specific equipment, 3D card frames and animation rigs. No photographs are textures."""
import copy, math
from rider_reference_models import box, plate, bevel_plate, armor_skeleton, octagonal_ring
from card_rider_models import append_belt,b
from refine_card_riders import beam,mirrored

def object_form(parts):
    p=armor_skeleton();p['body']=parts;return p

def card_parts():
    p=[box('holographic_card_face',[-5.4,-8.4,-.12],[10.8,16.8,.24],'card_decade')]
    for sign in [-1,1]:
        p += [box('silver_card_side_'+str(sign),[sign*5.65-.25,-8.8,-.2],[.5,17.6,.4],'chrome'),box('magenta_card_corner_'+str(sign),[-5.8,sign*8.65-.25,-.22],[11.6,.5,.44],'decade_magenta')]
    p += [box('card_rear_chip',[-1.6,-2.4,.13],[3.2,4.8,.12],'gold'),box('card_top_reader',[-3.5,-7.8,-.19],[7,.5,.12],'white')]
    return p

def shield():
    outline=[[-3.1,-10],[0,-10.8],[3.1,-10],[3.3,-5.5],[2.5,2],[2.2,7.5],[0,10.5],[-2.2,7.5],[-2.5,2],[-3.3,-5.5]]
    p=[bevel_plate('red_dragon_scale_shell',outline,-.9,1.9,'ryuki_red',.07),bevel_plate('silver_segmented_ventral_plate',[[-1.9,-9.6],[0,-10],[1.9,-9.6],[1.6,7.7],[0,9.5],[-1.6,7.7]],-1.15,.5,'ryuki_metal',.06)]
    for i in range(7):
        y=-7.7+i*2.25;p.append(plate('silver_scale_joint_'+str(i),[[-1.8,y],[0,y+.8],[1.8,y],[1.8,y+.14],[0,y+.97],[-1.8,y+.14]],-1.24,.15,'steel_dark'))
    for sign in [-1,1]:
        palm=[sign*3,-6.8,-1.6]
        p += [beam('red_shield_foreleg_'+str(sign),[sign*2.5,-.3,-.4],[sign*3,-4.6,-1.1],.85,1,'ryuki_red'),box('red_dragon_palm_'+str(sign),[sign*3-.85,-7.5,-1.7],[1.7,1.7,1.3],'ryuki_red')]
        for j in range(3):
            end=[sign*3+(j-1)*.75,-9.8+abs(j-1)*.5,-2]
            p += [beam('red_dragon_digit_'+str(sign)+'_'+str(j),palm,end,.48,.5,'ryuki_red'),beam('silver_shield_claw_'+str(sign)+'_'+str(j),end,[end[0],end[1]-.6,end[2]-.4],.3,.3,'chrome')]
    p += [box('rear_forearm_socket',[-1.6,-3.5,.9],[3.2,7,1],'carbon'),box('rear_grip_bar',[-1.3,-.3,2.1],[2.6,.65,.65],'steel')]
    return p

def dragon_head(claw=False):
    # Long silver swept brow and snout, red cheeks, open toothed jaw from official items 1086/1186.
    p=[box('red_dragon_crown',[-2.5,-2.1,-5],[5,2.4,8],'ryuki_red'),bevel_plate('silver_dragon_brow',[[-2.7,-2.1],[0,-3],[2.7,-2.1],[1.8,-.4],[-1.8,-.4]],-5.5,.8,'ryuki_metal',.08),box('red_dragon_snout',[-2,-.2,-9.2],[4,1.4,5],'ryuki_red'),box('black_card_receiver',[-2.2,.6,-3],[4.4,2.4,7],'carbon')]
    for sign in [-1,1]:
        p += [beam('silver_swept_horn_'+str(sign),[sign*1.4,-2,-2],[sign*3,-3.7,5],.65,.6,'chrome'),box('amber_dragon_eye_'+str(sign),[sign*2.25-.2,-1.7,-5.2],[.4,.7,2.3],'lens_amber'),beam('silver_dragon_cheek_'+str(sign),[sign*2.4,-.1,-4],[sign*3.8,-.5,2],1.1,.5,'chrome')]
        for i in range(5):
            p.append(box('silver_dragon_tooth_'+str(sign)+'_'+str(i),[sign*1.65-.18,1.1,-8.6+i*1.1],[.36,.9,.55],'chrome'))
    if claw:
        p += [beam('red_open_lower_jaw',[0,3.1,-2.2],[0,4.6,-8.6],3.6,.65,'ryuki_red')]
        for sign in [-1,1]:
            for i in range(5):p.append(box('lower_fang_'+str(sign)+'_'+str(i),[sign*1.45-.15,3.1+i*.17,-3.6-i*1.05],[.3,.85,.45],'chrome'))
    else:
        p += [box('visor_forearm_cuff',[-2.7,1.1,1.5],[5.4,2.8,2.5],'carbon'),box('visor_reader_slot',[-1.8,-2.3,.2],[3.6,.25,2.1],'black')]
        for i in range(5):p.append(box('visor_nose_radiator_'+str(i),[-1.3,-.4,-8+i*.58],[2.6,.18,.25],'chrome'))
    return p

def visor_z():
    p=[box('visor_z_black_receiver',[-3,-2.4,-2],[6,4.8,8],'carbon'),box('visor_z_red_lower_jaw',[-2.4,1,-10],[4.8,1.2,11],'ryuki_red'),box('visor_z_silver_snout',[-2.3,-1.1,-10],[4.6,2,8],'chrome'),box('visor_z_black_reader',[-2.5,-2.1,2],[5,3.2,5],'black')]
    for sign in [-1,1]:
        p += [beam('visor_z_gold_dorsal_crest_'+str(sign),[sign*2.7,-2,-5],[sign*2.5,-2.9,4],.6,.6,'gold'),box('visor_z_gold_jaw_hinge_'+str(sign),[sign*2.6-.25,1,-.5],[.5,1.8,1.8],'gold')]
        for i in range(7):p.append(box('visor_z_white_fang_'+str(sign)+'_'+str(i),[sign*2-.15,.8,-9.5+i*1.1],[.3,.65,.5],'white'))
    for i in range(6):p.append(box('visor_z_snout_grille_'+str(i),[-1.8,-1.3,-9+i*1.05],[3.6,.18,.5],'black'))
    return p

def ktouch():
    p=[box('ktouch_black_case',[-5.8,-4.4,-.4],[11.6,8.8,.8],'carbon'),box('ktouch_silver_upper_rail',[-5.2,-4.7,-.45],[10.4,.5,.9],'chrome'),box('ktouch_touchscreen',[-3.8,-3.9,-.57],[7.5,7.7,.12],'ktouch_screen'),box('ktouch_green_confirm',[4.2,.6,-.7],[1.2,.6,.2],'optic_green')]
    for sign in [-1,1]:
        for i in range(6):
            p.append(box('ktouch_magenta_key_'+str(sign)+'_'+str(i),[sign*4.8-.65,-3.9+i*1.35,-.65],[1.3,.65,.35],'decade_magenta'))
    p += [box('ktouch_reader_socket',[-5.8,-1.2,-.66],[1.6,2.5,.25],'steel_dark')]
    return p

def extend(data):
    data['materials'].update({'card_decade':[29,17,32],'ktouch_screen':[12,12,15]})
    data['forms'].update({'rider_card':object_form(card_parts()),'drag_shield':object_form(shield()),'drag_visor':object_form(dragon_head()),'drag_claw':object_form(dragon_head(True)),'drag_visor_z':object_form(visor_z()),'ktouch':object_form(ktouch())})
    # A proper faceted circular lens in the white Decadriver, with black side handles and RGB buttons.
    belt=[b('driver_white_shell',[2.2,4.4,6.4],[13.8,11.6,8.8],'white'),b('driver_black_crossbar',[-1,7.3,5.9],[17,8.7,6.5],'black')]
    belt+=octagonal_ring('driver_silver_circular_lens',8,8,5.45,2.6,.8,'chrome')
    belt+=octagonal_ring('driver_black_lens_rim',8,8,5.23,1.9,.4,'steel_dark')
    # Pixel stepped black glass disk fills the hollow rim without a square central badge.
    for i in range(7):
        y=6.3+i*.5;half=math.sqrt(max(0,1.7**2-(y+.25-8)**2))
        belt.append(b('driver_dark_lens_scanline_'+str(i),[8-half,y,5.4],[8+half,y+.5,5.65],'black'))
    for sign in [-1,1]:
        x=8+sign*7
        belt += [b('driver_side_handle_top_'+str(sign),[x-1.5,5.2,6.5],[x+1.5,6.1,8.5],'steel_dark'),b('driver_side_handle_bottom_'+str(sign),[x-1.5,9.9,6.5],[x+1.5,10.8,8.5],'steel_dark'),b('driver_side_handle_outer_'+str(sign),[x+sign*1.1-.4,6,6.5],[x+sign*1.1+.4,10,8.5],'steel_dark')]
        for i,color in enumerate(['optic_green','lens_red','blue_edge']):belt.append(b('driver_rgb_switch_'+str(sign)+'_'+str(i),[x-1.2+i*.85,7.5,5.7],[x-.55+i*.85,8.2,6.2],color))
    belt.append(b('driver_green_status',[7.7,4.85,5.7],[8.3,5.45,6.1],'optic_green'))
    data['items']['decade_belt']=belt
    p=data['forms']['decade'];p['body']=[x for x in p['body'] if not x['name'].startswith(('belt_','waist_'))];append_belt(p,'decade_belt',data['items'])
    # Ryuki's red knee armor surrounds the small silver hinge.
    for side in ['left','right']:
        data['forms']['ryuki'][side+'_leg'].insert(0,bevel_plate(side+'_red_knee_shell',[[-2.15,4.2],[2.15,4.2],[2.25,6.2],[0,7.25],[-2.25,6.2]],-2.85,.55,'ryuki_red',.06))
    # Independent Darkwing wings, ears and silver/turquoise shoulder mechanisms.
    for part in data['forms']['darkwing']['body']:
        n=part['name']
        if any(k in n for k in ['membrane','wing_humerus','leading_edge','finger_spar']):
            sign=-1 if '_-1' in n else 1
            part['animation']={'bone':'wing_'+str(sign),'pivot':[sign*2.4,-7.1,.3],'axis':'z','amplitude':sign*18,'period':1.1,'phase':0}
    for sign in [-1,1]:
        data['forms']['darkwing']['body'] += [box('darkwing_turquoise_shoulder_'+str(sign),[sign*2.5-.6,-7.8,-1],[1.2,1.2,.5],'blue_edge'),box('darkwing_shoulder_silver_rim_'+str(sign),[sign*2.5-.8,-8,-.6],[1.6,1.6,.5],'chrome')]
    # Serpentine segments have an editable, modest travelling motion instead of static props.
    for name in ['dragreder','dragranzer']:
        for part in data['forms'][name]['body']:
            if '_armored_segment_' in part['name']:
                index=int(part['name'].split('_armored_segment_')[1].split('_')[0])
                vertices=part['vertices'];pivot=[sum(v[i] for v in vertices)/len(vertices) for i in range(3)]
                part['animation']={'bone':'spine_'+str(index),'pivot':pivot,'axis':'y','amplitude':4,'period':1.6,'phase':index*.1}
    # Dragranzer has a silver/gold mechanical head and a wheeled armored chassis,
    # unlike Dragreder's red dragon face. Reference: both product photos and KRRy-Dragranzer.
    mechanical=data['forms']['dragranzer']['body']
    for part in mechanical:
        if any(k in part['name'] for k in ['dragon_skull','angular_dragon_snout','dragon_nose_cap','lower_silver_jaw','dragon_cheek_spike','silver_swept_horn','swept_crown_root','swept_crown_tip']):
            part['material']='gold' if 'horn' in part['name'] or 'crown' in part['name'] else 'silver_white'
    for sign in [-1,1]:
        mechanical += [beam('dragranzer_long_gold_crown_'+str(sign),[-5+sign*1.5,-25,-9],[-5+sign*2.5,-33.5,-6],.8,.65,'gold'),beam('dragranzer_silver_jaw_rail_'+str(sign),[-5+sign*2,-23,-16.1],[-5+sign*2,-19.5,-12.8],.7,.55,'silver_white'),box('dragranzer_red_engine_pod_'+str(sign),[sign*4.5-1.4,-13,3],[2.8,5.5,5.5],'ryuki_red'),beam('dragranzer_gold_engine_trim_'+str(sign),[sign*4.5,-13.2,2.5],[sign*4.5,-9,7.5],.5,.5,'gold')]
        for i in range(5):mechanical.append(box('dragranzer_silver_face_cage_'+str(sign)+'_'+str(i),[-5+sign*1.6-.15,-23+i*.55,-15.8],[.3,.25,3],'chrome'))
        for i in range(12):
            angle_a=i*math.tau/12;angle_b=(i+1)*math.tau/12
            center=[sign*4.4,-7,6]
            v1=[center[0],center[1]+math.sin(angle_a)*3,center[2]+math.cos(angle_a)*3]
            v2=[center[0],center[1]+math.sin(angle_b)*3,center[2]+math.cos(angle_b)*3]
            mechanical.append(beam('dragranzer_tire_'+str(sign)+'_'+str(i),v1,v2,1.1,1,'carbon'))
        mechanical.append(beam('dragranzer_wheel_axle_'+str(sign),[sign*3.8,-7,6],[sign*5,-7,6],1.5,1.5,'gold'))
    cloak=[]
    for sign in [-1,1]:
        wing=bevel_plate('cloak_bat_wing_'+str(sign),mirrored([[.1,-7],[2.5,-7.5],[7,-5.5],[6,-1],[4.5,3],[3,2],[1,6],[0,4]],sign),.7,.35,'knight_navy',.035)
        wing['animation']={'bone':'cloak_wing_'+str(sign),'pivot':[0,-7,.7],'axis':'y','amplitude':sign*35,'period':2,'phase':.25};cloak.append(wing)
        for j in range(3):
            rib=beam('cloak_silver_wing_rib_'+str(sign)+'_'+str(j),[sign*.4,-6.7,.35],[sign*(6-j*2),-1+j*2.4,.35],.15,.17,'knight_metal');rib['animation']=dict(wing['animation']);cloak.append(rib)
    data['forms']['knight_cloak']=object_form(cloak)
    # One shared joint pivot per segment: all facets rotate together.
    for name in ['dragreder','dragranzer']:
        joints={}
        for part in data['forms'][name]['body']:
            if 'animation' not in part:continue
            a=part['animation'];joints.setdefault(a['bone'],a['pivot']);a['pivot']=joints[a['bone']]
    data['references']['additional_equipment_sources']=['https://www.kamen-rider-official.com/zukan/items/'+str(i) for i in [1086,1087,1186,1187,272,273]]
