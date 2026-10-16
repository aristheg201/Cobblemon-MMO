"""Original voxel geometry following the four user-supplied photographic references.

Keep the Zecter's horizontal beetle silhouette and gold fork (never a helmet face),
and the Kunai Gun's open foreguard, silver cylinder, white grip and underslung blade.
All surfaces use native Minecraft materials; no photographs are mapped onto models.
"""
import copy

PALETTE={'red':'minecraft:block/red_concrete','red_light':'minecraft:block/redstone_block','black':'minecraft:block/black_concrete','silver':'minecraft:block/light_gray_concrete','chrome':'minecraft:block/iron_block','gold':'minecraft:block/gold_block','blue':'minecraft:block/light_blue_concrete','glass':'minecraft:block/glass','yellow':'minecraft:block/yellow_concrete','dark':'minecraft:block/gray_concrete'}

def box(a,b,material,angle=0,pivot=None,axis='z'):
    e={'from':list(a),'to':list(b),'faces':{f:{'uv':[0,0,16,16],'texture':'#'+material} for f in ('north','south','east','west','up','down')}}
    if angle:e['rotation']={'angle':angle,'axis':axis,'origin':pivot or [(a[i]+b[i])/2 for i in range(3)]}
    return e

def equipment_models():
    b=box
    belt=[
        b([.5,7,9],[16,9,10.2],'silver'),b([2.4,5.1,8],[15.5,10.9,9.8],'black'),
        b([1.8,5.7,7.7],[3.6,10.3,9.1],'chrome'),b([14.3,5.7,7.7],[16,10.3,9.1],'chrome'),
        b([2.2,6.6,7.5],[3.2,9.4,7.8],'dark'),b([14.7,6.6,7.5],[15.7,9.4,7.8],'dark'),
        # Rounded beetle shell expressed as several inset voxel tiers.
        b([3.3,6,6.6],[7.8,10,8.9],'red'),b([4,5.4,6.6],[7.7,10.6,8.7],'red'),
        b([11,5.4,6.6],[14.1,10.6,8.7],'red'),b([10.7,6,6.5],[14.8,10,8.8],'red'),
        b([4.1,9.6,6.35],[7,10.15,6.65],'red_light'),b([12,9.6,6.25],[14.1,10.15,6.55],'red_light'),
        b([12,8.7,6.2],[14.3,9.2,6.5],'black'),b([12.3,6.45,6.2],[14.2,7.0,6.5],'black'),
        b([13.35,7.4,6.15],[14.0,8.0,6.5],'black'),
        # Top/bottom silver hinge barrels and red central release button.
        b([6.5,10.8,6.7],[8.3,11.45,7.55],'chrome'),b([10.8,10.8,6.7],[12.7,11.45,7.55],'chrome'),
        b([6.5,4.6,6.7],[8.3,5.25,7.55],'chrome'),b([10.8,4.6,6.7],[12.7,5.25,7.55],'chrome'),
        b([8.8,10.6,6.8],[10.0,11.9,8.0],'red_light'),b([8.8,4.1,6.8],[10.0,5.4,8.0],'red'),
        # Octagonal chrome rim surrounding the transparent red rotor.
        b([7.9,10.0,5.4],[10.7,10.45,6.4],'chrome'),b([7.9,5.55,5.4],[10.7,6.0,6.4],'chrome'),
        b([6.85,6.8,5.4],[7.3,9.2,6.4],'chrome'),b([11.3,6.8,5.4],[11.75,9.2,6.4],'chrome'),
        b([6.9,9.5,5.4],[8.4,9.95,6.4],'chrome',45),b([10.2,9.5,5.4],[11.7,9.95,6.4],'chrome',-45),
        b([6.9,6.05,5.4],[8.4,6.5,6.4],'chrome',-45),b([10.2,6.05,5.4],[11.7,6.5,6.4],'chrome',45),
        b([7.3,6.1,5.7],[11.3,9.9,6.6],'black'),
        b([7.5,7.75,5.0],[11.1,8.25,5.7],'red_light',45,[9.3,8,5.35]),
        b([7.5,7.75,5.0],[11.1,8.25,5.7],'red_light',-45,[9.3,8,5.35]),
        b([8.8,7.5,4.8],[9.8,8.5,5.7],'gold'),b([9.0,7.7,4.65],[9.6,8.3,4.9],'yellow'),
        b([7.35,6.2,4.45],[11.25,9.8,4.55],'glass'),
        # Large gold fork, horizontal and projecting to the left of the beetle.
        b([.5,7.5,4.1],[7.6,8.5,4.8],'gold'),b([.5,5.45,4.1],[1.4,10.55,4.8],'gold'),
        b([.8,10.1,4.1],[2.1,10.9,4.8],'gold'),b([.8,5.1,4.1],[2.1,5.9,4.8],'gold'),
        b([6.7,9.1,4.1],[7.6,10.3,4.8],'gold',-45),b([6.7,5.7,4.1],[7.6,6.9,4.8],'gold',45),
        b([7.1,10.0,4.1],[9.6,10.7,4.8],'gold'),b([7.1,5.3,4.1],[9.6,6.0,4.8],'gold'),
        b([1.5,7.9,4.0],[6.1,8.1,4.15],'chrome'),
        b([7.2,9.6,3.9],[7.8,10.2,4.6],'chrome'),b([7.2,5.8,3.9],[7.8,6.4,4.6],'chrome')]
    gun=[
        # Open, red-trimmed D foreguard. The middle stays genuinely empty.
        b([-3.3,6.4,6.5],[-1.8,12.4,9.5],'black',22.5),b([-2.8,6.6,6.2],[-2.2,11.9,6.5],'red'),
        b([-1.8,5.7,6.5],[8.7,7.1,9.5],'black'),b([-1.5,5.45,6.3],[8.7,6.1,9.7],'red'),
        b([8.5,6,6.5],[10.0,10.6,9.5],'black',-22.5),b([8.3,6.4,6.25],[8.8,9.2,6.55],'red'),
        # Ribbed silver cylinder over the foreguard.
        b([-1.6,10.2,7.0],[9.3,12.0,9.0],'silver'),b([-1.6,11.6,6.8],[9.3,12.2,9.2],'chrome'),
        # Black receiver, silver side, red release button and white trigger frame.
        b([8.7,9.6,6.1],[20.8,13.0,9.9],'black'),b([10,12.8,6.5],[19.5,13.6,9.5],'dark'),
        b([11.0,12.9,6.25],[19.2,13.35,6.55],'chrome'),b([19.3,9.8,6.2],[21.0,12.4,9.8],'black'),
        b([10.2,8.0,6.0],[17.2,9.8,10.0],'silver'),b([11,9.7,5.9],[17.0,10.1,6.2],'red'),
        b([9.1,8.25,5.55],[10.6,9.75,6.2],'chrome'),b([9.4,8.55,5.35],[10.3,9.45,5.7],'red_light'),
        b([12.0,6.3,6.5],[13.0,8.3,9.5],'silver'),b([12.6,6.6,7.1],[13.0,8.4,8.9],'red'),
        # Angled black grip bounded by silver frame, with an underslung cutting blade.
        b([14,1.8,6.8],[16.8,8.1,9.2],'black',22.5,[14,8,8]),
        b([16.5,1.8,6.7],[17.3,7.6,9.3],'silver',22.5,[14,8,8]),
        b([13.0,1.5,6.6],[14.0,6.9,9.4],'chrome',22.5,[14,8,8]),
        b([12.2,.6,6.2],[20.0,1.9,9.8],'black'),b([11.2,.25,6.0],[20.7,.85,10.0],'red'),
        b([10.8,-.45,5.9],[21.2,.25,10.1],'chrome'),b([20,1.0,6.3],[21.2,2.5,9.7],'black',22.5),
        b([8.8,13.3,7.2],[10.2,14.2,8.8],'red'),
        # Three bars compose the Z-like insignia on the white frame.
        b([11.0,8.9,5.9],[12.2,9.1,6.1],'black'),b([11.0,8.2,5.9],[12.2,8.4,6.1],'black'),
        b([11.5,8.25,5.9],[11.7,9.05,6.1],'black',-45)]
    for x in range(10):gun.append(b([-.9+x,10.1,6.9],[-.65+x,12.1,9.1],'chrome'))
    # Three squared muzzle ports retain the reference's distinctive front profile.
    for y,r,material in [(10.9,.95,'red'),(13.0,.4,'chrome'),(6.5,.4,'chrome')]:
        gun.append(b([-4.2,y-r,8-r],[-2.8,y+r,8+r],material))
        gun.append(b([-4.35,y-r*.58,8-r*.58],[-4.2,y+r*.58,8+r*.58],'black'))
    # The user's1000391753 reference is the close-combat configuration of
    # this same frame: the fist grips the ribbed silver cylinder, exposing
    # the broad blade below the former pistol grip. Never replace it with a dagger.
    kunai=copy.deepcopy(gun)
    hyper=copy.deepcopy(belt)+[b([11.5,10.5,5.8],[14.0,11.2,6.5],'gold'),b([11.5,4.8,5.8],[14,5.5,6.5],'gold')]
    results={}
    for name,elements in [('zecter_belt',belt),('hyper_zecter',hyper),('kunai_gun',gun),('kunai',kunai)]:
        display={'gui':{'rotation':[12,205 if name in ['zecter_belt','hyper_zecter'] else 25,0],'translation':[-1,0,0],'scale':[.66,.66,.66]},'fixed':{'scale':[1,1,1]},'thirdperson_righthand':{'rotation':[0,-90,0] if name=='kunai_gun' else [0,90,0] if name=='kunai' else [0,-90,45],'translation':[0,1.8,-3.15] if name=='kunai_gun' else [0,-1.35,-2.25] if name=='kunai' else [0,2,0],'scale':[.45,.45,.45] if name in ['kunai_gun','kunai'] else [.65,.65,.65]},'firstperson_righthand':{'rotation':[0,-90,0] if name=='kunai_gun' else [0,-90,0] if name=='kunai' else [0,-90,20],'translation':[0,2.0,-3.5] if name=='kunai_gun' else [0,-1.5,2.5] if name=='kunai' else [1,1,0],'scale':[.5,.5,.5] if name in ['kunai_gun','kunai'] else [.65,.65,.65]}}
        display['thirdperson_lefthand']=copy.deepcopy(display['thirdperson_righthand']);display['firstperson_lefthand']=copy.deepcopy(display['firstperson_righthand'])
        results[name]={'textures':{**PALETTE,'particle':PALETTE['red']},'elements':elements,'display':display}
    return results

