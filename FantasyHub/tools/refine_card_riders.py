"""Reference correction after the user's rejection of the first Card Rider model pass.

The helmets are closed, faceted shells. Their distinct face cages, jaw profiles,
chest armor and rear seams are authored individually; square shoulders remain.
"""
import math
from rider_reference_models import box,plate,bevel_plate,side_plate,armor_skeleton,undersuit


def hull(name,rings,material):
    vertices=[]
    for y,w,d,chamfer in rings:
        vertices += [[x,y,z] for x,z in [(-w/2+chamfer,-d/2),(w/2-chamfer,-d/2),(w/2,-d/2+chamfer),(w/2,d/2-chamfer),(w/2-chamfer,d/2),(-w/2+chamfer,d/2),(-w/2,d/2-chamfer),(-w/2,-d/2+chamfer)]]
    n=8;faces=[list(range(7,-1,-1)),list(range((len(rings)-1)*8,len(rings)*8))]
    for r in range(len(rings)-1):
        for i in range(n):faces.append([r*n+i,r*n+(i+1)%n,(r+1)*n+(i+1)%n,(r+1)*n+i])
    return {'name':name,'vertices':vertices,'faces':faces,'material':material}


def surface(name,xy,z,depth,mat,curve=.065,bevel=.08):
    part=bevel_plate(name,xy,z,depth,mat,bevel)
    for v in part['vertices']:v[2]+=curve*v[0]**2
    return part


def mirrored(points,sign):return [[sign*x,y] for x,y in points]


def badge(name,y,z,r,mat):
    xy=[[math.sin(i*math.pi/4)*r,y+math.cos(i*math.pi/4)*r] for i in range(8)]
    return bevel_plate(name,xy,z,.23,mat,.2)


def ryuki_mask():
    p=[hull('sealed_faceted_ryuki_shell',[(-8.1,6.8,7.4,1.1),(-7.2,8.25,8.3,1.1),(-2,8.2,8.1,.9),(.6,6.2,6.6,1)],'carbon'),
       surface('ryuki_face_cage_silver_surround',[[-3.6,-7.5],[-1.4,-7.6],[-.8,-6.9],[.8,-6.9],[1.4,-7.6],[3.6,-7.5],[4.3,-6.2],[4.05,-1.7],[2.3,.4],[-2.3,.4],[-4.05,-1.7],[-4.3,-6.2]],-4.7,.55,'ryuki_metal',.045),
       surface('deep_black_face_recess',[[-3.8,-6.3],[3.8,-6.3],[3.9,-2.4],[2.8,-1.65],[-2.8,-1.65],[-3.9,-2.4]],-5.0,.25,'black',.045),
       surface('pointed_silver_jaw',[[-2.9,-1.8],[2.9,-1.8],[2,.45],[0,1],[-2,.45]],-4.72,.5,'ryuki_metal',.06),
       surface('narrow_black_chin_vent',[[-1.9,-.6],[1.9,-.6],[1.4,.2],[-1.4,.2]],-5.0,.13,'black',.02),
       badge('forehead_jewel_mount',-7.65,-4.6,.48,'chrome'),badge('forehead_ruby',-7.65,-4.9,.23,'lens_red')]
    for i in range(6):
        y=-6.45+i*.69
        p.append(surface('ryuki_wraparound_visor_rail_'+str(i),[[-4.15,y],[0,y+.13],[4.15,y],[4.12,y+.27],[0,y+.42],[-4.12,y+.27]],-5.32,.25,'ryuki_metal',.047,.035))
    for i in range(3):
        p.append(surface('jaw_intake_'+str(i),[[-1.65+i*.18,-1.6+i*.48],[1.65-i*.18,-1.6+i*.48],[1.55-i*.18,-1.43+i*.48],[-1.55+i*.18,-1.43+i*.48]],-5.31,.14,'steel_dark',.04))
    for sign in [-1,1]:
        p += [surface('red_optic_louver_'+str(sign),mirrored([[.4,-5.8],[2.9,-5.9],[3.05,-5.62],[.4,-5.48]],sign),-5.2,.14,'lens_red',.045),
          side_plate('ryuki_red_temple_'+str(sign),[[-7.1,-2.25],[-7.1,1.8],[-5.6,3.8],[-1.9,3.4],[-1.6,.5],[-2.3,-1.9]],sign*3.95-.14,.28,'ryuki_red'),
          side_plate('temple_silver_disc_'+str(sign),[[-4.1,-.8],[-4.1,.5],[-3.3,1.1],[-2.5,.5],[-2.5,-.8],[-3.3,-1.35]],sign*4.17-.12,.24,'ryuki_metal')]
        for i in range(3):p.append(box('temple_radiator_'+str(sign)+'_'+str(i),[sign*4.24-.08,-5.4+i*.45,1.25],[.16,.18,1.4],'steel_dark'))
    p += [surface('rear_upper_silver_plate',[[-3,-7.25],[3,-7.25],[3.65,-5.9],[3.3,-2.4],[-3.3,-2.4],[-3.65,-5.9]],3.7,.35,'ryuki_metal',0),box('rear_red_spine',[-.43,-7.5,4.08],[.86,5.5,.3],'ryuki_red'),box('rear_black_nape',[-2.7,-1.65,3.25],[5.4,.6,.45],'carbon')]
    return p


