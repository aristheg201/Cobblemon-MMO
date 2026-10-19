"""Rebuild the rejected armor from the five 2026-10-18 user references.

Normal Ryuki: red limbs, black Card Deck and V-shaped silver cage.
Survive: separate black suit/crimson-gold cuirass and crown, not a recolor.
Decade: the supplied Minecraft screenshot defines proportions and pixel panels.
"""
from rider_reference_models import box, plate, side_plate, armor_skeleton
from card_rider_models import append_belt, b
from refine_card_riders import hull

MATERIALS={
 'pixel_ruby':[186,17,34], 'pixel_ruby_light':[219,30,46], 'pixel_ruby_dark':[119,12,24],
 'pixel_crimson':[120,9,34], 'pixel_crimson_light':[163,14,45],
 'pixel_black':[23,25,29], 'pixel_silver':[190,201,211],
 'pixel_silver_dark':[102,117,128], 'pixel_silver_light':[229,236,240],
 'pixel_gold':[211,160,43], 'pixel_gold_light':[245,207,91], 'pixel_gold_dark':[140,96,25],
 'pixel_magenta':[215,11,119], 'pixel_magenta_dark':[133,7,70],
 'pixel_lens_red':[225,13,28], 'pixel_lens_green':[65,166,40],
 'pixel_deck_black':[14,21,31], 'pixel_deck_survive':[130,11,36],
 'pixel_decadriver':[221,228,230],
}
S='pixel_silver';SL='pixel_silver_light';SD='pixel_silver_dark';K='pixel_black';R='pixel_ruby';RD='pixel_ruby_dark';G='pixel_gold';GL='pixel_gold_light';GD='pixel_gold_dark';C='pixel_crimson';M='pixel_magenta'

def mirror(xy,sign):return [[x*sign,y] for x,y in xy]

def silhouette(name,xy,z,material,depth=.24):return plate(name,xy,z,depth,material)

def shell(name):
 return [hull(name,[(-8.1,7.3,7.7,.45),(-7.65,8.05,8.05,.45),(-1.0,8.05,8.05,.45),(.15,6.9,7.15,.45)],K)]

