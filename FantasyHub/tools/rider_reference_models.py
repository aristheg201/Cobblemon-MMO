"""Original voxel armor and equipment, authored against the supplied Agito/Zolda photographs.

Coordinates follow native biped bones. Materials are painted in Blockbench, not copied
from movie photographs. All exports retain editable bone groups and named components.
"""
import json, math
from pathlib import Path

MATERIALS={
 'gold':[190,139,38],'gold_edge':[240,196,85],'gold_dark':[115,77,22],
 'steel':[154,171,181],'chrome':[212,223,228],'steel_dark':[65,77,84],
 'green':[26,123,72],'green_dark':[10,64,47],'green_light':[55,171,102],
 'red':[161,22,37],'red_edge':[222,46,58],'blue':[20,79,160],'blue_edge':[45,130,220],
 'carbon':[22,28,31],'fabric':[38,43,45],'black':[10,14,17],'white':[222,225,220],
 'lens_red':[223,24,34],'lens_green':[31,176,89],'lens_amber':[246,178,24],
 'amber':[235,173,45],'gem':[15,119,111],'silver_white':[184,196,198],
}

def box(name,at,size,material,rotation=None,pivot=None,**extra):
    part={'name':name,'from':at,'size':size,'material':material,**extra}
    if rotation:part.update(rotation=rotation,pivot=pivot or [at[i]+size[i]/2 for i in range(3)])
    return part

def plate(name,xy,z,depth,material,**extra):
    """Extruded silhouette; unlike thin rods, horns keep a broad faceted root."""
    n=len(xy);vertices=[[x,y,z] for x,y in xy]+[[x,y,z+depth] for x,y in xy]
    return {'name':name,'vertices':vertices,'faces':[list(range(n-1,-1,-1)),list(range(n,2*n))]+[[i,(i+1)%n,(i+1)%n+n,i+n] for i in range(n)],'material':material,**extra}

def bevel_plate(name,xy,z,depth,material,inset=.12,**extra):
    """A convex lens/forged plate with a real bevel and lit side facets."""
    n=len(xy);cx=sum(x for x,y in xy)/n;cy=sum(y for x,y in xy)/n
    vertices=[[x,y,z+depth] for x,y in xy]+[[x,y,z+.18] for x,y in xy]+[[x+(cx-x)*inset,y+(cy-y)*inset,z] for x,y in xy]
    faces=[list(range(n-1,-1,-1)),list(range(2*n,3*n))]
    for ring in range(2):
        a=ring*n;b=(ring+1)*n
        faces += [[a+i,a+(i+1)%n,b+(i+1)%n,b+i] for i in range(n)]
    return {'name':name,'vertices':vertices,'faces':faces,'material':material,**extra}

def side_plate(name,yz,x,depth,material):
    item=plate(name,yz,x,depth,material)
    item['vertices']=[[v[2],v[0],v[1]] for v in item['vertices']]
    return item

def agito_mask(shining):
    gold='red' if shining else 'gold';edge='red_edge' if shining else 'gold_edge'
    eye=[[-.52,-4.8],[-.85,-6.4],[-1.8,-7],[-3.1,-6.95],[-3.95,-5.9],[-4.1,-4.65],[-3.7,-3.15],[-2.7,-2.5],[-1.4,-2.85],[-.7,-3.65]]
    horn=[[-.35,-5.95],[-.85,-7.25],[-2.5,-8.45],[-4.55,-9.75],[-4.75,-10.65],[-4.2,-12.25],[-2.8,-14.25],[-3.25,-12.3],[-3.3,-10.9],[-2.8,-9.85],[-1.75,-9.05],[-.75,-8.55]]
    parts=[box('sealed_inner_helmet',[-4.15,-8.2,-4.15],[8.3,8.3,8.3],'carbon'),
      box('crown_silver_midline',[-.62,-8.55,-3.8],[1.24,.38,8.25],'chrome'),
      bevel_plate('silver_forehead',[[-.65,-8.6],[.65,-8.6],[1.1,-7],[0,-6.3],[-1.1,-7]],-4.93,.5,'chrome',.06),
      bevel_plate('forehead_jewel',[[-.25,-7.25],[0,-7.45],[.25,-7.25],[.2,-6.95],[-.2,-6.95]],-5.08,.2,'blue_edge' if shining else 'gem',.2),
      bevel_plate('cross_horn_center',[[-.52,-6.9],[.52,-6.9],[.45,-2.25],[0,-1.9],[-.45,-2.25]],-5.05,.65,gold,.08),
      bevel_plate('silver_jaw_crusher',[[-2.35,-2.55],[0,-2.1],[2.35,-2.55],[2.05,-.7],[0,.05],[-2.05,-.7]],-4.93,.7,'steel',.12)]
    for sign,side in [(-1,'left'),(1,'right')]:
        mirror=lambda xy:[[(-x if sign>0 else x),y] for x,y in xy]
        parts += [bevel_plate(side+'_compound_eye',mirror(eye),-4.85,.73,'lens_amber' if shining else 'lens_red',.12),
          bevel_plate(side+'_forged_cross_horn',mirror(horn),-5.0,1.05,gold,.08),
          bevel_plate(side+'_gold_cheek',mirror([[-.4,-2.2],[-1.55,-2.95],[-3.2,-2.7],[-3.7,-1.9],[-2.2,.0],[-1.75,-.3],[-2.2,-1.1],[-1.4,-.85],[-1.85,-1.65],[-1,-1.4]]),-4.97,.65,gold,.08),
          plate(side+'_silver_temporal_rim',mirror([[-3.7,-3.2],[-4.15,-3.6],[-4.3,-1.7],[-3.2,.3],[-2.5,.55],[-3.55,-1.55]]),-4.45,.4,'chrome'),
          bevel_plate('cross_horn_expanded_'+side,mirror([[-1.9,-8.55],[-4.45,-9.65],[-5.7,-12.5],[-5.35,-9.5],[-6.4,-10.0],[-5.4,-8.3],[-3.05,-7.8]]),-4.8,.7,edge,.08,requires_force=True),
          box(side+'_helmet_ear',[-4.46 if sign<0 else 3.9,-3.65,-1.3],[.56,2.1,2.3],'steel_dark')]
    for i in range(3):
        y=-2.25+i*.65
        parts.append(plate('v_jaw_vent_'+str(i),[[-2.1+i*.2,y],[0,y+.52],[2.1-i*.2,y],[1.95-i*.2,y+.18],[0,y+.7],[-1.95+i*.2,y+.18]],-5.03,.13,'steel_dark'))
    return parts