def knight_mask():
    p=[hull('sealed_faceted_knight_shell',[(-8,6.6,7.2,.95),(-6.8,8.15,8.35,1.1),(-2.1,8.05,8.05,1),(.8,5.8,6.4,.95)],'knight_navy'),
       surface('knight_deep_visor',[[-3.85,-6.8],[-1.1,-6.6],[0,-6],[1.1,-6.6],[3.85,-6.8],[4.2,-4.5],[3.7,-1.45],[0,-.7],[-3.7,-1.45],[-4.2,-4.5]],-4.6,.35,'black',.055),
       surface('bat_visor_upper_frame',[[-4.25,-6.95],[-1.15,-6.7],[-.5,-7.1],[.5,-7.1],[1.15,-6.7],[4.25,-6.95],[4.1,-6.27],[1.2,-6.02],[0,-6.33],[-1.2,-6.02],[-4.1,-6.27]],-4.85,.4,'knight_metal',.055,.035),
       surface('bat_visor_lower_frame',[[-4.15,-2.2],[-3,-1.7],[0,-.7],[3,-1.7],[4.15,-2.2],[3.95,-1.45],[0,.04],[-3.95,-1.45]],-4.78,.5,'knight_metal',.06,.05),
       surface('black_lower_bat_mask',[[-3.5,-1.1],[0,-.2],[3.5,-1.1],[2.7,.6],[0,1.25],[-2.7,.6]],-4.45,.65,'knight_navy',.06),
       surface('silver_bat_jaw',[[-2.85,-1.05],[-1.65,-1.25],[0,-.45],[1.65,-1.25],[2.85,-1.05],[2.65,-.3],[1.4,.2],[.8,-.5],[0,.55],[-.8,-.5],[-1.4,.2],[-2.65,-.3]],-4.9,.38,'knight_metal',.04,.08),
       surface('central_silver_nose',[[-.33,-6.4],[.33,-6.4],[.42,-.85],[0,-.58],[-.42,-.85]],-5.07,.3,'knight_metal',0),
       surface('blue_forehead_gem',[[-.36,-7.65],[0,-8],[.36,-7.65],[.3,-6.9],[0,-6.55],[-.3,-6.9]],-4.57,.35,'blue_edge',0,.18)]
    for i in range(10):
        x=-3.85+i*.85;edge=abs(x)/4;top=-6.15-edge*.32;bottom=-1.02-edge*.6
        p.append(surface('knight_silver_face_cage_rib_'+str(i),[[x-.14,top],[x+.14,top],[x+.14,bottom],[x-.14,bottom]],-5,.25,'knight_metal',.055,.02))
    for sign in [-1,1]:
        points=[[.68,-6.8],[1.2,-7.6],[2.7,-8.15],[3.75,-8.9],[4.4,-12.2],[4.62,-13.4],[4.86,-11.4],[4.5,-7.5],[4.4,-2.15],[3.86,-1.6],[3.9,-6.7],[2,-6.2]]
        p.append(surface('swept_bat_crown_frame_'+str(sign),mirrored(points,sign),-4.6,.7,'knight_metal',.045,.06))
        p.append(surface('gold_brow_piping_'+str(sign),mirrored([[.28,-8],[1.7,-8.4],[3.55,-7.5],[3.55,-7.28],[1.65,-8.1],[.3,-7.7]],sign),-4.6,.14,'gold',.035,.025))
        p += [side_plate('knight_silver_side_band_'+str(sign),[[-6.5,-2.8],[-6.6,.8],[-5.8,3.1],[-1.4,2.8],[-.8,.5],[-2.5,-1.4]],sign*3.99-.17,.34,'knight_metal'),side_plate('knight_dark_side_inset_'+str(sign),[[-6.15,-1.8],[-6.1,.5],[-4.8,2.45],[-2.4,1.8],[-2,.5],[-3.4,-.8]],sign*4.19-.1,.2,'knight_navy')]
    p += [box('rear_silver_crown_spine',[-.42,-7.6,3.95],[.84,6.5,.34],'knight_metal'),surface('rear_silver_nape',[[-3.2,-2.6],[0,-1.8],[3.2,-2.6],[2.8,-1.3],[0,-.7],[-2.8,-1.3]],3.5,.35,'knight_metal',0)]
    return p


