// Execute in the real Blockbench desktop renderer using blockbench_cdp.py.
(async()=>{
 const root='/workspace/integration/FantasyHub',source=root+'/assets-source/riders',assets=root+'/client/src/imported/resources/assets/fantasyhub';
 const read=p=>new Promise(resolve=>Blockbench.read([p],{readtype:'text'},files=>resolve(files[0].content)));
 const write=(p,value)=>Blockbench.writeFile(p,{content:typeof value==='string'?value:JSON.stringify(value,null,2),savetype:'text'});
 const data=JSON.parse(await read(source+'/rider_geometry.json')),names=Object.keys(data.materials);
 for(const old of ModelProject.all.slice())if(Object.hasOwn(data.forms,old.name)||Object.hasOwn(data.items,old.name)){old.saved=true;old.close();}
 const canvas=document.createElement('canvas');canvas.width=canvas.height=512;const ctx=canvas.getContext('2d');
 for(let i=0;i<names.length;i++){
   const name=names[i],base=data.materials[name],lens=name.startsWith('lens_')||name==='optic_green',cloth=['fabric','carbon','black','knight_navy','navy','bat_membrane'].includes(name),x0=i%8*64,y0=Math.floor(i/8)*64;
   for(let y=0;y<64;y++)for(let x=0;x<64;x++){
     const edge=Math.min(x,y,63-x,63-y),noise=((x*17+y*31+i*13)%19-9)*(cloth?.12:.08);
     let value=cloth?-.13+(x%4===0?.025:0)+(y%4===0?.015:0):.15-y*.0035-x*.0008;
     if(edge<2)value-=.36;else if(edge<4)value+=(x<32&&y<32?.22:-.14);
     if(!cloth&&!lens)value+=.18*Math.exp(-(((x-17)/10)**2));
     if(lens){const cell=((x+(Math.floor(y/5)%2)*3)%6),row=y%5;value+=(cell===2&&row===2?.2:-.07);if(x<26&&y<23&&((x-19)**2+(y-16)**2)<64)value+=.45;}
     if(name.startsWith('pixel_')){
       const gx=Math.floor(x/4),gy=Math.floor(y/4),metal=/silver|gold/.test(name),lens=/lens/.test(name);
       value=metal?(gy<3?.16:gy>12?-.15:0):(gy<4?.035:gy>11?-.09:0);
       if(gx===0||gy===0)value+=metal?.12:-.09;
       if(gx===15||gy===15)value-=.16;
       if(!metal&&!lens&&gx>9&&gy>3&&gy<12)value-=.035;
       if(lens){value+=(gx%3===1&&gy%3===1?.17:-.04);if(gx>=3&&gx<=5&&gy>=2&&gy<=4)value+=.35;}
       const c=base.map(v=>Math.max(0,Math.min(255,Math.round(v*(1+value)))));
       ctx.fillStyle=`rgb(${c.join(',')})`;ctx.fillRect(x0+x,y0+y,1,1);continue;
     }
     const c=base.map(v=>Math.max(0,Math.min(255,Math.round(v*(1+value)+noise))));
     ctx.fillStyle=`rgb(${c.join(',')})`;ctx.fillRect(x0+x,y0+y,1,1);
   }
 }
 // Hand-painted contract emblems traced as pixel silhouettes from the supplied
 // card deck references. These replace the rejected arbitrary L-shaped gold blocks.
 function poly(points,color,x,y){ctx.fillStyle=color;ctx.beginPath();points.forEach((p,i)=>i?ctx.lineTo(x+p[0],y+p[1]):ctx.moveTo(x+p[0],y+p[1]));ctx.closePath();ctx.fill();}
 for(const name of ['crest_ryuki','crest_knight']){
   const i=names.indexOf(name),x=i%8*64,y=Math.floor(i/8)*64;
   ctx.fillStyle=name==='crest_ryuki'?'#77131c':'#0b202d';ctx.fillRect(x,y,64,64);
   const dragon=[[13,13],[36,8],[48,15],[42,22],[34,22],[34,27],[45,31],[43,39],[35,40],[35,48],[29,55],[20,53],[24,48],[25,40],[18,35],[20,28],[28,27],[29,20],[19,22],[11,18]];
   const bat=[[7,16],[19,23],[28,19],[32,11],[36,19],[45,23],[57,16],[51,34],[43,29],[38,38],[34,48],[32,55],[30,48],[26,38],[21,29],[13,34]];
   const shape=name==='crest_ryuki'?dragon:bat;
   poly(shape,'#805219',x+1,y+2);poly(shape,'#d5a940',x,y);
   if(name==='crest_ryuki'){poly([[20,14],[35,10],[44,15],[36,17],[29,17],[29,29],[24,28]],'#f1da88',x,y);ctx.fillStyle='#391c14';ctx.fillRect(x+37,y+14,3,3);}
   else poly([[11,19],[21,25],[29,21],[32,15],[32,29],[26,34],[21,27],[15,31]],'#eed581',x,y);
   ctx.fillStyle='#a5b0b5';ctx.fillRect(x+3,y+3,58,2);ctx.fillRect(x+3,y+59,58,2);
 }
 // Original pixel tracing of the supplied normal/Survive V-Buckle and Decadriver.
 for(const name of ['pixel_deck_black','pixel_deck_survive','pixel_decadriver']){
   const i=names.indexOf(name);if(i<0)continue;const x=i%8*64,y=Math.floor(i/8)*64;
   if(name==='pixel_decadriver'){
     ctx.fillStyle='#dce3e6';ctx.fillRect(x,y,64,64);ctx.fillStyle='#89969d';ctx.fillRect(x+3,y+3,58,58);ctx.fillStyle='#eff2ed';ctx.fillRect(x+5,y+5,54,54);
     const colors=['#d61d29','#3159ac','#c2a337','#309364','#9b237a','#264256','#b6253d','#cbd3d9','#78459b'];
     const emblems=['10101/10101/01110/00100/00100','00100/01110/10101/01110/00100','01010/11011/01110/01010/00100','01110/10101/10101/01110/00100','00100/01110/11111/01110/00100','10101/01110/10101/11111/00100','10001/01010/01110/00100/00100','01110/10001/10101/10001/01110','11011/11111/01110/01010/00100'];
     for(let a=0;a<9;a++){const t=(a/9)*Math.PI*2-Math.PI/2,cx=Math.round(32+Math.cos(t)*23),cy=Math.round(32+Math.sin(t)*23);ctx.fillStyle='#25282a';ctx.fillRect(x+cx-4,y+cy-4,8,8);ctx.fillStyle='#e5e9e1';ctx.fillRect(x+cx-3,y+cy-3,6,6);ctx.fillStyle=colors[a];emblems[a].split('/').forEach((row,ry)=>[...row].forEach((bit,rx)=>{if(bit==='1')ctx.fillRect(x+cx-2+rx,y+cy-2+ry,1,1);}));}
     ctx.fillStyle='#353b3e';ctx.fillRect(x+17,y+17,30,30);ctx.fillStyle='#f0f0eb';ctx.fillRect(x+20,y+20,24,24);ctx.fillStyle='#b61b2c';ctx.fillRect(x+24,y+23,16,18);ctx.fillStyle='#e33b43';ctx.fillRect(x+25,y+24,4,13);ctx.fillStyle='#519c53';ctx.fillRect(x+18,y+21,6,6);ctx.fillStyle='#345cba';ctx.fillRect(x+40,y+36,6,6);
   }else{
     ctx.fillStyle=name==='pixel_deck_black'?'#101b2b':'#7e1129';ctx.fillRect(x,y,64,64);
     ctx.fillStyle='#cdd7dc';ctx.fillRect(x+2,y+2,60,2);ctx.fillRect(x+2,y+60,60,2);
     for(let n=0;n<6;n++){ctx.fillRect(x+3+n*3,y+5,1,3);ctx.fillRect(x+43+n*3,y+56,1,3);}
     for(const [cx,cy]of [[4,6],[55,6],[4,50],[55,50]]){ctx.fillStyle='#c6d0d6';ctx.fillRect(x+cx,y+cy,5,8);ctx.fillStyle='#162231';ctx.fillRect(x+cx+2,y+cy+2,1,4);}
     // Ryuki's forked dragon head: horns, paired eye cuts and pointed lower jaw.
     const crest=[[32,8],[36,20],[42,7],[43,22],[51,16],[47,30],[43,35],[44,43],[38,50],[32,57],[26,50],[20,43],[21,35],[17,30],[13,16],[21,22],[22,7],[28,20]];
     poly(crest,'#8c5b1e',x+1,y+1);poly(crest,'#d5ac44',x,y);
     poly([[22,12],[26,24],[31,30],[31,49],[27,44],[25,34],[21,28]],'#f1d780',x,y);
     poly([[23,31],[29,34],[26,38],[22,35]],'#242229',x,y);poly([[41,31],[35,34],[38,38],[42,35]],'#242229',x,y);
     ctx.fillStyle='#e0bb59';ctx.fillRect(x+30,y+39,4,10);
   }
 }
 // Pixel-painted card portrait and K-Touch screen. The armor itself remains full geometry.
 for(const name of ['card_decade','ktouch_screen']){
   const i=names.indexOf(name),x=i%8*64,y=Math.floor(i/8)*64;
   ctx.fillStyle='#151017';ctx.fillRect(x,y,64,64);
   if(name==='card_decade'){
     ctx.fillStyle='#c61979';ctx.fillRect(x+3,y+3,58,3);ctx.fillRect(x+3,y+58,58,3);
     ctx.fillStyle='#ced5d8';ctx.fillRect(x+8,y+9,48,2);ctx.fillRect(x+8,y+52,48,2);
     poly([[16,19],[24,14],[40,14],[48,19],[47,40],[39,46],[25,46],[17,40]],'#b61a6c',x,y);
     poly([[18,25],[29,22],[29,35],[20,37]],'#7ec34a',x,y);poly([[35,22],[46,25],[44,37],[35,35]],'#7ec34a',x,y);
     for(let b=0;b<7;b++){ctx.fillStyle='#08080b';ctx.fillRect(x+19+b*4,y+12+Math.abs(b-3),2,30-Math.abs(b-3));}
     ctx.fillStyle='#c1c9cc';ctx.fillRect(x+26,y+39,12,2);
     for(let b=0;b<20;b++){ctx.fillStyle=b%3===0?'#c5cbd2':'#17121b';ctx.fillRect(x+12+b*2,y+56,1,3);}
   }else{
     ctx.fillStyle='#822027';ctx.fillRect(x+7,y+9,50,1);ctx.fillRect(x+7,y+50,50,1);
     const glyphs=[
       [[0,0],[2,3],[4,2],[5,0],[6,2],[8,3],[10,0],[9,5],[6,6],[6,10],[4,10],[4,6],[1,5]],
       [[0,4],[5,0],[10,4],[7,6],[5,10],[3,6]],
       [[0,2],[8,0],[10,3],[6,4],[6,6],[9,7],[8,9],[5,9],[4,11],[2,10],[3,7],[1,6],[3,4]],
       [[1,1],[9,1],[9,3],[5,3],[5,5],[8,5],[8,7],[5,7],[5,10],[3,10],[3,3],[1,3]],
       [[5,0],[10,5],[9,8],[6,8],[7,10],[3,10],[4,8],[1,8],[0,5]],
       [[0,0],[3,3],[5,1],[7,3],[10,0],[8,5],[10,7],[7,8],[7,10],[3,10],[3,8],[0,7],[2,5]],
       [[1,0],[4,4],[5,0],[6,4],[9,0],[8,5],[6,7],[6,10],[4,10],[4,7],[2,5]],
       [[0,2],[3,0],[5,2],[7,0],[10,2],[9,6],[5,10],[1,6]],
       [[0,1],[4,4],[5,0],[6,4],[10,1],[9,7],[7,5],[5,10],[3,5],[1,7]]
     ];
     for(let i=0;i<glyphs.length;i++)poly(glyphs[i],'#b33440',x+10+(i%3)*17,y+12+Math.floor(i/3)*12);
     ctx.fillStyle='#c8c8d1';ctx.fillRect(x+11,y+54,42,1);ctx.fillRect(x+13,y+5,5,2);ctx.fillRect(x+43,y+5,8,2);
   }
 }
 const png=canvas.toDataURL('image/png');Blockbench.writeFile(source+'/rider_metals.png',{content:png,savetype:'image'});Blockbench.writeFile(assets+'/textures/author/rider_metals.png',{content:png,savetype:'image'});
 const tile=name=>{const i=names.indexOf(name);if(i<0)throw Error('Unknown material '+name);return [i%8*64,Math.floor(i/8)*64,i%8*64+64,Math.floor(i/8)*64+64];};
 const projects=[];
 function project(name,format){newProject(format);Project.name=name;Project.texture_width=Project.texture_height=512;return new Texture({name:'rider_metals.png',width:512,height:512}).fromDataURL(png).add(false);}
 function save(name){Canvas.updateAll();const text=Codecs.project.compile({compressed:false});write(source+'/'+name+'.bbmodel',text);projects.push(name);}
 const offsets={head:[0,24,0],body:[0,24,0],left_arm:[5,22,0],right_arm:[-5,22,0],left_leg:[2,12,0],right_leg:[-2,12,0]};
 for(const [name,bones]of Object.entries(data.forms)){
   const texture=project(name,Formats.free),rig=new Map(),baseBones=new Map();
   for(const [bone,parts]of Object.entries(bones)){
     const o=offsets[bone],group=new Group({name:bone,origin:o}).init();baseBones.set(bone,group);
     for(const part of parts){
       let destination=group;
       if(part.animation){
         const a=part.animation,key=bone+':'+a.bone;
         if(!rig.has(key)){
           const pivot=[a.pivot[0]+o[0],-a.pivot[1]+o[1],-a.pivot[2]+o[2]];
           rig.set(key,{group:new Group({name:a.bone,origin:pivot}).addTo(group).init(),spec:a});
         }
         destination=rig.get(key).group;
       }
       part.uv=tile(part.material);part.material='fantasyhub:author/rider_metals';
       if(part.vertices){
         const vertices=Object.fromEntries(part.vertices.map((v,i)=>['v'+i,[v[0]+o[0],-v[1]+o[1],-v[2]+o[2]]]));
         const mesh=new Mesh({name:part.name,vertices,faces:{},visibility:!part.requires_force}).addTo(destination).init(),faces=[];
         for(const polygon of part.faces){
           if(polygon.length>4){
             // Project onto the polygon's own plane. Side temple mechanisms
             // lie in Y/Z; projecting every mesh onto X/Y drops those faces.
             const normal=new THREE.Vector3();
             for(let j=0;j<polygon.length;j++){
               const a=part.vertices[polygon[j]],b=part.vertices[polygon[(j+1)%polygon.length]];
               normal.x+=(a[1]-b[1])*(a[2]+b[2]);normal.y+=(a[2]-b[2])*(a[0]+b[0]);normal.z+=(a[0]-b[0])*(a[1]+b[1]);
             }
             const axes=Math.abs(normal.x)>Math.abs(normal.y)&&Math.abs(normal.x)>Math.abs(normal.z)?[1,2]:Math.abs(normal.y)>Math.abs(normal.z)?[0,2]:[0,1];
             const points=polygon.map(i=>new THREE.Vector2(part.vertices[i][axes[0]],part.vertices[i][axes[1]]));
             const sign=points.reduce((s,p,i)=>s+p.x*points[(i+1)%points.length].y-p.y*points[(i+1)%points.length].x,0);
             for(const triangle of THREE.ShapeUtils.triangulateShape(points,[])){
               let ids=triangle.map(i=>polygon[i]);if(sign<0)ids=[ids[0],ids[2],ids[1]];faces.push(ids);
             }
           }else faces.push(polygon);
         }
         part.faces=faces;
         for(const polygon of faces){
           const points=polygon.map(i=>part.vertices[i]),normal=new THREE.Vector3().subVectors(new THREE.Vector3(...points[1]),new THREE.Vector3(...points[0])).cross(new THREE.Vector3().subVectors(new THREE.Vector3(...points[2]),new THREE.Vector3(...points[0])));
           const ax=Math.abs(normal.x)>Math.abs(normal.z)?2:0,ay=Math.abs(normal.y)>Math.abs(normal.z)?2:1;
           const loX=Math.min(...points.map(v=>v[ax])),hiX=Math.max(...points.map(v=>v[ax])),loY=Math.min(...points.map(v=>v[ay])),hiY=Math.max(...points.map(v=>v[ay]));
           const uv=Object.fromEntries(polygon.map(i=>{const v=part.vertices[i];return ['v'+i,[part.uv[0]+(part.uv[2]-part.uv[0])*(v[ax]-loX)/Math.max(.001,hiX-loX),part.uv[1]+(part.uv[3]-part.uv[1])*(v[ay]-loY)/Math.max(.001,hiY-loY)]];}));
           mesh.addFaces(new MeshFace(mesh,{vertices:polygon.map(i=>'v'+i),uv,texture:texture.uuid}));
         }
       }else{
         const from=[part.from[0]+o[0],-part.from[1]-part.size[1]+o[1],-part.from[2]-part.size[2]+o[2]],cube=new Cube({name:part.name,from,to:from.map((v,i)=>v+part.size[i]),autouv:0}).addTo(destination).init();
         if(part.rotation){cube.rotation=[-part.rotation[0],part.rotation[1],-part.rotation[2]];cube.origin=[part.pivot[0]+o[0],-part.pivot[1]+o[1],-part.pivot[2]+o[2]];}
         for(const face of Object.values(cube.faces)){face.texture=texture.uuid;face.uv=part.uv.slice();}
       }
     }
   }
   if(rig.size){
     const length=Math.max(...[...rig.values()].map(r=>r.spec.period))*2,clip=new Animation({name:'flight',length,loop:'loop'}).add();clip.select();
     for(const {group,spec:a} of rig.values()){
       const animator=clip.getBoneAnimator(group);
       for(let t=0;t<=length+.001;t+=.1){
         const angle=Math.sin((t/a.period+a.phase)*Math.PI*2)*a.amplitude;
         animator.addKeyframe({channel:'rotation',time:Math.min(t,length),interpolation:'linear',data_points:[{x:'0',y:'0',z:'0',[a.axis]:String(a.axis==='y'?angle:-angle)}]});
       }
     }
   } else if(['ryuki','ryuki_survive','knight','decade'].includes(name)) {
     const poses={idle:{},walk:{left_leg:[30,0,0],right_leg:[-30,0,0],left_arm:[-20,0,0],right_arm:[20,0,0]},sprint:{left_leg:[45,0,0],right_leg:[-45,0,0],left_arm:[-40,0,0],right_arm:[40,0,0]},jump:{left_leg:[25,0,0],right_leg:[-35,0,0]},sword:{right_arm:[-90,0,-30],body:[0,25,0]},punch:{right_arm:[-90,0,0]},casting:{left_arm:[-65,25,0],right_arm:[-50,-25,0]},block:{left_arm:[-70,40,0],right_arm:[-70,-40,0]},hit:{body:[-12,0,0]},rider_kick:{right_leg:[-77,0,0],left_leg:[35,0,0],body:[12,0,0]},henshin:{left_arm:[-65,25,0],right_arm:[-60,-25,0]},recovery:{}};
     for(const [pose,bones]of Object.entries(poses)){
       const loop=['idle','walk','sprint'].includes(pose),clip=new Animation({name:pose,length:1,loop:loop?'loop':'once'}).add();clip.select();
       for(const [bone,rotation]of Object.entries(bones)){
         const animator=clip.getBoneAnimator(baseBones.get(bone));
         for(const [t,weight]of [[0,0],[.25,1],[.65,loop?-1:1],[1,0]])animator.addKeyframe({channel:'rotation',time:t,interpolation:'linear',data_points:[{x:String(rotation[0]*weight),y:String(rotation[1]*weight),z:String(rotation[2]*weight)}]});
       }
     }
   }
   save(name);
   if(['ryuki','ryuki_survive','knight','decade'].includes(name)){
     const helmet=JSON.parse(Codecs.project.compile({compressed:false})),head=baseBones.get('head'),ids=new Set(head.children.map(e=>e.uuid));
     helmet.name=name+'_helmet';helmet.elements=helmet.elements.filter(e=>ids.has(e.uuid));helmet.groups=helmet.groups.filter(g=>g.uuid===head.uuid);helmet.outliner=helmet.outliner.filter(g=>g.uuid===head.uuid);helmet.animations=[];
     write(source+'/'+name+'_helmet.bbmodel',helmet);projects.push(name+'_helmet');
   }
 }
 const itemModels=[];
 for(const [name,elements]of Object.entries(data.items)){
   const texture=project(name,Formats.java_block),gun=['magna_visor','giga_launcher','ride_booker_gun'].includes(name),belt=name.endsWith('_belt');
   const display=belt?{gui:{rotation:[15,215,0],translation:[0,0,0],scale:[.85,.85,.85]},fixed:{rotation:[0,0,0],translation:[0,0,0],scale:[1,1,1]}}:gun?{
     firstperson_righthand:{rotation:[0,-90,0],translation:[0,2,-3.5],scale:[.5,.5,.5]},thirdperson_righthand:{rotation:[0,-90,0],translation:[0,1.8,-3.15],scale:[.45,.45,.45]},
     firstperson_lefthand:{rotation:[0,-90,0],translation:[0,2,-3.5],scale:[.5,.5,.5]},thirdperson_lefthand:{rotation:[0,-90,0],translation:[0,1.8,-3.15],scale:[.45,.45,.45]},gui:{rotation:[30,225,0],translation:[0,0,0],scale:[.5,.5,.5]}}:{
     firstperson_righthand:{rotation:[0,-90,25],translation:[1.13,3.2,1.13],scale:[.6,.6,.6]},thirdperson_righthand:{rotation:[0,-90,55],translation:[0,4,.5],scale:[.65,.65,.65]},
     firstperson_lefthand:{rotation:[0,90,-25],translation:[1.13,3.2,1.13],scale:[.6,.6,.6]},thirdperson_lefthand:{rotation:[0,90,-55],translation:[0,4,.5],scale:[.65,.65,.65]},gui:{rotation:[0,0,-35],translation:[0,-1,0],scale:[.45,.45,.45]}};
   if(name==='shining_caliber'){
     for(const hand of ['righthand','lefthand']){
       display['thirdperson_'+hand].scale=[.52,.52,.52];
       display['firstperson_'+hand].scale=[.55,.55,.55];
     }
   }
   if(['flame_saber','storm_halberd','shining_caliber','drag_saber','dark_visor','dark_blade','ride_booker_sword','ride_booker_gun'].includes(name)){
     // Calibrate the centre of the real handle to the authored glove centre,
     // through vanilla HeldItemFeatureRenderer's -90X/+180Y/palm transform.
     const grip=elements.find(e=>['red_grip','storm_hand_grip','shining_grip','drag_grip','dark_grip','booker_grip'].includes(e.name));
     const at=grip.from.map((v,i)=>(v+grip.to[i])/2-8);
     for(const hand of ['righthand','lefthand']){
       const entry=display['thirdperson_'+hand],left=hand==='lefthand';
       const q=new THREE.Vector3(...at).multiply(new THREE.Vector3(...entry.scale));
       const r=entry.rotation.map(v=>v*Math.PI/180);if(left){r[1]*=-1;r[2]*=-1;}
       q.applyEuler(new THREE.Euler(...r,'XYZ'));
       entry.translation=[(left?-1:1)*-q.x,-1.8-q.y,.3-q.z];
     }
   }
   DisplayMode.loadJSON(display);
   const exported=[];
   for(const e of elements){
     let uv=tile(e.material);if(e.uv_crop){const [u,v,U,V]=uv,[x,y,X,Y]=e.uv_crop;uv=[u+(U-u)*x,v+(V-v)*y,u+(U-u)*X,v+(V-v)*Y];}
     const cube=new Cube({name:e.name,from:e.from,to:e.to,autouv:0}).init();for(const face of Object.values(cube.faces)){face.texture=texture.uuid;face.uv=uv.slice();}
     if(e.rotation){cube.rotation=[0,0,0];cube.rotation[{x:0,y:1,z:2}[e.rotation.axis]]=e.rotation.angle;cube.origin=e.rotation.origin;}
     exported.push({from:e.from,to:e.to,...(e.rotation?{rotation:e.rotation}:{}),faces:Object.fromEntries(['north','south','east','west','up','down'].map(face=>[face,{texture:'#metal',uv:uv.map(n=>n/32)}]))});
   }
   write(assets+'/models/item/riders/'+name+'.json',{textures:{metal:'fantasyhub:author/rider_metals',particle:'fantasyhub:author/rider_metals'},elements:exported,display});itemModels.push('fantasyhub:item/riders/'+name);save(name);
 }
 write(assets+'/authored/rider_forms.json',{texture_size:512,forms:data.forms});write(assets+'/riders-models.json',itemModels);
 const atlasPath=root+'/client/src/imported/resources/assets/minecraft/atlases/blocks.json',atlas=JSON.parse(await read(atlasPath));
 if(!atlas.sources.some(s=>s.resource==='fantasyhub:author/rider_metals'))atlas.sources.push({type:'minecraft:single',resource:'fantasyhub:author/rider_metals',sprite:'fantasyhub:author/rider_metals'});write(atlasPath,atlas);
 const report={tool:'Blockbench '+Blockbench.version,projects,materials:names,textureSize:512,references:data.references,notes:'Original painted metallic bevels, compound lenses, cloth weave, square shoulders. Native geometry and editable projects; not a screenshot texture.'};write(source+'/authoring.json',report);
 return report;
})()