def zolda_mask():
    p=[box('sealed_inner_helmet',[-4.15,-8.2,-4.15],[8.3,8.3,8.3],'carbon'),
      bevel_plate('green_helmet_front',[[-3.4,-8.5],[3.4,-8.5],[4.2,-7.5],[4.35,-5.1],[-4.35,-5.1],[-4.2,-7.5]],-4.42,.5,'green',.055),
      box('green_crown',[-3.5,-8.55,-3.8],[7,1.15,7.65],'green'),
      bevel_plate('silver_visor_surround',[[-4.55,-5.2],[4.55,-5.2],[4.65,-2.4],[3.8,-1.45],[-3.8,-1.45],[-4.65,-2.4]],-4.92,.8,'chrome',.055),
      box('three_slot_black_visor',[-4.43,-4.91,-5.07],[8.86,2.5,.18],'black'),
      bevel_plate('black_lower_chin',[[-3.5,-.3],[3.5,-.3],[2.7,1.25],[-2.7,1.25]],-4.2,.65,'carbon',.05),
      bevel_plate('z_signal_silver_mount',[[-.62,-8.1],[.62,-8.1],[1.27,-7],[.68,-5.3],[-.68,-5.3],[-1.27,-7]],-4.95,.55,'chrome',.06),
      box('z_signal_black_recess',[-.45,-7.8,-5.08],[.9,2.1,.2],'black'),
      box('z_signal_receiver',[-.29,-7.65,-5.27],[.58,.62,.2],'steel'),
      box('z_signal_blue_bar',[-.37,-6.9,-5.29],[.74,.26,.21],'blue_edge'),
      bevel_plate('z_signal_red_lamp',[[-.27,-6.37],[.27,-6.37],[.38,-6.1],[.2,-5.86],[-.2,-5.86],[-.38,-6.1]],-5.3,.2,'lens_red',.2)]
    for i in range(4):p.append(box('visor_silver_rail_'+str(i),[-4.5,-5.02+i*.78,-5.15],[9,.31,.28],'chrome'))
    for sign,side in [(-1,'left'),(1,'right')]:
        pivot=[sign*.95,-7.1,-4.55]
        p.append(box(side+'_main_antenna',[pivot[0]-.35,-14.1,-4.9],[.7,7,.7],'silver_white',rotation=[0,0,sign*29],pivot=pivot))
        for i in range(3):p.append(box(side+'_antenna_tip_ring_'+str(i),[pivot[0]-.47,-14.0+i*.35,-5.02],[.94,.16,.94],'chrome',rotation=[0,0,sign*29],pivot=pivot))
        p += [box(side+'_vertical_side_antenna',[sign*4.45-.38,-10.45,.55],[.76,5.65,.76],'silver_white'),
              box(side+'_side_antenna_cap',[sign*4.45-.48,-10.5,.45],[.96,.35,.96],'chrome'),
              box(side+'_side_antenna_collar',[sign*4.3-.46,-5.55,.47],[.92,.35,.92],'steel_dark'),
              box(side+'_silver_band',[sign*4.2-.3,-5.1,-3.9],[.6,3.9,8.3],'steel'),
              box(side+'_jaw_bar',[sign*3.5-.2,-1.1,-5.2],[.4,1.15,.5],'chrome')]
        p.append(side_plate(side+'_green_helmet_side',[[-8.2,-3.75],[-8.2,3.75],[-7.55,4.1],[-5.08,4.15],[-5.08,-4.15],[-7.5,-4.1]],sign*4.16-.12,.24,'green'))
        yz=[[-4.8,-3.45],[-5.05,-2.4],[-4.25,-1.1],[-2.4,-1],[-1.15,-2.2],[-1.55,-3.55],[-3.3,-4.1]]
        p.append(side_plate(side+'_temple_mechanism',yz,sign*4.45-.18,.36,'chrome'))
        p.append(side_plate(side+'_temple_receiver',[[-4.1,-2.9],[-4.3,-2],[-3.7,-1.35],[-2.8,-1.35],[-2.2,-2],[-2.4,-2.9],[-3.2,-3.35]],sign*4.67-.1,.2,'steel_dark'))
        for y,z in [(-3.15,1.25),(-1.7,.35),(-.5,-3.2)]:
            points=[[y-.43,z-.55],[y+.43,z-.55],[y+.62,z],[y+.43,z+.55],[y-.43,z+.55],[y-.62,z]]
            p.append(side_plate(side+'_side_hinge_'+str(y),points,sign*4.68-.1,.2,'silver_white'))
            p.append(box(side+'_hinge_screw_'+str(y),[sign*4.86-.08,y-.09,z-.32],[.16,.18,.64],'steel_dark'))
        p.append(box(side+'_side_vent_slot',[sign*4.63-.1,-4.65,.1],[.2,.18,1.45],'black'))
        for i in range(3):p.append(box(side+'_side_cooling_slit_'+str(i),[sign*4.64-.1,-2.2,.9+i*.45],[.2,.6,.14],'black',rotation=[-30,0,0]))
    p += [box('jaw_lower_crossbar',[-3.65,-.1,-5.2],[7.3,.4,.5],'chrome'),
          box('jaw_central_mesh',[-2.7,-1.65,-4.75],[5.4,1.9,.25],'steel_dark')]
    for x in [-1.9,0,1.9]:p.append(box('jaw_piston_'+str(x),[x-.24,-1.55,-5],[.48,1.5,.45],'silver_white'))
    return p