def decade_mask():
    p=[hull('sealed_faceted_decade_shell',[(-8.5,6.7,7.5,1),(-7,8.2,8.55,.9),(-1.8,8.25,8.3,.9),(.7,6.6,6.7,.95)],'decade_magenta'),
       surface('decade_black_eye_surround',[[-4.05,-6.6],[4.05,-6.6],[4.3,-4.6],[3.45,-1.6],[-3.45,-1.6],[-4.3,-4.6]],-4.63,.42,'black',.047)]
    eye=[[.6,-6.2],[2.8,-6.45],[3.85,-5.8],[4,-3.4],[3.1,-2.1],[.6,-2.55]]
    for sign in [-1,1]:
        p.append(surface('green_compound_eye_'+str(sign),mirrored(eye,sign),-5,.6,'optic_green',.048,.08))
        p.append(surface('magenta_cheek_'+str(sign),mirrored([[2.5,-2.2],[3.9,-2.8],[3.5,-.25],[2.2,.55],[1.65,-.05]],sign),-4.62,.55,'decade_magenta',.06,.08))
        p += [side_plate('black_temple_inset_'+str(sign),[[-6.3,-2.8],[-6.1,1],[-4.5,2.7],[-2,2.8],[-1.7,0],[-3.8,-1.6]],sign*4.03-.13,.26,'carbon'),box('rear_side_magenta_trim_'+str(sign),[sign*3.25-.2,-5.7,3.85],[.4,4,.3],'decade_magenta')]
        for i in range(3):p.append(box('decade_temple_reader_groove_'+str(sign)+'_'+str(i),[sign*4.24-.08,-3.7+i*.5,.15],[.16,.18,1.8],'steel_dark'))
    # The central tall barcode is continuous over the brow and crown, while the
    # outer blades step down. Front profiles follow the faceted green lenses.
    for i in range(7):
        x=-3.45+i*1.15;edge=abs(i-3);top=-10.8+edge*.6;front=-5.5+abs(x)*.09
        p.append(plate('barcode_blade_'+str(i),[[x-.18,top],[x+.18,top],[x+.18,-.3],[x-.18,-.3]],front,.36,'black'))
        p.append(box('barcode_over_crown_'+str(i),[x-.18,top,-3.7],[.36,-8.2-top,6.6],'black'))
    p += [box('forehead_amber_reader',[-.33,-8.18,-5.94],[.66,.5,.3],'lens_amber'),surface('black_mouth_grille',[[-2.6,-1.65],[2.6,-1.65],[1.7,.45],[-1.7,.45]],-4.9,.35,'black',.03),box('silver_mouth_crossbar',[-2.2,-.65,-5.1],[4.4,.33,.25],'chrome'),box('rear_black_reader_panel',[-2.3,-6.4,4.13],[4.6,4.1,.27],'carbon'),box('rear_magenta_spine',[-.44,-7.7,4.48],[.88,6.1,.22],'decade_magenta')]
    for i in range(3):p.append(box('rear_reader_seam_'+str(i),[-1.6,-4.2+i*.65,4.43],[3.2,.17,.18],'steel_dark'))
    return p


