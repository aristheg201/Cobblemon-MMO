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
 const png=canvas.toDataURL('image/png');Blockbench.writeFile(source+'/rider_metals.png',{content:png,savetype:'image'});Blockbench.writeFile(assets+'/textures/author/rider_metals.png',{content:png,savetype:'image'});
 const tile=name=>{const i=names.indexOf(name);if(i<0)throw Error('Unknown material '+name);return [i%8*64,Math.floor(i/8)*64,i%8*64+64,Math.floor(i/8)*64+64];};
 const projects=[];
 function project(name,format){newProject(format);Project.name=name;Project.texture_width=Project.texture_height=512;return new Texture({name:'rider_metals.png',width:512,height:512}).fromDataURL(png).add(false);}
 function save(name){Canvas.updateAll();const text=Codecs.project.compile({compressed:false});write(source+'/'+name+'.bbmodel',text);projects.push(name);}
 const offsets={head:[0,24,0],body:[0,24,0],left_arm:[5,22,0],right_arm:[-5,22,0],left_leg:[2,12,0],right_leg:[-2,12,0]};
 for(const [name,bones]of Object.entries(data.forms)){
   const texture=project(name,Formats.free);
   for(const [bone,parts]of Object.entries(bones)){
     const o=offsets[bone],group=new Group({name:bone,origin:o}).init();
     for(const part of parts){
       part.uv=tile(part.material);part.material='fantasyhub:author/rider_metals';
       if(part.vertices){
         const vertices=Object.fromEntries(part.vertices.map((v,i)=>['v'+i,[v[0]+o[0],-v[1]+o[1],-v[2]+o[2]]]));
         const mesh=new Mesh({name:part.name,vertices,faces:{},visibility:!part.requires_force}).addTo(group).init(),faces=[];
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
         const from=[part.from[0]+o[0],-part.from[1]-part.size[1]+o[1],-part.from[2]-part.size[2]+o[2]],cube=new Cube({name:part.name,from,to:from.map((v,i)=>v+part.size[i]),autouv:0}).addTo(group).init();
         if(part.rotation){cube.rotation=[-part.rotation[0],part.rotation[1],-part.rotation[2]];cube.origin=[part.pivot[0]+o[0],-part.pivot[1]+o[1],-part.pivot[2]+o[2]];}
         for(const face of Object.values(cube.faces)){face.texture=texture.uuid;face.uv=part.uv.slice();}
       }
     }
   }
   save(name);
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