def armor_skeleton():
    return {bone:[] for bone in ['head','body','left_arm','right_arm','left_leg','right_leg']}

def undersuit(parts,material='fabric'):
    parts['head'].append(box('sealed_inner_helmet',[-4.15,-8.2,-4.15],[8.3,8.3,8.3],'carbon'))
    parts['body'].append(box('woven_body',[-4.08,-.05,-2.08],[8.16,12.1,4.16],material))
    for bone,origin in [('left_arm',[-1.08,-2.08,-2.08]),('right_arm',[-3.08,-2.08,-2.08]),('left_leg',[-2.08,0,-2.08]),('right_leg',[-2.08,0,-2.08])]:
        parts[bone].append(box('woven_'+bone,origin,[4.16,12.15,4.16],material))

def rear_armor(p,family,color,trim):
    """A full rear construction, not a front plate pasted onto a blank torso.

    Segmented scapulae, a recessed spine, rear vambraces and heel components
    stay attached to their own native bones during every pose.
    """
    h=p['head'];body=p['body']
    if family=='agito':
        for sign in [-1,1]:
            h.append(plate('rear_helmet_facet_'+str(sign),[(sign*.65,-7.7),(sign*3.55,-7.15),(sign*3.9,-3.2),(sign*2.8,-1.4),(sign*.75,-2.05)],3.95,.35,'carbon'))
            h.append(box('rear_perimeter_trim_'+str(sign),[sign*3.98-.1,-6.5,4.15],[.2,5.85,.28],'steel'))
        h += [plate('wide_flared_silver_occipital_stripe',[[-.62,-8.45],[.62,-8.45],[1.55,-.25],[-1.55,-.25]],4.29,.27,'chrome'),
              box('silver_nape_rim',[-3.5,-.42,4.15],[7,.36,.42],'steel')]
        body += [plate('left_rear_scapula',[[-4.2,.8],[-.5,.55],[-.65,5.9],[-2.8,6.6],[-4.2,5]],2.25,.78,color),
                 plate('right_rear_scapula',[[4.2,.8],[.5,.55],[.65,5.9],[2.8,6.6],[4.2,5]],2.25,.78,color),
                 box('recessed_back_spine',[-.38,1.4,2.5],[.76,5.35,.24],'carbon')]
        for i in range(3):
            y=6.2+i*.95
            body.append(plate('lumbar_segment_'+str(i),[[-3.8,y],[-1,y+.15],[0,y+.65],[1,y+.15],[3.8,y],[3.5,y+.7],[0,y+1.05],[-3.5,y+.7]],2.2,.5,'silver_white'))
    else:
        h += [box('rear_green_helmet_panel',[-3.9,-7.9,4],[7.8,3.25,.4],'green'),
              box('rear_wraparound_silver_band',[-4.5,-5.05,4.1],[9,3.85,.55],'steel'),
              box('rear_band_lower_chrome_rim',[-4.35,-1.45,4.6],[8.7,.3,.18],'chrome'),
              box('rear_clasp_black_outline',[-2.75,-3.85,4.64],[5.5,1.65,.16],'black'),
              box('rear_clasp_left_socket',[-2.55,-3.7,4.8],[1.3,1.35,.23],'chrome'),
              box('rear_clasp_right_socket',[1.25,-3.7,4.8],[1.3,1.35,.23],'chrome'),
              plate('rear_tapered_black_chin',[[-3.5,-1.15],[3.5,-1.15],[2.4,1],[-2.4,1]],3.95,.4,'carbon')]
        for sign in [-1,1]:
            for i in range(2):h.append(box('rear_socket_slot_'+str(sign)+'_'+str(i),[sign*1.9-.42,-3.5+i*.6,5.04],[.84,.27,.12],'black'))
        for i in range(6):h.append(box('rear_clasp_rib_'+str(i),[-1.17+i*.39,-3.7,4.82],[.21,1.35,.25],'silver_white'))
        body += [plate('left_rear_silver_harness',[[-4.4,.6],[-.65,.6],[-.65,5.5],[-2.65,7.1],[-4.35,5.9]],2.15,.95,'steel'),
                 plate('right_rear_silver_harness',[[4.4,.6],[.65,.6],[.65,5.5],[2.65,7.1],[4.35,5.9]],2.15,.95,'steel'),
                 box('rear_black_center_channel',[-.48,1.1,2.3],[.96,5.8,.45],'black'),
                 box('rear_harness_crossbar',[-3.6,1.65,3.15],[7.2,.65,.32],'chrome'),
                 box('rear_harness_lower_rail',[-3.15,5.45,3.15],[6.3,.5,.3],'steel_dark')]
        for x in [-3.8,3.15]:body.append(box('rear_harness_fastener',[x,2.8,3.13],[.65,1.4,.4],'chrome'))
    for side in ['left','right']:
        left=side=='left';x=-1.45 if left else -3.9
        arm=p[side+'_arm']
        arm += [box(side+'_rear_pauldron_rim',[x,-2.35,2.7],[5.35,.6,.42],'chrome'),
                box(side+'_rear_upper_arm_seam',[-.9 if left else -2.9,2.3,2.12],[3.8,.35,.18],'steel_dark'),
                box(side+'_rear_bracer_panel',[-.85 if left else -2.85,5.7,2.35],[3.7,2.35,.25],trim if family=='agito' else 'green_dark'),
                box(side+'_rear_glove_knuckles',[-.8 if left else -2.8,9.3,2.13],[3.6,.65,.2],'steel_dark')]
        leg=p[side+'_leg']
        leg += [box(side+'_rear_knee_flex',[-1.85,4.8,2.12],[3.7,.5,.2],'steel_dark'),
                box(side+'_calf_center_seam',[-.2,6.3,2.12],[.4,3.2,.2],'steel_dark'),
                box(side+'_boot_heel_cap',[-2.18,10.3,1.65],[4.36,1.6,.7],'carbon'),
                box(side+'_boot_heel_trim',[-1.9,10.2,2.33],[3.8,.4,.2],'chrome' if family=='agito' else 'green'),
                box(side+'_boot_sole',[-2.23,11.65,-3.24],[4.46,.6,5.76],'black')]