def limb_details(p,family,color,metal):
    for side in ['left','right']:
        left=side=='left';ax=-1 if left else -3;arm=p[side+'_arm'];leg=p[side+'_leg']
        arm += [bevel_plate(side+'_sculpted_bicep',[[ax-.9,1.2],[ax+2.9,1.2],[ax+2.7,4.5],[ax-.7,4.5]],-2.35,.55,color,.06),bevel_plate(side+'_vambrace',[[ax-.95,5.4],[ax+2.95,5.4],[ax+2.7,8.3],[ax-.7,8.3]],-2.6,.6,color,.08),box(side+'_forearm_inner_gap',[ax-.1,5.5,-2.75],[.2,2.3,.16],'steel_dark'),box(side+'_elbow_hinge',[ax-.7,4.2,2.08],[3.4,.6,.24],'carbon')]
        for i in range(3):arm.append(box(side+'_arm_underjoint_'+str(i),[ax-.75,3.7+i*.35,-2.24],[3.5,.12,.18],'carbon'))
        arm += [box(side+'_rear_glove_seam',[ax-.75,9.6,2.23],[3.5,.2,.2],'steel_dark'),box(side+'_rear_wrist_trim',[ax-.9,8.7,2.3],[3.8,.35,.24],metal)]
        leg += [bevel_plate(side+'_front_thigh_panel',[[-1.85,.6],[1.85,.6],[1.6,4.1],[-1.6,4.1]],-2.35,.35,color,.08),badge(side+'_knee_silver_hinge',5.6,-2.95,1.25,metal),badge(side+'_knee_inner_axle',5.6,-3.23,.72,'steel_dark'),badge(side+'_knee_center_bolt',5.6,-3.44,.3,metal),bevel_plate(side+'_shin_front_panel',[[-1.65,6.7],[1.65,6.7],[1.45,10.4],[-1.45,10.4]],-2.5,.45,color,.07),box(side+'_rear_calf_seam',[-.18,7.1,2.27],[.36,2.85,.24],'steel_dark')]
        for i in range(3):leg.append(box(side+'_boot_lace_seam_'+str(i),[-1.55,10.6+i*.32,-3.14],[3.1,.12,.15],'steel_dark'))