def cage(survive=False):
 p=shell('sealed_survive_black_helmet' if survive else 'sealed_normal_ryuki_helmet')
 for sign in [-1,1]:
  p += [silhouette('full_red_compound_eye_'+str(sign),mirror([[.25,-6.6],[2.65,-7],[3.65,-6.3],[3.85,-4.5],[3.1,-2.6],[.45,-2.4]],sign),-4.62,'pixel_lens_red',.4)]
  for row in range(6):
   y=-7.2+row*.71
   p.append(silhouette('silver_v_cage_'+str(sign)+'_'+str(row),mirror([[.0,y+.83],[2.1,y+.44],[4.05,y],[4.05,y+.31],[2.1,y+.75],[0,y+1.14]],sign),-4.98,S,.27))
   # Physical temple rails wrap rearward, ending at staggered side attachment points.
   end=(3.25-row*.23) if survive else (1.35-row*.16)
   p.append(side_plate('wrapped_temple_rail_'+str(sign)+'_'+str(row),[[y,-4.55],[y+.3,-4.55],[y+.55,end],[y+.23,end]],sign*4.04-.12,.24,S))
 p += [silhouette('silver_pointed_dragon_crusher',[[-3.1,-2.5],[0,-1.8],[3.1,-2.5],[2.7,-.8],[1.3,.35],[0,.7],[-1.3,.35],[-2.7,-.8]],-4.76,S,.42)]
 for i in range(3):
  y=-1.8+i*.57;w=2.1-i*.36
  p.append(silhouette('crusher_black_v_vent_'+str(i),[[-w,y],[0,y+.28],[w,y],[w-.16,y+.19],[0,y+.49],[-w+.16,y+.19]],-4.99,K,.14))
 p += [box('black_jaw_bottom',[-1.4,.35,-4.23],[2.8,.3,.5],K),box('rear_crown_silver_seam',[-.23,-7.7,4.05],[.46,6.4,.2],S),silhouette('rear_nape_silver_wedge',[[-1.5,.1],[0,-3.1],[1.5,.1]],3.86,S,.22)]
 if not survive:
  for sign in [-1,1]:
   p.append(silhouette('short_silver_brow_crown_'+str(sign),mirror([[0,-7.03],[1.9,-7.5],[3.1,-8.35],[3.6,-8.95],[3.75,-7.65],[2.25,-6.85],[0,-6.4]],sign),-4.8,SL,.3))
   p.append(side_plate('normal_temporal_silver_mount_'+str(sign),[[-3.6,.8],[-3.6,2.25],[-1.8,2.25],[-1.6,.8]],sign*4.06-.15,.3,SD))
  p += [silhouette('normal_ryuki_forehead_crest',[[-.65,-8.25],[0,-8.65],[.65,-8.25],[.45,-7.25],[0,-6.95],[-.45,-7.25]],-4.55,S,.35),box('normal_forehead_red_jewel',[-.17,-8.12,-4.95],[.34,.38,.13],'pixel_lens_red')]
 else:
  for sign in [-1,1]:
   crown=[[0,-6.7],[.8,-7.35],[2.7,-8.1],[4.4,-9.15],[5.7,-10.7],[5.4,-8.2],[4.25,-7.15],[2.0,-6.45],[.35,-5.9]]
   p.append(silhouette('survive_gold_swept_crown_'+str(sign),mirror(crown,sign),-4.91,G,.42))
   p.append(silhouette('survive_crown_lit_edge_'+str(sign),mirror([[.2,-6.7],[2.8,-8.0],[5.55,-10.4],[5.05,-8.7],[2.7,-7.5],[.3,-6.3]],sign),-4.99,GL,.13))
   # The original crown sweeps around both temples into rear-facing fins.
   # A frontal gold silhouette alone disappears from the side in Minecraft.
   sweep=[[-7.25,-4.55],[-7.6,-1.8],[-8.7,2.2],[-9.65,4.15],[-7.55,3.55],[-6.95,1.0],[-6.8,-4.35]]
   p.append(side_plate('survive_swept_gold_temple_'+str(sign),sweep,sign*4.12-.18,.36,G))
   p.append(side_plate('survive_temple_lit_ridge_'+str(sign),[[-7.25,-4.55],[-7.6,-1.8],[-8.7,2.2],[-9.65,4.15],[-9.18,3.7],[-8.38,2.05],[-7.25,-1.7],[-6.95,-4.4]],sign*4.12-.22,.44,GL))
   # Gold antennae are deliberately thin, stepped and separate from the broad crown.
   for i,(x,y,h) in enumerate([(1.08,-10.05,2.05),(1.31,-11.7,1.8),(1.58,-13.1,1.55)]):
    p.append(box('survive_fine_antenna_'+str(sign)+'_'+str(i),[sign*x-.075,y,-4.27],[.15,h,.16],GL))
   p.append(silhouette('survive_forehead_tribal_branch_'+str(sign),mirror([[.25,-7.7],[.9,-8.0],[1.0,-8.8],[1.48,-9.2],[1.43,-8.45],[2.1,-8.7],[1.85,-8.05],[1.05,-7.6],[.35,-7.2]],sign),-4.42,G,.17))
  p += [silhouette('survive_gold_center_nose',[[-.4,-7.2],[.4,-7.2],[.45,-5.9],[0,-5.65],[-.45,-5.9]],-5.18,G,.2),box('survive_forehead_ruby',[-.16,-8.15,-4.55],[.32,.34,.17],'pixel_lens_red')]
 return p