def agito(form):
    p=armor_skeleton();undersuit(p)
    shining=form=='shining';storm=form=='storm';flame=form=='flame';trinity=form=='trinity'
    chest='silver_white' if shining else 'blue' if storm else 'red' if flame else 'gold'
    trim='red' if shining else 'gold_edge'
    p['head']=agito_mask(shining)
    p['body'] += [
      box('gold_collar',[-2.7,-.65,-2.65],[5.4,1.1,5.3],trim),
      plate('left_pectoral',[[-4.65,1.15],[-1.2,.5],[-.6,1.8],[-.8,5.8],[-3.1,6.25],[-4.6,4.8]],-3.25,1.2,chest),
      plate('right_pectoral',[[4.65,1.15],[1.2,.5],[.6,1.8],[.8,5.8],[3.1,6.25],[4.6,4.8]],-3.25,1.2,chest),
      box('sternum_frame',[-.65,.7,-3.4],[1.3,4.7,.6],trim),
      box('philosopher_stone',[-.4,1.1,-3.75],[.8,3.6,.4],'amber' if shining else 'black'),
      box('stone_glass_reflection',[-.32,1.25,-3.82],[.12,2.8,.13],'gold_edge' if shining else 'gem'),
    ]
    for i in range(3):
        y=6.1+i*1.15
        p['body'].append(plate('abdominal_plate_'+str(i),[[-3.75,y],[3.75,y],[3.4,y+.9],[0,y+1.55],[-3.4,y+.9]],-2.85-i*.02,.65,'silver_white' if not shining else 'white'))
        if storm or flame or trinity:
            p['body'].append(plate('elemental_abdominal_inlay_'+str(i),[[-1.95,y-.04],[0,y-.55],[1.95,y-.04],[1.7,y+.85],[0,y+1.3],[-1.7,y+.85]],-3.05-i*.02,.24,chest))
    for side in ['left','right']:
        left=side=='left';x=-1.45 if left else -3.9
        elemental=(storm and left) or(flame and not left) or trinity
        armcolor=('blue' if left else 'red') if elemental else 'silver_white' if shining else 'carbon'
        a=p[side+'_arm'];a.extend([
          box(side+'_square_pauldron',[x,-2.8,-2.7],[5.35,3.2,5.4],armcolor),
          box(side+'_pauldron_bevel',[x+.25,-3,-2.45],[4.85,.4,4.9],'chrome' if not shining else 'red'),
          box(side+'_arm_band',[-1.22 if left else -3.22,1,-2.22],[4.44,1.25,4.44],armcolor),
          box(side+'_forearm_armor',[-1.3 if left else -3.3,5.2,-2.35],[4.6,3.55,4.7],'silver_white' if shining else 'gold'),
          box(side+'_bracer_highlight',[-1.12 if left else -3.12,5.45,-2.49],[.4,2.85,.2],'chrome' if shining else 'gold_edge'),
          box(side+'_wrist_trim',[-1.25 if left else -3.25,8.5,-2.45],[4.5,.65,4.9],'chrome'),
          box(side+'_glove',[-1.17 if left else -3.17,9.15,-2.17],[4.34,1.1,4.34],'carbon')])
        if elemental:
            a.append(box(side+'_element_upper_arm',[-1.18 if left else -3.18,2.2,-2.18],[4.36,2.35,4.36],armcolor))
            a.append(plate(side+'_gold_shoulder_inlay',[[x+.4,-.9],[x+4.95,-1.85],[x+4.95,-.2],[x+.4,.2]],-2.87,.22,'gold'))
        else:
            a.append(box(side+'_shoulder_silver_lip',[x-.05,-.15,-2.85],[5.45,.55,.3],'chrome'))
        leg=p[side+'_leg'];leg.extend([
          plate(side+'_knee', [[-1.8,4.4],[1.8,4.4],[2.15,6], [0,7.25],[-2.15,6]],-2.45,.6,'red' if shining else 'gold'),
          plate(side+'_knee_silver_cross',[[-1.7,4.7],[-1.1,4.7],[0,5.7],[1.2,4.7],[1.7,4.7],[1.7,5.2],[.55,6.1],[1.2,6.9],[.6,7.1],[-.1,6.5],[-.8,7],[-1.3,6.8],[-.6,6.1],[-1.7,5.2]],-2.6,.17,'chrome'),
          box(side+'_shin_guard',[-.4,7,-2.45],[.8,3.8,.3],'chrome' if not shining else 'red'),
          box(side+'_ankle_bracer',[-2.16,9,-2.28],[4.32,1.65,4.56],'white' if shining else 'gold'),
          box(side+'_boot_toe',[-2.2,10.4,-3.15],[4.4,1.8,5.45],'carbon'),
          box(side+'_boot_bevel',[-1.9,10.15,-3.15],[3.8,.35,5.1],'steel_dark')])
    # Attach the form-specific Alter Ring to the torso; it follows crouching and
    # is also present when the actual armor is worn outside a transformation.
    add_buckle(p,'agito_belt','blue_edge' if storm else 'red_edge' if flame else 'lens_amber')
    rear_armor(p,'agito',chest,'silver_white' if shining else 'gold')
    return p