def refine_armor(data):
    data['materials'].update({'ryuki_metal':[166,176,179],'ryuki_red':[172,21,24],'knight_metal':[150,166,181],'knight_navy':[12,31,50],'decade_magenta':[192,17,107],'optic_green':[70,182,53],'dragon_scale':[166,19,22],'bat_membrane':[11,24,35],'crest_ryuki':[126,14,19],'crest_knight':[11,31,46]})
    masks={'ryuki':ryuki_mask(),'knight':knight_mask(),'decade':decade_mask()}
    for family in ['ryuki','knight','decade']:
        p=data['forms'][family];p['head']=masks[family]
        color={'ryuki':'ryuki_red','knight':'knight_navy','decade':'decade_magenta'}[family];metal='ryuki_metal' if family=='ryuki' else 'knight_metal' if family=='knight' else 'chrome'
        # Preserve only the woven base, belt and rear assembly before building
        # the photographed chest shape. The rejected generic V-strip is removed.
        if family=='ryuki':
            p['body']=[x for x in p['body'] if x['name'].startswith(('woven_','belt_','waist_','rear_','back_'))]
            for sign in [-1,1]:
                xy=mirrored([[.25,.5],[2.8,.15],[4.35,1.1],[4.1,3.65],[2.9,4.6],[.4,4.25]],sign)
                p['body'] += [bevel_plate('ryuki_silver_pectoral_'+str(sign),xy,-3.45,1.05,metal,.12),plate('black_pectoral_seam_'+str(sign),mirrored([[.35,4.3],[2.9,4.65],[4.1,3.75],[4.1,4.15],[2.8,5.05],[.3,4.65]],sign),-3.55,.25,'carbon')]
            p['body'] += [box('silver_sternum',[-.28,.7,-3.7],[.56,4.5,.45],metal),bevel_plate('silver_abdominal_cuirass',[[-2.8,4.9],[2.8,4.9],[2.5,7.2],[-2.5,7.2]],-3.03,.7,metal,.08)]
            for i in range(3):p['body'].append(box('abdominal_armor_louver_'+str(i),[-2.2,5.45+i*.55,-3.8],[4.4,.17,.15],'steel_dark'))
            for sign in [-1,1]:p['body'].append(box('red_lower_chest_harness_'+str(sign),[sign*3.5-.6,5.2,-2.8],[1.2,3,.65],color))
        elif family=='knight':
            p['body']=[x for x in p['body'] if x['name'].startswith(('woven_','belt_','waist_','rear_','back_','neck_'))]
            p['body'] += [bevel_plate('knight_silver_breastplate',[[-4.5,.2],[-2.9,-.15],[0,1.3],[2.9,-.15],[4.5,.2],[4.15,4.1],[0,6.9],[-4.15,4.1]],-3.3,.9,metal,.085),bevel_plate('knight_navy_sternum',[[-.55,.9],[.55,.9],[.45,6.2],[0,6.55],[-.45,6.2]],-4.3,.25,color,.04)]
            for sign in [-1,1]:
                for i in range(3):
                    x=1.1+i*.92
                    p['body'].append(plate('navy_chest_rib_'+str(sign)+'_'+str(i),mirrored([[x,.7-i*.24],[x+.4,.5-i*.24],[x*.68,5.8-i*.63],[x*.68-.35,5.9-i*.63]],sign),-4.3,.23,color))
            for i in range(7):p['body'].append(plate('navy_abdominal_pleat_'+str(i),[[i*.75-2.55,6.3],[i*.75-2.25,6.3],[i*.55-1.8,9.1],[i*.55-2,9.1]],-2.65,.2,'navy_edge'))
        else:
            p['body']=[x for x in p['body'] if x['name'] not in ['white_cross_band','black_cross_band','black_chest_plate','white_vertical_chest_band']]
            p['body'] += [bevel_plate('black_sculpted_decade_chest',[[-3.2,.4],[3.2,.4],[3.5,5.2],[1.5,7.2],[-1.5,7.2],[-3.5,5.2]],-3.3,.8,'carbon',.08),plate('white_diagonal_chest_frame',[[-4.3,4.5],[4.4,.3],[4.4,1.55],[-4.3,5.75]],-4.22,.3,'white'),plate('black_diagonal_chest_stripe',[[-4.3,4.85],[4.4,.65],[4.4,1.15],[-4.3,5.35]],-4.55,.15,'black'),plate('white_sternum_frame',[[.6,.5],[1.65,.5],[1.3,6.5],[.25,6.5]],-4.15,.3,'white'),plate('black_sternum_inlay',[[.95,.5],[1.3,.5],[.95,6.4],[.6,6.4]],-4.5,.12,'black')]
            for i in range(3):p['body'].append(box('black_abdominal_flex_'+str(i),[-2.3,6.8+i*.7,-2.5],[4.6,.4,.35],'carbon'))
        limb_details(p,family,color,metal)
        # Square shoulder caps with physical bevels and a darker under-shell.
        for side in ['left','right']:
            left=side=='left';ax=-1.4 if left else -4.2
            p[side+'_arm']=[x for x in p[side+'_arm'] if not any(word in x['name'] for word in ['pauldron','shoulder'])]
            p[side+'_arm'] += [box(side+'_square_shoulder_underplate',[ax,-2.3,-2.45],[5.6,2.85,4.9],'carbon'),bevel_plate(side+'_square_shoulder_front',[[ax,-2.35],[ax+5.6,-2.35],[ax+5.6,.15],[ax+5.1,.6],[ax+.4,.6],[ax,.15]],-3.02,.75,color if family=='decade' else metal,.08),box(side+'_square_shoulder_top',[ax+.25,-2.7,-2.5],[5.1,.55,5.05],color if family=='decade' else metal),bevel_plate(side+'_square_shoulder_rear',[[ax+.2,-2.2],[ax+5.4,-2.2],[ax+5.35,.4],[ax+.25,.4]],2.5,.6,color if family=='decade' else metal,.07)]
            if family=='ryuki':p[side+'_arm'].append(box(side+'_black_shoulder_inset',[ax+.8,-1.8,-3.2],[4,.85,.2],'carbon'))
            elif family=='knight':
                p[side+'_arm'].append(bevel_plate(side+'_swept_shoulder_fin',[[ax+.5,-1.5],[ax+1.8,-4.6],[ax+2.55,-2.6],[ax+4.8,-.5],[ax+4.2,.2],[ax+1.1,.2]],-2.75,.6,color,.08))
            else:
                p[side+'_arm'] += [box(side+'_white_shoulder_border',[ax,-2.68,-3.22],[5.6,.58,.3],'white'),box(side+'_black_shoulder_band',[ax,-1.7,-3.29],[5.6,.45,.25],'black')]
        # Recolor the inherited undersuit panels consistently, without touching
        # the belt's authentic red/blue/gold card symbols.
        for bone,parts in p.items():
            for part in parts:
                if part['name'].startswith(('belt_','waist_')):continue
                if part['material'] in ['dragon_red','magenta','navy']:part['material']=color
    for side in ['left','right']:
        for part in data['forms']['decade'][side+'_leg']:
            if any(k in part['name'] for k in ['front_thigh_panel','shin_front_panel']):part['material']='carbon'
        for part in data['forms']['ryuki'][side+'_leg']:
            if any(k in part['name'] for k in ['_shin','shin_front_panel']):part['material']='carbon'
        data['forms']['ryuki'][side+'_leg'].append(plate(side+'_red_boot_chevron',[[-1.75,7.4],[0,8.5],[1.75,7.4],[1.75,8.1],[0,9.25],[-1.75,8.1]],-3.03,.27,'ryuki_red'))
        data['forms']['decade'][side+'_leg']=[part for part in data['forms']['decade'][side+'_leg'] if not any(k in part['name'] for k in ['knee_silver_hinge','knee_inner_axle','knee_center_bolt'])]
    return data