def decade_mask():
 p=[hull('sealed_voxel_decade_helmet',[(-8.1,7.5,7.8,.35),(-7.7,8.05,8.05,.35),(-.55,8.05,8.05,.35),(.2,7.1,7.4,.35)],M),box('black_eye_socket',[-4,-6.6,-4.18],[8,4.75,.28],K)]
 for sign in [-1,1]:
  eye=[[.45,-6.3],[2.9,-6.3],[2.9,-5.9],[3.8,-5.9],[3.8,-2.7],[3.25,-2.7],[3.25,-2.1],[.45,-2.1]]
  p.append(silhouette('voxel_green_lens_'+str(sign),mirror(eye,sign),-4.64,'pixel_lens_green',.37))
  p.append(side_plate('green_lens_side_return_'+str(sign),[[-5.8,-4.1],[-2.7,-4.1],[-2.7,-3.25],[-5.8,-3.25]],sign*4.04-.1,.2,'pixel_lens_green'))
  p += [side_plate('black_temple_reader_'+str(sign),[[-6.9,-2.6],[-6.9,1.7],[-2.0,2.5],[-.6,.9],[-1.8,-1.7]],sign*4.02-.1,.2,K),box('rear_magenta_edge_'+str(sign),[sign*3.3-.45,-7.1,3.98],[.9,6.7,.24],M)]
 for i in range(7):
  x=-3.6+i*1.2;top=-9.8+abs(i-3)*.34
  bottom=-.65 if abs(i-3)<3 else -2.2
  p += [box('integral_barcode_face_'+str(i),[x-.19,top,-5.04],[.38,bottom-top,.28],K),box('barcode_over_crown_'+str(i),[x-.19,top,-4.15],[.38,-8.05-top,8.25],K)]
 p += [box('magenta_chin_shell',[-2.7,-1.85,-4.25],[5.4,1.85,.43],M),box('black_mouth_bar',[-2.75,-1.1,-4.8],[5.5,.75,.26],K),box('decade_forehead_amber',[-.23,-8.02,-5.11],[.46,.46,.15],'lens_amber'),box('rear_black_reader',[-2.4,-6.25,4.09],[4.8,4.4,.18],K),box('rear_barcode_center',[-.23,-7.7,4.29],[.46,6.7,.16],M)]
 for x in [-2,-1,0,1,2]:p.append(box('separate_silver_mouth_tooth_'+str(x),[x-.27,-.95,-5.1],[.54,.38,.17],SL))
 for y in [-4.8,-3.5,-2.2]:p.append(box('rear_reader_slot_'+str(y),[-1.8,y,4.3],[3.6,.15,.1],'pixel_magenta_dark'))
 return p

def belt_items(kind):
 decade=kind=='decade';survive=kind=='survive'
 deck='pixel_decadriver' if decade else 'pixel_deck_survive' if survive else 'pixel_deck_black'
 p=[b('black_waist_band',[-.2,6.7,8],[16.2,9.3,9.3],K),b('white_receiver_backplate',[1.1,5.6,6.8],[14.9,10.4,8.4],SL)]
 if decade:
  p += [b('decadriver_main_white_housing',[2.5,5.1,6.1],[13.5,10.9,7.4],SL),b('driver_dark_slide',[.8,7,5.9],[15.2,9,6.5],K),b('driver_physical_icon_ring_frame',[4.3,4.6,5.65],[11.7,11.4,6.3],SD),b('nine_rider_emblems_and_red_core',[4.65,4.95,5.43],[11.35,11.05,5.76],deck)]
  for x in [1.6,13.4]:p += [b('driver_white_side_lock_'+str(x),[x,6.3,5.7],[x+1,9.7,6.8],SL),b('driver_magenta_side_switch_'+str(x),[x+.2,7,5.45],[x+.8,9,5.9],M)]
 else:
  p += [b('card_deck_black_recess',[4.15,4.7,5.9],[11.85,11.3,6.9],K),b('deck_physical_silver_frame',[4.4,4.9,5.45],[11.6,11.1,6.05],S),b('gold_dragon_crest_card_deck',[4.85,5.3,5.2],[11.15,10.7,5.6],deck),b('deck_top_card_slot',[5.4,10.8,5.1],[10.6,11.1,5.6],K)]
  for x in [2,12.5]:
   p += [b('silver_buckle_hinge_'+str(x),[x,6,5.95],[x+1.5,10,6.9],S),b('buckle_release_red_'+str(x),[x+.45,6.6,5.7],[x+1.05,7.6,6.2],R),b('hinge_black_channel_'+str(x),[x+.45,8.1,5.7],[x+1.05,9.5,6.2],SD)]
 return p