def zolda():
    p=armor_skeleton();undersuit(p,'green_dark')
    p['head']=zolda_mask()
    p['body'] += [
      box('silver_collar',[-2.6,-.6,-2.55],[5.2,1.2,5.1],'chrome'),
      plate('left_tank_chest',[[-4.7,.75],[-.9,.6],[-.65,5.4],[-3.6,6.55],[-4.6,5.3]],-3.4,1.2,'steel'),
      plate('right_tank_chest',[[4.7,.75],[.9,.6],[.65,5.4],[3.6,6.55],[4.6,5.3]],-3.4,1.2,'steel'),
      box('chest_black_crest_panel',[-2.4,1.1,-3.7],[4.8,2.7,.65],'carbon'),
      box('chest_circular_receiver',[-1.05,1.4,-4],[2.1,2.1,.65],'chrome'),
      box('chest_receiver_recess',[-.68,1.78,-4.15],[1.36,1.36,.2],'steel_dark'),
      plate('silver_abdominal_cuirass',[[-3.5,6.2],[0,5.35],[3.5,6.2],[2.7,8.6],[0,9.25],[-2.7,8.6]],-3.2,.85,'chrome'),
      box('tank_back_underlayer',[-4.1,.5,1.6],[8.2,7,1.25],'steel_dark')]
    for side in ['left','right']:
      left=side=='left';x=-1.75 if left else -4.25
      a=p[side+'_arm'];a += [box('square_gun_pauldron',[x,-3,-3],[6,4.3,6],'steel_dark'),
        box('pauldron_front_frame',[x+.35,-2.75,-3.4],[5.3,3.8,.65],'chrome'),
        box('pauldron_black_panel',[x+.95,-2.15,-3.65],[4.1,2.6,.3],'black'),
        box('pauldron_yellow_receiver',[x+2,-1.9,-3.9],[2,2,.35],'lens_amber'),
        box('green_forearm',[-1.25 if left else -3.25,4,-2.25],[4.5,4.3,4.5],'green'),
        box('silver_wrist',[-1.2 if left else -3.2,8.2,-2.2],[4.4,.8,4.4],'steel'),
        box('black_glove',[-1.15 if left else -3.15,9,-2.15],[4.3,1.2,4.3],'carbon')]
      p[side+'_leg'] += [box('green_thigh',[-2.16,0,-2.16],[4.32,4.3,4.32],'green'),
        box('round_square_knee',[-1.55,4.25,-2.65],[3.1,2.6,.8],'steel'),
        box('knee_inner_receiver',[-.9,4.75,-2.85],[1.8,1.6,.25],'steel_dark'),
        plate('black_shin',[[-2,6.2],[2,6.2],[1.6,11.1],[-1.6,11.1]],-2.45,.6,'carbon'),
        box('green_shin_line',[-.35,6.6,-2.65],[.7,4.4,.3],'green_light'),
        box('boot_toe',[-2.2,10.55,-3.25],[4.4,1.65,5.5],'carbon')]
    add_buckle(p,'zolda_belt')
    rear_armor(p,'zolda','steel','chrome')
    return p

def add_buckle(parts,item,stone=None):
    for source in equipment()[item]:
        a,c=source['from'],source['to']
        s=.65
        at=[(a[0]-8)*s,10.35-(c[1]-8)*s,-3.0+(a[2]-8)*s]
        size=[(c[i]-a[i])*s for i in range(3)]
        mat=stone if stone and source['name'].startswith('faceted_philosopher_stone') else source['material']
        element=box('belt_'+source['name'],at,size,mat)
        if source.get('rotation'):
            r=source['rotation'];o=r['origin']
            element.update(rotation=[0,0,-r['angle']],pivot=[(o[0]-8)*s,10.35-(o[1]-8)*s,-3+(o[2]-8)*s])
        parts['body'].append(element)
    parts['body'] += [box('waist_rear_strap',[-4.3,9.6,2.08],[8.6,1.5,.35],'red' if item=='agito_belt' else 'carbon'),box('waist_left_strap',[-4.4,9.6,-2.25],[.35,1.5,4.65],'red' if item=='agito_belt' else 'carbon'),box('waist_right_strap',[4.05,9.6,-2.25],[.35,1.5,4.65],'red' if item=='agito_belt' else 'carbon'),box('waist_rear_clasp',[-1.2,9.35,2.4],[2.4,1.95,.35],'steel'),box('waist_clasp_recess',[-.7,9.75,2.75],[1.4,1.15,.12],'steel_dark')]