def form_geometry():
    forms={'masked':{},'rider':{},'hyper':{}}
    def put(form,bone,material,a,size,rotation=None,pivot=None,emissive=False):
        part={'from':a,'size':size,'material':PALETTE[material]}
        if rotation:part['rotation']=rotation;part['pivot']=pivot or [a[i]+size[i]/2 for i in range(3)]
        if emissive:part['emissive']=True
        forms[form].setdefault(bone,[]).append(part)
    # Sculpted Masked shell, wrapped two-facet visor and broad forehead V.
    # The crown consists of silver wedge plates rooted in the brow, never rods.
    def mesh(bone,material,points,depth=.22,emissive=False,axis=2):
        n=len(points);back=[list(v) for v in points]
        for v in back:v[axis]+=depth
        vertices=[list(v) for v in points]+back
        faces=[list(range(n)),list(reversed(range(n,2*n)))]+[[i,(i+1)%n,(i+1)%n+n,i+n] for i in range(n)]
        forms['masked'].setdefault(bone,[]).append({'vertices':vertices,'faces':faces,'material':PALETTE[material],'emissive':emissive})
    put('masked','head','silver',[-3.65,-8.6,-3.65],[7.3,8.7,7.5])
    put('masked','head','silver',[-4.25,-7.5,-3.65],[8.5,6.5,7.5])
    put('masked','head','chrome',[-2.9,-8.95,-3.25],[5.8,.7,6.6])
    for side in [-1,1]:
        # Rounded shell edges and the cheek shells are actual angled surfaces.
        x=side*3.72
        put('masked','head','silver',[x-.45,-7.4,-4.35],[.9,5.65,1.8],[0,side*24,0])
        lens=[[-3.65,-6.8,-4.65],[-2.65,-7.65,-5.05],[-.3,-7.2,-5.5],[-.25,-4.3,-5.55],[-.65,-3.25,-5.4],[-2.7,-3.7,-5.05],[-3.7,-4.5,-4.65]]
        if side==1:lens=[[ -x,y,z] for x,y,z in reversed(lens)]
        center=[side*1.9,-5.35]
        for material,scale,z in [('black',1.13,.21),('red',1.065,.11),('blue',1,0)]:
            mesh('head',material,[[center[0]+(x-center[0])*scale,center[1]+(y-center[1])*scale,z0+z] for x,y,z0 in lens],.16,material=='blue')
        blade=[[0,-4.85,-5.85],[side*5.45,-10.15,-5.0],[side*3.2,-8.95,-5.25],[side*.65,-6.85,-5.9]]
        if side==-1:blade.reverse()
        mesh('head','dark',[[x,y,z+.13] for x,y,z in blade],.7)
        mesh('head','chrome',blade,.24)
        # Tapered outer tip has a separate bright upper bevel.
        mesh('head','silver',[[side*5.45,-10.15,-5.0],[side*3.2,-8.95,-5.25],[side*3.6,-8.45,-5.15]],.35)
        # Concentric earpieces in the sagittal plane.
        import math
        for radius,material,xoffset in [(1.04,'silver',4.25),(.75,'black',4.36),(.48,'chrome',4.45)]:
            disk=[[side*xoffset,-3.75+math.sin(i*math.tau/12)*radius,-.7+math.cos(i*math.tau/12)*radius] for i in range(12)]
            mesh('head',material,disk,-side*.18,False,0)
    mesh('head','black',[[-.8,-6.9,-6],[.8,-6.9,-6],[.38,-4.35,-6],[0,-4.05,-6],[-.38,-4.35,-6]],.3)
    put('masked','head','red_light',[-.24,-5.55,-6.15],[.48,.48,.25])
    put('masked','head','silver',[-.75,-7.3,-5.95],[1.5,.5,.5])
    put('masked','head','black',[-2.25,-3.0,-4.95],[4.5,2.5,.8])
    mesh('head','silver',[[-2.45,-2.3,-5.1],[-1.6,-.4,-5.35],[1.6,-.4,-5.35],[2.45,-2.3,-5.1],[1.1,-1.7,-5.4],[-1.1,-1.7,-5.4]],.5)
    put('masked','head','dark',[-.65,-3.3,-5.45],[1.3,2.0,.35])
    for y in [-3.1,-2.65,-2.2,-1.75]:put('masked','head','chrome',[-.6,y,-5.7],[1.2,.17,.3])
    # Masked torso has a red yoke, silver breast plates and dark central armor.
    put('masked','body','dark',[-4.5,.2,-3],[9,7.8,1.2]);put('masked','body','red',[-4.6,-.3,-3.4],[9.2,2.9,.8]);
    for side in [-1,1]:
        points=[[side*4.4,2.6,-3.4],[side*1.9,3.1,-3.6],[side*1.2,6.8,-3.5],[side*3.3,7.6,-3.15]]
        if side==-1:points.reverse()
        mesh('body','silver',points,.8)
    put('masked','body','black',[-1.7,3.8,-3.3],[3.4,3.7,.5])
    for x in [-3.6,-2.2,-.8,.6,2]:put('masked','body','chrome',[x,1.1,-3.5],[.45,1.05,.2],[0,0,-18])
    put('masked','body','silver',[-4.5,0,2.05],[9,7.7,1.15])
    # Rider has red shaped chest panels over the black suit and vertical silver abdomen ribs.
    for x in [-4.35,.25]:put('rider','body','red',[x,-.2,-3.15],[4.1,3.2,1.2])
    for x in [-3.8,.45]:put('rider','body','red',[x,3.8,-3.1],[3.35,3.8,1.0])
    put('rider','body','red_light',[-.8,2.8,-3.3],[1.6,.8,.55]);put('rider','body','black',[-.35,3.6,-3.2],[.7,5,.4])
    for x in [-2.8,-1.4,0,1.4,2.8]:put('rider','body','chrome',[x,7.8,-2.8],[.45,1.65,.5])
    for form in ['masked','rider']:
        for bone,left in [('left_arm',True),('right_arm',False)]:
            x=-1.6 if left else -3.3
            put(form,bone,'black',[-1.1 if left else -3.1,-2.1,-2.1],[4.2,12.2,4.2])
            if form=='masked':
                # Explicit user direction: square Minecraft pauldrons, no discs.
                cap=-2.2 if left else -4.2
                put(form,bone,'silver',[cap,-3.1,-2.9],[6.4,3.9,5.8])
                put(form,bone,'chrome',[cap,-3.35,-2.9],[6.4,.5,5.8])
                put(form,bone,'black',[cap+1.2,-2.5,-3.08],[4.0,2.65,.25])
                put(form,bone,'dark',[cap+1.6,-2.15,-3.25],[3.2,1.95,.2])
                for y in [2.5,4.1,5.7]:put(form,bone,'silver',[x+.6,y,-2.5],[3.7,.65,.8],[0,0,-12 if left else 12])
            else:
                put(form,bone,'silver',[x,-2.3,-2.7],[4.9,3.3,5.4])
                put(form,bone,'red',[x+.35,-2.65,-2.85],[4.2,2.3,5.3])
            put(form,bone,'chrome',[x+.6,7.1,-2.4],[3.7,2.4,4.8]);put(form,bone,'black',[x+.6,9.6,-2.3],[3.7,.45,4.6])
        for bone in ['left_leg','right_leg']:
            put(form,bone,'silver',[-1.7,3.5,-2.6],[3.4,2.1,.9]);put(form,bone,'chrome',[-1.4,5.7,-2.5],[2.8,3.3,.7]);
            for y in [9.1,9.9,10.7]:put(form,bone,'silver',[-1.9,y,-2.3],[3.8,.45,4.6])
    for form in ['masked','rider']:
        put(form,'head','black',[-4.12,-8.12,-4.12],[8.24,8.24,8.24])
        put(form,'body','black',[-4.1,-.1,-2.15],[8.2,12.2,4.3])
        for bone in ['left_leg','right_leg']:
            put(form,bone,'black',[-2.05,-.1,-2.1],[4.1,12.2,4.2])
            put(form,bone,'black',[-2.1,10.8,-2.6],[4.2,1.7,5.4])
            put(form,bone,'silver',[-2.05,11.6,-3.2],[4.1,.85,1.2])
    # Hyper additions retain the Rider silhouette rather than an unrelated suit.
    put('hyper','body','gold',[-5.4,.5,2.5],[2.2,6.5,.7]);put('hyper','body','gold',[3.2,.5,2.5],[2.2,6.5,.7])
    return {'forms':forms,'source':'user:1000391750.jpg; original Minecraft voxel adaptation'}