def base(suit):
 p=armor_skeleton();p['body']=[box('woven_base_torso',[-4.03,0,-2.03],[8.06,12,4.06],suit)]
 for side in ['left','right']:
  ax=-1.02 if side=='left' else -3.02
  p[side+'_arm']=[box(side+'_fitted_sleeve',[ax,-2.02,-2.02],[4.04,12.04,4.04],suit)]
  p[side+'_leg']=[box(side+'_fitted_leg',[-2.03,0,-2.03],[4.06,12.1,4.06],suit)]
 return p

def arm_x(side):return (-1.08,3.08) if side=='left' else (-3.08,1.08)

def add_ryuki_limbs(p,survive):
 for side in ['left','right']:
  ax,bx=arm_x(side);arm=p[side+'_arm'];leg=p[side+'_leg'];sgn=1 if side=='left' else -1
  arm += [box(side+'_black_shoulder_socket',[ax-.25,-2.15,-2.25],[4.66,3.5,4.5],K),box(side+'_red_upper_arm',[ax,1.3,-2.1],[4.16,3.1,4.2],C if survive else R),box(side+'_elbow_black_joint',[ax,4.25,-2.2],[4.16,.75,4.4],K)]
  if survive:
   # Raised, angular dragon wings, each with an actual gold perimeter.
   xy=[[ax-.25,.6],[ax+.05,-1.2],[ax+1.45,-1.8],[bx+.45,-6.25],[bx+1,-5.8],[bx+.8,-2],[bx+.25,.9]] if side=='left' else [[bx+.25,.6],[bx-.05,-1.2],[bx-1.45,-1.8],[ax-.45,-6.25],[ax-1,-5.8],[ax-.8,-2],[ax-.25,.9]]
   arm += [silhouette(side+'_survive_gold_wing_edge',xy,-2.72,G,.45)]
   cx=sum(x for x,y in xy)/len(xy);cy=sum(y for x,y in xy)/len(xy)
   arm.append(silhouette(side+'_survive_crimson_wing',[[cx+(x-cx)*.88,cy+(y-cy)*.88] for x,y in xy],-2.86,C,.18))
   arm += [box(side+'_survive_forearm_gold_frame',[ax-.12,5.0,-2.31],[4.4,3.6,4.62],G),box(side+'_survive_red_vambrace_inset',[ax+.25,5.35,-2.53],[3.65,2.9,.27],C)]
   for i in range(3):arm.append(box(side+'_survive_wrist_gold_band_'+str(i),[ax-.13,8.3+i*.34,-2.35],[4.42,.18,4.7],GL))
   arm.append(box(side+'_glove',[ax,9.3,-2.13],[4.16,1.2,4.26],K))
  else:
   arm += [box(side+'_red_square_shoulder_cap',[ax-.15,-1.85,-2.45],[4.46,2.6,4.9],R),box(side+'_shoulder_black_frame',[ax+.3,-1.55,-2.68],[3.55,1.8,.3],K),box(side+'_shoulder_silver_hinge',[ax+1.25,-1.15,-2.91],[1.65,1.0,.28],S),box(side+'_red_forearm',[ax-.08,5.1,-2.15],[4.32,3.5,4.3],R),box(side+'_glove',[ax,9.1,-2.1],[4.16,1.4,4.2],R)]
   for i in range(2):arm.append(box(side+'_dark_forearm_strap_'+str(i),[ax-.13,5.3+i*2.6,-2.3],[4.42,.4,4.6],K))
   arm += [box(side+'_rear_red_glove_plate',[ax+.3,9.25,2.15],[3.55,.9,.24],RD),box(side+'_rear_forearm_black_inset',[ax+.6,5.8,2.16],[3,1.8,.18],K)]
  # The original knee pivot is lateral, never an oversized round badge on the front.
  hx=2.08 if side=='left' else -2.52
  leg += [box(side+'_outer_knee_pivot_mount',[hx,4.9,-.9],[.44,1.65,1.8],SD),box(side+'_outer_knee_silver_axle',[hx-.04,5.2,-.6],[.52,1.05,1.2],S),box(side+'_rear_knee_flex',[-2.1,4.65,2.05],[4.2,1.5,.22],K)]
  if survive:
   xy=[[-1.6,6.7],[-.85,6.2],[0,6.7],[.85,6.2],[1.6,6.7],[1.85,10.8],[.9,11.6],[0,11.2],[-.9,11.6],[-1.85,10.8]]
   leg += [silhouette(side+'_survive_gold_shin',xy,-2.46,G,.35),silhouette(side+'_survive_crimson_shin_inset',[[-1.0,8.5],[1,8.5],[1.2,10.8],[0,11.15],[-1.2,10.8]],-2.61,C,.17)]
   for i in range(3):leg.append(silhouette(side+'_survive_red_shin_gill_'+str(i),[[-1.4,7+i*.45],[0,7.4+i*.45],[1.4,7+i*.45],[1.4,7.16+i*.45],[0,7.56+i*.45],[-1.4,7.16+i*.45]],-2.82,C,.16))
   leg.append(box(side+'_rear_gold_calf_seam',[-.19,7.0,2.1],[.38,3.8,.21],G))
  else:
   leg += [box(side+'_red_thigh_front',[-1.95,.3,-2.1],[3.9,4.15,.2],R),box(side+'_red_shin_front',[-2.02,6.3,-2.18],[4.04,4.3,.28],R),box(side+'_small_silver_knee_pad',[-.55,5,-2.37],[1.1,1.4,.3],SL),box(side+'_black_shin_channel',[-.56,7.05,-2.5],[1.12,3.7,.18],K),box(side+'_red_rear_calf_panel',[-1.75,6.4,2.1],[3.5,4.15,.19],RD)]
   for i in range(3):leg.append(box(side+'_tailored_red_thigh_fold_'+str(i),[-1.8,.75+i*1.05,-2.32],[3.6,.27,.14],RD))
  leg += [box(side+'_black_boot_toe',[-2.16,10.55,-2.95],[4.32,1.55,5.1],K),box(side+'_red_boot_instep',[-1.65,10.65,-3.09],[3.3,.66,.23],C if survive else R),box(side+'_boot_silver_sole',[-2.2,12.0,-3],[4.4,.3,5.2],SD),box(side+'_boot_rear_heel',[-1.7,10.8,2.12],[3.4,1,.24],C if survive else R)]