def magnugiga():
    p=armor_skeleton();undersuit(p,'green_dark')
    p['head']=[box('armored_bull_head',[-3.5,-7,-3.5],[7,7,7],'green'),box('bull_forehead_sensor',[-1.25,-6,-4],[2.5,2.5,.6],'gold'),box('sensor_lens',[-.7,-5.5,-4.2],[1.4,1.4,.3],'lens_amber')]
    for sign in [-1,1]:
      xy=[(sign*2.6,-4.2),(sign*4.5,-3.6),(sign*8,-5),(sign*10,-8.3),(sign*9.5,-12.5),(sign*8,-8.2),(sign*5.5,-6.25),(sign*3,-6)]
      p['head'].append(plate('giga_horn_'+str(sign),xy,-3,2,'gold'))
    p['body']=[box('massive_green_chassis',[-7,-.8,-4],[14,12,8],'green'),
       plate('upper_chest_shield',[[-6.5,.8],[6.5,.8],[5.1,6],[0,7.9],[-5.1,6]],-4.8,1.4,'chrome'),
       plate('lower_chest_shield',[[-4.7,5.2],[0,6.1],[4.7,5.2],[3.3,9.4],[0,11.1],[-3.3,9.4]],-5,1.25,'steel'),
       box('back_docking_socket',[-1.5,3.3,3.8],[3,3,1.5],'black'),
       box('docking_socket_rim',[-1.9,2.9,3.85],[3.8,.55,1.7],'gold')]
    for side in ['left','right']:
      left=side=='left';x=-3 if left else -6
      p[side+'_arm']=[box('giga_shoulder',[x,-3.5,-4.25],[9,6.5,8.5],'green_dark'),box('shoulder_armor',[x-.4,-3.9,-4.75],[9.8,1.25,9.5],'green'),box('shoulder_missile_bay',[x+.7,-2.5,-4.9],[7.6,4.7,.65],'steel_dark'),box('massive_arm',[-2.3 if left else -4.3,2,-3.2],[6.6,7.6,6.4],'green')]
      if left:
        for xx in [-1.7,2.1]:p[side+'_arm'].append(box('giga_hand_claw', [xx,7.5,-6.5],[1.5,3,9],'steel'))
      else:
        p[side+'_arm'] += [box('giga_barrel',[-4.4,7.2,-14],[6.8,5.6,16],'steel'),box('gold_muzzle',[-4.7,6.95,-15],[7.4,6.1,1.5],'gold'),box('bore',[-3.5,8,-15.2],[5,4,.5],'black')]
      p[side+'_leg']=[box('massive_thigh',[-3.1,0,-3.2],[6.2,5.7,6.4],'steel'),box('giga_knee_armor',[-3.9,4,-4.5],[7.8,6.5,3.5],'green'),box('giga_shin',[-3.35,5.7,-3.7],[6.7,6,7.4],'green_dark'),box('giga_foot',[-3.8,10,-6],[7.6,2.2,10],'steel_dark')]
      for xx in [-1.8,.5]:
        for yy in [4.9,6.7,8.5]:p[side+'_leg'].append(box('knee_missile_port',[xx,yy,-4.7],[1.3,1.3,.5],'gold_dark'))
    # Rear docking assembly, service hatches and the twin cannon housings are
    # part of the robot, so the summon remains recognizable from behind too.
    p['body'] += [box('rear_reactor_frame',[-4.7,.7,3.9],[9.4,8.2,1.1],'steel_dark'),
                  box('rear_reactor_core',[-3.45,1.75,4.95],[6.9,5.9,.55],'green'),
                  box('rear_spine_rail',[-.55,1.5,5.45],[1.1,6.4,.35],'chrome'),
                  box('rear_waist_crossmember',[-5,9.2,3.9],[10,1.15,1.1],'chrome')]
    for sign in [-1,1]:
        x=sign*5
        p['body'] += [box('rear_cannon_housing_'+str(sign),[x-1.75,-3,3.6],[3.5,9.5,3.5],'green_dark'),
                      box('rear_cannon_silver_cap_'+str(sign),[x-1.5,-3.3,3.9],[3,1.05,2.9],'steel'),
                      box('rear_cannon_exhaust_'+str(sign),[x-1.1,4.8,7.05],[2.2,1.2,.3],'black')]
        for j in range(3):p['body'].append(box('rear_cooling_vent_'+str(sign)+'_'+str(j),[sign*1.8-.6,2.1+j*1.3,5.55],[1.2,.35,.2],'black'))
    return p

def item_box(name,a,b,material,angle=0,origin=None):
    value={'name':name,'from':a,'to':b,'material':material}
    if angle:value['rotation']={'axis':'z','angle':angle,'origin':origin or [(a[i]+b[i])/2 for i in range(3)]}
    return value

def octagonal_ring(name,cx,cy,z,r,width,material):
    """A real hollow eight-sided ring: the lens is recessed, not a square decal."""
    pieces=[]
    edge=2*r*math.tan(math.pi/8)
    for i in range(8):
        angle=i*45
        rad=math.radians(angle)
        x,y=cx+r*math.sin(rad),cy+r*math.cos(rad)
        # Java block models permit rotations only up to 45 degrees. Orthogonal
        # segments exchange their dimensions instead of using invalid rotations.
        a=[x-edge/2,y-width/2,z];c=[x+edge/2,y+width/2,z+.55]
        if i%4==2:a=[x-width/2,y-edge/2,z];c=[x+width/2,y+edge/2,z+.55]
        turn=0 if i%2==0 else (-45 if i in (1,5) else 45)
        pieces.append(item_box(name+'_'+str(i),a,c,material,turn,[x,y,z+.275]))
    return pieces

def swept_blade(name,outline,material,spine=None,thickness=3.2,spine_width=None):
    """Continuous voxel-cut silhouette with continuous UVs, not disconnected rods."""
    parts=[];step=.5;min_y=min(y for x,y in outline);max_y=max(y for x,y in outline)
    min_x=min(x for x,y in outline);max_x=max(x for x,y in outline)
    rows=math.ceil((max_y-min_y)/step)
    for row in range(rows):
        y0=min_y+row*step;y1=min(max_y,y0+step);mid=(y0+y1)/2
        edges=[]
        for (x,y),(xx,yy) in zip(outline,outline[1:]+outline[:1]):
            if (y<=mid<yy) or (yy<=mid<y):edges.append(x+(xx-x)*(mid-y)/(yy-y))
        edges.sort()
        for index,(lo,hi) in enumerate(zip(edges[::2],edges[1::2])):
            cube=item_box(name+'_edge_'+str(row)+'_'+str(index),[lo,y0,8-thickness/2],[hi,y1,8+thickness/2],material)
            cube['uv_crop']=[(lo-min_x)/(max_x-min_x),(y0-min_y)/(max_y-min_y),(hi-min_x)/(max_x-min_x),(y1-min_y)/(max_y-min_y)]
            parts.append(cube)
            if spine and hi-lo>1.0:
                inset=item_box(name+'_inlay_'+str(row),[max(lo+.45,hi-(spine_width or hi-lo-.62)),y0,8-thickness/2-.025],[hi-.12,y1,8+thickness/2+.025],spine)
                inset['uv_crop']=cube['uv_crop'];parts.append(inset)
    return parts