def add(a,b):return [a[i]+b[i] for i in range(3)]
def sub(a,b):return [a[i]-b[i] for i in range(3)]
def mul(a,s):return [v*s for v in a]
def norm(v):
    d=max(.00001,math.sqrt(sum(x*x for x in v)));return [x/d for x in v]
def cross(a,b):return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]


def beam(name,a,b,width,depth,material):
    d=norm(sub(b,a));side=norm(cross(d,[0,0,1] if abs(d[2])<.8 else [0,1,0]));up=norm(cross(side,d));verts=[]
    for c in [a,b]:
        for s,t in [(-1,-1),(1,-1),(1,1),(-1,1)]:verts.append(add(c,add(mul(side,s*width/2),mul(up,t*depth/2))))
    return {'name':name,'vertices':verts,'faces':[[3,2,1,0],[4,5,6,7],[0,1,5,4],[1,2,6,5],[2,3,7,6],[3,0,4,7]],'material':material}


def segmented_dragon(mechanical=False):
    p=armor_skeleton();body=p['body'];family='dragranzer' if mechanical else 'dragreder'
    # S-curve silhouette from the supplied dragon photographs, not a closed ring.
    centers=[[-8,11,10],[-11,7,9],[-11,2,8],[-8,-3,7],[-3,-7,6],[2,-11,5],[5,-16,4],[5,-21,2],[2,-25,-1],[-2,-26,-5],[-5,-23,-8]]
    for i in range(len(centers)-1):
        a,b=centers[i],centers[i+1];d=norm(sub(b,a));s=norm(cross(d,[0,0,1]));u=norm(cross(s,d));radius=1.25+min(i,5)*.22
        for face in range(8):
            angle0=(face+.5)*math.pi/4;angle1=(face+1.5)*math.pi/4
            verts=[]
            for center in [a,b]:
                for angle in [angle0,angle1]:verts.append(add(center,add(mul(s,math.cos(angle)*radius),mul(u,math.sin(angle)*radius))))
            # The silver ventral armor occupies the inward/front arc; red armored
            # side facets and narrow gold collars preserve the original contrast.
            mat='ryuki_metal' if face in [5,6] else 'dragon_scale'
            body.append({'name':family+'_armored_segment_'+str(i)+'_facet_'+str(face),'vertices':verts,'faces':[[0,1,3,2]],'material':mat})
        for face in range(8):
            angle0=(face+.5)*math.pi/4;angle1=(face+1.5)*math.pi/4;verts=[]
            for center in [a,add(a,mul(d,.38))]:
                for angle in [angle0,angle1]:verts.append(add(center,add(mul(s,math.cos(angle)*(radius+.1)),mul(u,math.sin(angle)*(radius+.1)))))
            body.append({'name':family+'_joint_collar_'+str(i)+'_'+str(face),'vertices':verts,'faces':[[0,1,3,2]],'material':'gold'})
        mid=mul(add(a,b),.5)
        # Plate-like dorsal fins follow the joint tangent rather than standing
        # as repeated white posts along a horizontal coil.
        base=add(mid,mul(u,radius*.9));tip=add(base,mul(u,1.45 if i<8 else .8))
        body.append(beam('dorsal_fin_'+str(i),base,tip,1.25,.5,'gold' if mechanical else 'chrome'))
        for j in range(2):body.append(beam('ventral_rib_'+str(i)+'_'+str(j),add(a,add(mul(d,1+j*.9),mul(s,-radius*.75))),add(a,add(mul(d,1+j*.9),mul(s,radius*.75))),.25,.32,'steel_dark'))
    # Distinct mechanical dragon head: angular snout, open jaw, paired eyes,
    # swept silver horns, cheek fins, individual teeth and gold brow armor.
    offset=[-5,-23,-8]
    head=[hull('dragon_skull', [(-3,4.7,5.2,.6),(-1.8,6,6.3,.65),(1.7,5.9,6.1,.7),(2.6,4.8,4.9,.6)],'dragon_scale')]
    for part in head:
        part['vertices']=[add(v,offset) for v in part['vertices']];body.append(part)
    body += [box('angular_dragon_snout',[-7.4,-24.25,-16],[4.8,2.3,7],'dragon_scale'),box('silver_dragon_nose_cap',[-7.6,-24.4,-16.3],[5.2,.7,1],'ryuki_metal'),box('black_open_dragon_mouth',[-7.1,-21.9,-15.7],[4.2,2.7,6.1],'black'),beam('lower_silver_jaw',[-5,-20.2,-8.1],[-5,-18.6,-16.1],4.4,.65,'ryuki_metal'),beam('red_lower_jaw_shell',[-5,-19.6,-8.1],[-5,-17.9,-15.1],4.7,.8,'dragon_scale')]
    for sign in [-1,1]:
        x=-5+sign*2.8
        body += [box('dragon_amber_eye_'+str(sign),[x-.27,-24,-11.9],[.54,1,1.8],'lens_amber'),beam('silver_swept_horn_'+str(sign),[-5+sign*1.9,-25,-8],[-5+sign*3.1,-29.9,-4.5],.8,.7,'ryuki_metal'),beam('gold_brow_guard_'+str(sign),[-5+sign*2.3,-24.7,-12.3],[-5+sign*3,-24,-8.8],.5,.5,'gold'),beam('dragon_cheek_spike_'+str(sign),[-5+sign*2.4,-22.4,-8.1],[-5+sign*4,-21.2,-5.2],.8,.6,'chrome')]
        for i in range(5):
            z=-15.1+i*1.1
            body += [beam('upper_tooth_'+str(sign)+'_'+str(i),[-5+sign*1.8,-21.85,z],[-5+sign*1.65,-20.9,z-.2],.45,.5,'chrome'),beam('lower_tooth_'+str(sign)+'_'+str(i),[-5+sign*1.65,-18.8,z],[-5+sign*1.6,-20.1,z-.1],.38,.4,'chrome')]
    for sign in [-1,1]:
        body += [beam('dragon_silver_swept_crown_root_'+str(sign),[-5+sign*2,-25,-8],[-5+sign*3.2,-28.7,-3.8],1.05,.7,'ryuki_metal'),beam('dragon_swept_crown_tip_'+str(sign),[-5+sign*3.2,-28.7,-3.8],[-5+sign*3.4,-29.6,.5],.55,.45,'chrome'),beam('dragon_silver_eyebrow_'+str(sign),[-5+sign*1.6,-24.8,-15.5],[-5+sign*3.15,-25.4,-11.3],.65,.55,'chrome')]
        for vent in range(3):
            body.append(box('dragon_cheek_radiator_'+str(sign)+'_'+str(vent),[-5+sign*2.9-.09,-23+vent*.5,-9.1],[.18,.2,1.9],'steel_dark'))
    # Articulated dragon limbs are placed at two body stations.
    for index in [4,7]:
        a=centers[index]
        for sign in [-1,1]:
            shoulder=add(a,[sign*1.9,0,0]);elbow=add(a,[sign*4,1.5,-.5]);palm=add(a,[sign*4.6,3,-1.5])
            body += [beam('dragon_upper_limb_'+str(index)+'_'+str(sign),shoulder,elbow,1.5,1.5,'dragon_scale'),beam('dragon_forelimb_'+str(index)+'_'+str(sign),elbow,palm,1,1,'ryuki_metal')]
            for digit in range(3):body.append(beam('dragon_claw_'+str(index)+'_'+str(sign)+'_'+str(digit),add(palm,[(digit-1)*.55,0,0]),add(palm,[(digit-1)*.65,1,-1]),.35,.35,'chrome'))
    if mechanical:
        for sign in [-1,1]:
            a=centers[7]
            body.append(plate('dragranzer_gold_wing_'+str(sign),[[a[0]+sign*1.4,a[1]-1],[a[0]+sign*9,a[1]-3.2],[a[0]+sign*10.2,a[1]+.8],[a[0]+sign*3.7,a[1]+3.5]],a[2],.8,'gold'))
            body += [beam('dragranzer_engine_cannon_'+str(sign),add(a,[sign*4.5,1,2]),add(a,[sign*4.5,1,-6]),1.4,1.4,'steel'),beam('dragranzer_gold_rail_'+str(sign),add(a,[sign*3.9,-1,2]),add(a,[sign*3.9,-1,-5.8]),.5,.5,'gold')]
    # Tail terminates in a tapered silver/red lance.
    body.append(beam('dragon_tail_lance',centers[0],add(centers[0],[2,4,1]),.7,.7,'chrome'))
    return p