def ryuki(survive,items):
 p=base(K if survive else R);p['head']=cage(survive);body=p['body'];body += [box('black_chest_harness',[-4.13,.15,-2.13],[8.26,7.7,4.26],K)]
 if survive:
  body.append(box('survive_silver_neck_collar',[-2.7,-.6,-2.35],[5.4,1.0,4.7],S))
  outline=[[-4.4,.0],[-2.8,.35],[0,1.05],[2.8,.35],[4.4,0],[4.25,2.4],[1.1,3.3],[0,4.25],[-1.1,3.3],[-4.25,2.4]]
  body += [silhouette('survive_cuirass_gold_perimeter',outline,-2.92,G,.38),silhouette('survive_crimson_dragon_breast',[[x*.94,1.7+(y-1.7)*.8] for x,y in outline],-3.1,C,.24),silhouette('survive_gold_sternum',[[-.65,3.6],[.65,3.6],[.65,8.15],[0,8.9],[-.65,8.15]],-3.05,G,.35)]
  for sign in [-1,1]:
   body.append(silhouette('survive_crimson_flank_'+str(sign),mirror([[3.0,2.1],[4.4,1.65],[4.3,7.5],[2.9,7.0]],sign),-2.88,C,.4))
   for i in range(5):body.append(silhouette('survive_silver_abdominal_rib_'+str(sign)+'_'+str(i),mirror([[.85,4+i*.72],[2.95,3.55+i*.72],[3.0,4.0+i*.72],[.85,4.45+i*.72]],sign),-2.93,S,.27))
   body += [silhouette('survive_gold_flank_piping_'+str(sign),mirror([[4.17,1.8],[4.4,1.7],[4.25,7.65],[2.9,7.2],[2.9,7],[4.05,7.3]],sign),-3.03,G,.16),box('survive_rear_crimson_plate_'+str(sign),[sign*2.1-1.7,.65,2.12],[3.4,5.7,.32],C)]
  body += [box('survive_rear_gold_spine',[-.22,.6,2.47],[.44,7.2,.22],G),box('survive_rear_gold_shoulder_line',[-3.8,.45,2.5],[7.6,.3,.2],G)]
  for i in range(3):body.append(box('survive_rear_silver_flex_'+str(i),[-2.8,6.8+i*.63,2.13],[5.6,.3,.26],S))
 else:
  for sign in [-1,1]:
   body += [silhouette('normal_silver_pectoral_'+str(sign),mirror([[.15,.6],[3.45,.6],[3.7,1.1],[3.65,4.35],[2.8,4.8],[.18,4.35]],sign),-2.8,S,.55),box('normal_silver_pectoral_glint_'+str(sign),[sign*1.75-.55,2,-3.39],[1.1,.6,.08],SL),box('normal_black_waist_socket_'+str(sign),[sign*2.8-.65,6.1,-2.43],[1.3,1.5,.28],SD)]
  body += [silhouette('normal_silver_sternum',[[-.24,.8],[.24,.8],[.4,5.1],[0,6.8],[-.4,5.1]],-3.41,S,.18)]
  for i in range(3):body.append(silhouette('normal_silver_abdominal_segment_'+str(i),[[-2.35,4.7+i*.55],[0,5.1+i*.55],[2.35,4.7+i*.55],[2.25,5.05+i*.55],[0,5.5+i*.55],[-2.25,5.05+i*.55]],-2.8,S,.2))
  body += [box('normal_rear_red_harness',[-3.95,.65,2.12],[7.9,6.0,.3],RD),box('normal_rear_silver_collar',[-3.45,.65,2.46],[6.9,.45,.2],S),box('normal_rear_black_spine',[-.32,1.3,2.5],[.64,5.5,.2],K)]
  for sign in [-1,1]:body.append(silhouette('normal_rear_silver_yoke_'+str(sign),mirror([[.6,.9],[3.7,.9],[3.7,2.2],[.6,4.2]],sign),2.45,S,.22))
 add_ryuki_limbs(p,survive)
 append_belt(p,'ryuki_survive_belt' if survive else 'ryuki_belt',items)
 return p