def equipment():
    b=item_box
    belt=[b('strap',[0,6.7,9.5],[16,9.3,10.5],'carbon'),b('silver_buckle',[2.2,5.8,8],[13.8,10.2,10],'chrome'),b('deck_border',[4.1,5.4,7],[11.9,10.6,8.5],'steel_dark'),b('green_card_deck',[4.65,5.85,6.6],[11.35,10.15,7.5],'green'),b('bull_crest_stem',[7.6,6.5,6.3],[8.4,8.6,6.8],'gold'),b('bull_crest_horns',[5.6,8.1,6.3],[10.4,8.75,6.8],'gold'),b('deck_left_latch',[2.6,6.4,7.7],[3.4,7.4,8.2],'red'),b('deck_right_latch',[12.6,6.4,7.7],[13.4,7.4,8.2],'blue')]
    agito_belt=[b('alter_ring_red_strap',[0,6.5,9.1],[16,9.5,10.2],'red'),b('alter_ring_silver_body',[1.2,5.1,7.6],[14.8,10.9,9.9],'chrome'),b('alter_ring_black_inset',[3.1,5.6,6.9],[12.9,10.4,8.5],'carbon'),b('gold_outer_ring',[5.1,5,6.3],[10.9,11,7.5],'gold'),b('black_ring_recess',[5.85,5.75,6],[10.15,10.25,6.5],'black'),b('philosopher_stone',[6.25,6.15,5.8],[9.75,9.85,6.3],'lens_amber'),b('left_form_orb',[3.25,6.65,6.5],[5,9.35,7.3],'red_edge'),b('right_form_orb',[11,6.65,6.5],[12.75,9.35,7.3],'blue_edge')]
    agito_belt=[e for e in agito_belt if e['name'] not in ('gold_outer_ring','black_ring_recess','philosopher_stone')]
    agito_belt+=octagonal_ring('alter_gold_bezel',8,8,6.1,2.35,.6,'gold')
    agito_belt+=octagonal_ring('alter_silver_inner_rim',8,8,5.95,1.8,.28,'chrome')
    for i,(width,y0,y1) in enumerate([(2.15,6.5,6.9),(2.85,6.9,9.1),(2.15,9.1,9.5)]):
        agito_belt.append(b('faceted_philosopher_stone_'+str(i),[8-width/2,y0,6.12],[8+width/2,y1,6.5],'lens_amber'))
    for i in range(3):
        agito_belt.append(b('left_belt_vent_'+str(i),[1.65,5.85+i*1.4,7.35],[2.55,6.4+i*1.4,7.7],'steel_dark'))
        agito_belt.append(b('right_belt_vent_'+str(i),[13.45,5.85+i*1.4,7.35],[14.35,6.4+i*1.4,7.7],'steel_dark'))
    # The deck's bull emblem has upturned horns and a split muzzle, not a T.
    belt=[e for e in belt if e['name'] not in ('bull_crest_horns','bull_crest_stem')]
    belt += [b('bull_gold_face',[7.2,6.4,6.25],[8.8,8.3,6.6],'gold'),
      b('bull_left_horn',[5.4,8,6.25],[7.7,8.65,6.6],'gold',-22.5),
      b('bull_right_horn',[8.3,8,6.25],[10.6,8.65,6.6],'gold',22.5),
      b('bull_left_tip',[5.05,8.3,6.25],[5.65,9.15,6.6],'gold'),
      b('bull_right_tip',[10.35,8.3,6.25],[10.95,9.15,6.6],'gold')]
    visor=[b('receiver',[6.5,9.5,6],[18.6,13.4,10],'green'),b('top_silver_rail',[5,13.35,6.5],[18,14.15,9.5],'chrome'),b('bull_muzzle',[-1.8,9,6],[7.5,13.5,10],'green_dark'),b('muzzle_ring',[-2.2,9.5,6.5],[-1.5,13,9.5],'gold'),b('barrel_bore',[-2.4,10.3,7.2],[-2.15,12.2,8.8],'black'),b('pistol_grip',[12,2.5,6.7],[14.8,9.6,9.3],'carbon'),b('grip_silver_base',[11.7,2.15,6.4],[15.1,3.15,9.6],'chrome'),b('trigger_guard',[9.1,6.2,6.5],[12.3,7.2,9.5],'steel'),b('card_slot',[8.2,12.1,5.6],[14.7,13.1,6],'black'),b('card_reader_plate',[5.8,9.9,5.2],[10.7,12.8,6],'chrome'),b('reader_green_deck',[6.3,10.25,5],[10.2,12.45,5.3],'green')]
    launcher=[b('rocket_body',[-9,5,4],[15,11,12],'green_dark'),b('front_collar',[-10,4.4,3.4],[-8,11.6,12.6],'steel'),b('gold_muzzle',[-10.6,5.1,4.1],[-10,10.9,11.9],'gold'),b('rocket_bore',[-10.85,6.2,5.2],[-10.6,9.8,10.8],'black'),b('rear_shroud',[13,3,2],[21,13,14],'green'),b('rear_vent',[20.7,4,3],[21.3,12,13],'carbon'),b('shoulder_stock',[8,1,5],[16,5,11],'steel_dark'),b('launcher_handle',[2,1,6],[5,5,10],'carbon')]
    saber=[b('red_grip',[7,1.5,7],[9,9.8,9],'red'),b('silver_pommel',[6.4,.5,6.4],[9.6,2.2,9.6],'steel')]
    saber+=octagonal_ring('flame_gold_guard',8,10,6,2.7,.65,'gold')
    blade=swept_blade('flame_saber',[(6.9,11),(9.4,11),(9.2,21),(7.4,28),(4.4,32),(5.2,28),(6.5,21)],'chrome');saber+=blade
    for i,segment in enumerate(blade):
        a,c=segment['from'],segment['to'];width=min(.45,(c[0]-a[0])*.35)
        spine=b('flame_red_spine_'+str(i),[c[0]-width,a[1],6.25],[c[0]+.03,c[1],9.75],'red');spine['uv_crop']=segment['uv_crop'];saber.append(spine)
    halberd=[b('storm_blue_shaft',[7.3,-12,7.3],[8.7,21,8.7],'blue'),
      b('storm_hand_grip',[7,3,7],[9,9,9],'blue_edge'),
      b('pole_lower_silver_band',[7,0,7],[9,.8,9],'chrome'),
      b('pole_upper_silver_band',[7,18.7,7],[9,19.5,9],'chrome'),
      b('gold_head_socket',[6.8,20,6.8],[9.2,24,9.2],'gold'),
      b('gold_long_upper_sweep',[7.3,23,6.7],[10.4,31.5,9.3],'gold',-22.5),
      b('gold_hook_tip',[9.2,28.5,6.9],[14.3,31.8,9.1],'gold',22.5),
      b('gold_hook_outer_edge',[12.7,27.1,6.8],[15.3,31.1,9.2],'gold_edge'),
      b('gold_inner_edge',[6.6,23.3,6.5],[7.5,29.2,9.5],'gold_edge',-22.5),
      b('gold_lower_sweep',[2.3,21.5,6.7],[7.6,24,9.3],'gold',-22.5),
      b('gold_lower_hook',[.8,20.5,6.9],[3.6,23.2,9.1],'gold_edge',22.5)]
    for source in list(halberd):
        if not source['name'].startswith('gold_'):continue
        a,c=source['from'],source['to']
        mirrored=b('opposite_'+source['name'],[16-c[0],16-c[1],a[2]],[16-a[0],16-a[1],c[2]],source['material'])
        if source.get('rotation'):
            r=source['rotation'];o=r['origin'];mirrored['rotation']={**r,'origin':[16-o[0],16-o[1],o[2]]}
        halberd.append(mirrored)
    # Shining Caliber in twin-saber mode: narrow swept cutting edge, visible
    # ribbed handle and small red/silver guard. The old thick C-shaped slab
    # obscured its hilt and read as a pair of bananas when held in both hands.
    caliber=[b('shining_grip',[7.2,1.8,7.3],[8.8,9.3,8.7],'carbon'),
      b('shining_pommel_silver',[6.8,1.2,6.95],[9.2,2.3,9.05],'chrome'),
      b('shining_pommel_red',[7,2.3,7.1],[9,3,8.9],'red'),
      b('shining_guard_silver',[5.4,9.1,6.9],[10.6,10.2,9.1],'chrome'),
      b('shining_guard_red',[6.6,10.15,7.2],[9.4,11.6,8.8],'red')]
    for i in range(6):
        y=3.2+i*.85
        caliber.append(b('shining_grip_rib_'+str(i),[7.08,y,7.18],[8.92,y+.17,8.82],'steel_dark'))
    caliber+=swept_blade('shining_crescent',
      [(7.0,10.9),(9.1,11.3),(9.35,15),(8.9,19),(7.6,23),(5.5,27),(2.0,30.8),
       (3.25,27),(4.8,23),(6.0,19),(6.65,15)],
      'chrome','red',thickness=.85,spine_width=.55)
    return {'zolda_belt':belt,'agito_belt':agito_belt,'magna_visor':visor,'giga_launcher':launcher,'flame_saber':saber,'storm_halberd':halberd,'shining_caliber':caliber}