def darkwing_refined():
    p=armor_skeleton();parts=p['body']
    parts += [hull('darkwing_robot_torso',[(-9,3.6,3.5,.65),(-7.5,5.4,4.4,.75),(-2,4.9,4.2,.65),(1.6,2.9,3.1,.5)],'knight_navy'),bevel_plate('darkwing_silver_chest',[[-2.1,-6.9],[0,-5.9],[2.1,-6.9],[1.7,-2.9],[0,-1.9],[-1.7,-2.9]],-2.8,.65,'knight_metal',.15),box('darkwing_mechanical_neck',[-1,-10.1,-1.2],[2,1.9,2.4],'steel_dark'),hull('darkwing_skull',[(-13.1,2.7,2.9,.4),(-11.8,3.6,3.6,.5),(-9.1,3,3.3,.5)],'knight_navy'),surface('darkwing_left_red_eye',[[-1.55,-11.7],[-.25,-11.35],[-.3,-10.8],[-1.45,-11]],-2.1,.3,'lens_red',0),surface('darkwing_right_red_eye',[[1.55,-11.7],[.25,-11.35],[.3,-10.8],[1.45,-11]],-2.1,.3,'lens_red',0),plate('darkwing_silver_beak',[[-.65,-10.7],[.65,-10.7],[0,-9.4]],-2.2,.45,'knight_metal'),plate('darkwing_gold_forehead',[[0,-13.4],[.55,-12.6],[0,-11.7],[-.55,-12.6]],-2,.3,'gold')]
    for i in range(3):parts.append(box('darkwing_chest_scale_'+str(i),[-1.35,-4.9+i*.9,-3.1],[2.7,.5,.32],'steel'))
    for sign in [-1,1]:
        root=[sign*2.4,-7.1,.3];elbow=[sign*8.7,-13,.3];tip=[sign*22,-8.8,1]
        points=[[sign*2.3,-7.1],[sign*8.6,-12.7],[sign*21.7,-8.8],[sign*17.2,-7.3],[sign*15.5,-3.5],[sign*13.5,-5.1],[sign*10.9,-1.4],[sign*8.9,-3.1],[sign*6.9,.7],[sign*4.6,-1.8],[sign*2.2,.6]]
        parts += [bevel_plate('darkwing_scalloped_membrane_'+str(sign),points,.2,.38,'bat_membrane',.018),beam('darkwing_wing_humerus_'+str(sign),root,elbow,.8,.9,'knight_metal'),beam('darkwing_wing_leading_edge_'+str(sign),elbow,tip,.6,.75,'knight_metal')]
        for endpoint in [[sign*15.5,-3.5,.05],[sign*10.9,-1.4,.05],[sign*6.9,.7,.05]]:
            parts.append(beam('darkwing_finger_spar_'+str(sign)+'_'+str(endpoint[0]),elbow,endpoint,.26,.32,'steel_dark'))
        parts += [plate('darkwing_swept_ear_'+str(sign),[[sign*.7,-12.6],[sign*1.8,-16.5],[sign*2.3,-15],[sign*1.8,-11.4]],-.35,.75,'knight_navy'),beam('darkwing_leg_femur_'+str(sign),[sign*1.8,-.2,0],[sign*3,2.5,-1.3],1.2,1.4,'knight_navy'),beam('darkwing_leg_tibia_'+str(sign),[sign*3,2.5,-1.3],[sign*2.6,4.8,-3],.9,1,'knight_metal')]
        for digit in range(3):parts.append(beam('darkwing_claw_'+str(sign)+'_'+str(digit),[sign*2.6+(digit-1)*.55,4.5,-3],[sign*2.6+(digit-1)*.7,5.3,-4.6],.35,.45,'chrome'))
    parts += [box('darkwing_rear_spine',[-.4,-7.4,2.3],[.8,6.9,.3],'steel'),box('darkwing_rear_central_armor',[-1.5,-6.7,2.1],[3,4.5,.4],'knight_navy')]
    return p