def decade(items):
 p=base(K);p['head']=decade_mask();body=p['body']
 for sign in [-1,1]:
  body += [box('magenta_side_armor_'+str(sign),[sign*3.3-.8,.35,-2.25],[1.6,8.4,4.5],M),silhouette('white_inner_pectoral_'+str(sign),mirror([[2.25,3.9],[2.8,4.1],[3.3,6.9],[2.7,6.9]],sign),-2.78,'white',.23),box('rear_magenta_torso_rail_'+str(sign),[sign*3.1-.6,.3,2.26],[1.2,8.4,.24],M)]
 body += [box('black_flat_breastplate',[-3.05,.4,-2.2],[6.1,7.1,.35],K),silhouette('white_diagonal_frame',[[-4.1,4.4],[4.15,.6],[4.15,1.9],[-4.1,5.7]],-2.87,'white',.22),silhouette('black_diagonal_inlay',[[-4.1,4.79],[4.15,.99],[4.15,1.48],[-4.1,5.28]],-3.12,K,.16),box('white_vertical_chest_reader',[.65,.45,-2.85],[.9,5.9,.22],'white'),box('black_vertical_reader_groove',[.94,.45,-3.11],[.3,5.9,.15],K),box('rear_white_spine',[-.3,.5,2.56],[.6,6.6,.2],'white')]
 for sign in [-1,1]:body.append(silhouette('rear_white_yoke_'+str(sign),mirror([[.35,3.4],[3.8,.6],[3.8,1.3],[.35,4.1]],sign),2.56,'white',.2))
 for i in range(3):body.append(box('black_abdominal_segment_'+str(i),[-2.4,6.9+i*.66,-2.21],[4.8,.38,.24],K))
 for side in ['left','right']:
  ax,bx=arm_x(side);arm=p[side+'_arm'];leg=p[side+'_leg'];sign=1 if side=='left' else -1
  xy=[[ax-.1,-1.0],[bx+.2,-2.35],[bx+.2,.95],[bx-.3,1.8],[ax-.1,.45]] if side=='left' else [[bx+.1,-1],[ax-.2,-2.35],[ax-.2,.95],[ax+.3,1.8],[bx+.1,.45]]
  arm += [silhouette(side+'_square_magenta_pauldron',xy,-2.48,M,.45),box(side+'_pauldron_rear',[ax-.1,-1.8,2.12],[4.36,2.5,.3],M)]
  edge=[[x,y] for x,y in [xy[0],xy[1],[xy[1][0],xy[1][1]+.48],[xy[0][0],xy[0][1]+.48]]]
  arm += [silhouette(side+'_sloping_white_shoulder_border',edge,-2.97,'white',.18),silhouette(side+'_black_shoulder_border',[[x,y+.65] for x,y in edge],-2.95,K,.2),box(side+'_magenta_bicep',[ax+.1,1.9,-2.08],[3.96,2.3,4.16],M),box(side+'_elbow_joint',[ax,4.1,-2.13],[4.16,.9,4.26],K),box(side+'_magenta_forearm',[ax-.04,5.1,-2.14],[4.24,3.05,4.28],M),box(side+'_white_wrist_ring',[ax-.1,8.25,-2.22],[4.36,.75,4.44],'white'),box(side+'_glove',[ax,9.05,-2.11],[4.16,1.5,4.22],K),box(side+'_rear_white_bracer_trace',[ax+.4,5.55,2.18],[3.3,.42,.17],'white')]
  leg += [box(side+'_front_thigh_black_panel',[-1.42,.4,-2.1],[2.84,4.6,.2],K),box(side+'_white_rectangular_kneecap',[-1.46,4.7,-2.23],[2.92,1.4,.26],'white'),box(side+'_white_shin_panel',[-1.38,6.35,-2.24],[2.76,4.8,.24],'white'),box(side+'_black_shin_reader_line',[-.2,6.35,-2.51],[.4,4.8,.16],K),box(side+'_black_boot',[-2.13,10.6,-2.9],[4.26,1.55,5.03],K),box(side+'_white_boot_sole',[-2.18,11.8,-3.0],[4.36,.35,5.2],'white'),box(side+'_rear_white_heel',[-1.75,10.85,2.14],[3.5,.65,.18],'white')]
  for sign in [-1,1]:
   leg += [box(side+'_magenta_thigh_outer_rail_'+str(sign),[sign*1.7-.31,.35,-2.16],[.62,4.25,.26],M),box(side+'_magenta_shin_outer_rail_'+str(sign),[sign*1.75-.29,6.1,-2.15],[.58,5.4,.23],M),box(side+'_rear_magenta_leg_rail_'+str(sign),[sign*1.65-.22,0.5,2.12],[.44,9.8,.17],M)]
 append_belt(p,'decade_belt',items);return p

def extend(data):
 data['materials'].update(MATERIALS)
 assert len(data['materials'])<=64,'512px atlas has64 material slots'
 data['items']['ryuki_belt']=belt_items('ryuki');data['items']['ryuki_survive_belt']=belt_items('survive');data['items']['decade_belt']=belt_items('decade')
 data['forms']['ryuki']=ryuki(False,data['items']);data['forms']['ryuki_survive']=ryuki(True,data['items']);data['forms']['decade']=decade(data['items'])
 data['references']['armor_correction_2026_10_18']={'images':['1000391841.jpg (Decade Minecraft)','1000391826.jpg (normal Ryuki Minecraft)','1000391827.jpg (Survive full body)','1000395522.jpg (Survive helmet all angles)','1000395528.jpg (normal/Survive anatomy and equipment)'],'rules':['Ryuki normal has red limbs and black dragon Card Deck','Ryuki Survive is a distinct crimson/gold/black wearable model','Decade preserves its own model in Complete','All geometry uses original pixel atlas, not pasted photographs']}