def main():
    root=Path(__file__).resolve().parents[1];out=root/'assets-source/riders';out.mkdir(parents=True,exist_ok=True)
    data={'texture_size':512,'materials':MATERIALS,'forms':{'zolda':zolda(),**{'agito_'+f:agito(f) for f in ['ground','storm','flame','trinity','shining']},'magnugiga':magnugiga()},'items':equipment(),'references':{'zolda':['images (3).jpg','images (4).jpg','AW_Zolda.jpg','images (5).jpg','sddefault.jpg'],'agito':['1000391777.webp','1000391776.jpg','1000391775.jpg','1000391774.webp','1000391773.jpg'],'mask_detail_references':['1000391788.png (Agito expanded)','1000391787.jpg (Agito front/back/side)','1000391791.jpg (Zolda back)','1000391790.jpg (Zolda side)','1000391789.jpg (Zolda front)'],'primary_sources':['https://www.kamen-rider-official.com/zukan/forms/598','https://www.kamen-rider-official.com/zukan/phantoms/867','https://www.kamen-rider-official.com/zukan/forms/266','https://www.kamen-rider-official.com/zukan/forms/267']}}
    from card_rider_models import extend
    extend(data)
    (out/'rider_geometry.json').write_text(json.dumps(data,indent=2)+'\n');print('Prepared',len(data['forms']),'armored forms and',len(data['items']),'equipment models for Blockbench authoring')

if __name__=='__main__':main()