def refine(data):
    refine_armor(data)
    for name,family in [('ryuki_belt','ryuki'),('knight_belt','knight')]:
        data['items'][name]=[part for part in data['items'][name] if not part['name'].startswith(('dragon_emblem_','bat_emblem_'))]
        data['items'][name].append({'name':'painted_'+family+'_contract_crest','from':[5.55,5.65,5.17],'to':[10.45,10.15,5.49],'material':'crest_'+family})
    # Physical belt geometry is generated from the same painted card deck.
    for family in ['ryuki','knight']:
        data['forms'][family]['body']=[part for part in data['forms'][family]['body'] if not part['name'].startswith(('belt_dragon_emblem_','belt_bat_emblem_'))]
        data['forms'][family]['body'].append(box('belt_painted_contract_crest',[-1.3965,9.0745,-4.5131],[2.793,2.565,.1824],'crest_'+family))
    data['forms'].update({'dragreder':segmented_dragon(),'dragranzer':segmented_dragon(True),'darkwing':darkwing_refined()})
    data['references']['card_rider_revision']='Geometry revision2 after explicit user rejection: faceted sealed helmets, corrected silver Ryuki chest, wrapped Knight cage/jaw, integral Decade barcode, S-curve articulated dragons, scalloped Darkwing wings. Pending native and user visual review.'
