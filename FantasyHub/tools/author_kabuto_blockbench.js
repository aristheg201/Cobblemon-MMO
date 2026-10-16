// Run inside Blockbench's native desktop renderer (DevTools/CDP).
// Author and save editable projects, paint the shared pixel-metal atlas, then
// export native Minecraft item models and the Fabric form geometry.
(async () => {
 const path={join:(...parts)=>parts.join('/')},inputs={};
 const fs={mkdirSync(){},readFileSync(p){return inputs[p]},writeFileSync(p,content){Blockbench.writeFile(p,{content,savetype:'text'})}};
 const root=globalThis.FANTASYHUB_ROOT||'/workspace/integration/FantasyHub',out=path.join(root,'client/src/imported/resources/assets/fantasyhub'),source=path.join(root,'assets-source/kabuto');
 for(const relative of ['authored/kabuto_forms.json',...['zecter_belt','hyper_zecter','kunai_gun','kunai'].map(n=>'models/item/additions/'+n+'.json')]){const p=path.join(out,relative);inputs[p]=await new Promise(resolve=>Blockbench.read([p],{readtype:'text'},files=>resolve(files[0].content)));}
 fs.mkdirSync(source,{recursive:true});fs.mkdirSync(path.join(out,'textures/author'),{recursive:true});
 const names=['red','red_light','black','silver','chrome','gold','blue','glass','yellow','dark'];
 const colors=[[152,12,28],[207,26,44],[25,29,36],[155,170,184],[182,196,210],[204,151,34],[12,130,183],[115,150,166],[255,204,60],[52,62,73]];
 const canvas=document.createElement('canvas');canvas.width=canvas.height=256;const ctx=canvas.getContext('2d');
 names.forEach((name,index)=>{
  const tx=index%4*64,ty=Math.floor(index/4)*64,c=colors[index];
  for(let y=0;y<64;y++)for(let x=0;x<64;x++){
   if(name==='glass'&&x>1&&x<62&&y>1&&y<62){ctx.clearRect(tx+x,ty+y,1,1);continue;}
   const metal=!['black','blue','dark','glass'].includes(name);
   let shade=.60+.30*(1-y/63)+.16*Math.cos((x/63-.28)*Math.PI);
   if(metal){shade+=.26*Math.exp(-Math.pow((x-18)/7,2));shade+=.12*Math.exp(-Math.pow((y-12)/3,2));}
   if(x<2||y<2)shade+=.21;if(x>61||y>61)shade-=.22;
   shade+=(((x*13+y*23)%11)-5)*.003;
   if(name==='blue'){
    shade=.68+.32*(1-y/63)+.12*Math.cos(x/63*Math.PI);const sx=(x+(Math.floor(y/6)%2)*3)%6,sy=y%6;
    if(sx===0||sy===0||((sx===1||sx===5)&&(sy===1||sy===5)))shade-=.15;
    if(x<3||y<2)shade+=.28;
   }
   if(name==='black'&&((x+y)%5===0))shade+=.13;
   let rgb=c.map(v=>Math.min(255,Math.max(0,Math.round(v*shade))));ctx.fillStyle=`rgb(${rgb.join(',')})`;ctx.fillRect(tx+x,ty+y,1,1);
  }
 });
 const png=canvas.toDataURL('image/png');Blockbench.writeFile(path.join(out,'textures/author/kabuto_metals.png'),{content:png,savetype:'image'});Blockbench.writeFile(path.join(source,'kabuto_metals.png'),{content:png,savetype:'image'});
 const suitCanvas=document.createElement('canvas');suitCanvas.width=64;suitCanvas.height=32;suitCanvas.getContext('2d').drawImage(canvas,128,0,64,64,0,0,64,32);const suitPng=suitCanvas.toDataURL('image/png');Blockbench.writeFile(path.join(out,'textures/author/kabuto_undersuit.png'),{content:suitPng,savetype:'image'});Blockbench.writeFile(path.join(source,'kabuto_undersuit.png'),{content:suitPng,savetype:'image'});
 const uv=name=>{const i=names.indexOf(name);if(i<0)throw Error('Unknown material '+name);return [i%4*64,Math.floor(i/4)*64,i%4*64+64,Math.floor(i/4)*64+64];};
 const palette={'minecraft:block/red_concrete':'red','minecraft:block/redstone_block':'red_light','minecraft:block/black_concrete':'black','minecraft:block/light_gray_concrete':'silver','minecraft:block/iron_block':'chrome','minecraft:block/gold_block':'gold','minecraft:block/light_blue_concrete':'blue','minecraft:block/glass':'glass','minecraft:block/yellow_concrete':'yellow','minecraft:block/gray_concrete':'dark'};
 let form=JSON.parse(fs.readFileSync(path.join(out,'authored/kabuto_forms.json'),'utf8'));let exported=[];
 async function project(name,format=Formats.free){newProject(format);Project.name=name;Project.texture_width=Project.texture_height=256;let texture=new Texture({name:'kabuto_metals.png',width:256,height:256}).fromDataURL(png).add(false);return texture;}
 function save(name){Canvas.updateAll();const text=Codecs.project.compile({compressed:false});fs.writeFileSync(path.join(source,name+'.bbmodel'),typeof text==='string'?text:JSON.stringify(text,null,2));exported.push(name);}
 for(const [variant,bones] of Object.entries(form.forms)){
  let texture=await project('Kabuto '+variant);for(const [bone,parts]of Object.entries(bones)){
   let offset={head:[0,24,0],body:[0,24,0],left_arm:[5,22,0],right_arm:[-5,22,0],left_leg:[2,12,0],right_leg:[-2,12,0]}[bone];
   const group=new Group({name:bone,origin:offset}).init();
   for(let i=0;i<parts.length;i++){
    let part=parts[i],material=palette[part.material];if(!material)throw Error(part.material);part.uv=uv(material);part.material='fantasyhub:author/kabuto_metals';
    if(part.vertices){let vertices={};part.vertices.forEach((v,j)=>vertices['v'+j]=[v[0]+offset[0],-v[1]+offset[1],-v[2]+offset[2]]);let mesh=new Mesh({name:bone+'_'+material+'_'+i,vertices,faces:{}}).addTo(group).init();
      for(const polygon of part.faces){
        const points=polygon.map(j=>vertices['v'+j]),normal=new THREE.Vector3().subVectors(new THREE.Vector3(...points[1]),new THREE.Vector3(...points[0])).cross(new THREE.Vector3().subVectors(new THREE.Vector3(...points[2]),new THREE.Vector3(...points[0])));
        const ax=Math.abs(normal.x)>Math.abs(normal.y)&&Math.abs(normal.x)>Math.abs(normal.z)?2:0,ay=Math.abs(normal.y)>Math.abs(normal.z)?2:1;
        let loX=Math.min(...points.map(v=>v[ax])),hiX=Math.max(...points.map(v=>v[ax])),loY=Math.min(...points.map(v=>v[ay])),hiY=Math.max(...points.map(v=>v[ay]));
        const coords={};polygon.forEach(j=>{let v=vertices['v'+j];coords['v'+j]=[part.uv[0]+(part.uv[2]-part.uv[0])*(v[ax]-loX)/Math.max(.001,hiX-loX),part.uv[1]+(part.uv[3]-part.uv[1])*(v[ay]-loY)/Math.max(.001,hiY-loY)];});
        for(let j=1;j<polygon.length-1;j++){let keys=[polygon[0],polygon[j],polygon[j+1]].map(k=>'v'+k);mesh.addFaces(new MeshFace(mesh,{vertices:keys,uv:Object.fromEntries(keys.map(k=>[k,coords[k]])),texture:texture.uuid}));}
      }
    }else{
      let from=[part.from[0]+offset[0],-part.from[1]-part.size[1]+offset[1],-part.from[2]-part.size[2]+offset[2]],to=from.map((v,j)=>v+part.size[j]);let cube=new Cube({name:bone+'_'+material+'_'+i,from,to,autouv:0}).addTo(group).init();
      if(part.rotation){cube.rotation=[-part.rotation[0],part.rotation[1],-part.rotation[2]];cube.origin=[part.pivot[0]+offset[0],-part.pivot[1]+offset[1],-part.pivot[2]+offset[2]];}
      for(let face of Object.values(cube.faces)){face.texture=texture.uuid;face.uv=part.uv.slice();}
    }
   }
  }save('kabuto_'+variant);
 }
 fs.writeFileSync(path.join(out,'authored/kabuto_forms.json'),JSON.stringify(form,null,2));
 for(const name of ['zecter_belt','hyper_zecter','kunai_gun','kunai']){
  let file=path.join(out,'models/item/additions',name+'.json'),data=JSON.parse(fs.readFileSync(file,'utf8'));let texture=await project(name,Formats.java_block);DisplayMode.loadJSON(data.display);
  for(let i=0;i<data.elements.length;i++){
   let e=data.elements[i],cube=new Cube({name:name+'_'+i,from:e.from,to:e.to,autouv:0}).init();if(e.rotation){cube.rotation=[0,0,0];cube.rotation[{x:0,y:1,z:2}[e.rotation.axis]]=e.rotation.angle;cube.origin=e.rotation.origin;}
   for(let [direction,face]of Object.entries(e.faces)){let material=face.texture.slice(1);let tile=uv(material);face.uv=tile.map(v=>v/16);cube.faces[direction].texture=texture.uuid;cube.faces[direction].uv=tile;}
  }
  for(let key of Object.keys(data.textures))if(key!=='particle')data.textures[key]='fantasyhub:author/kabuto_metals';data.textures.particle='fantasyhub:author/kabuto_metals';
  fs.writeFileSync(file,JSON.stringify(data,null,2));save(name);
 }
 const atlasPath=path.join(root,'client/src/imported/resources/assets/minecraft/atlases/blocks.json');const atlas=JSON.parse(await new Promise(resolve=>Blockbench.read([atlasPath],{readtype:'text'},files=>resolve(files[0].content))));if(!atlas.sources.some(s=>s.resource==='fantasyhub:author/kabuto_metals'))atlas.sources.push({type:'minecraft:single',resource:'fantasyhub:author/kabuto_metals',sprite:'fantasyhub:author/kabuto_metals'});fs.writeFileSync(atlasPath,JSON.stringify(atlas,null,2));
 const manifest={authoring:'Blockbench '+Blockbench.version,projects:exported,texture:'kabuto_metals.png',references:['1000391749.jpg','1000391748.jpg','1000391750.jpg','1000391751.jpg','1000391754.jpg','1000391753.jpg'],notes:'Silver brow wedge crown, wrapped patterned cyan visor, horizontal red/gold Zecter, same Kunai Gun frame with two real grip points. Original movie voices not supplied.'};fs.writeFileSync(path.join(source,'authoring.json'),JSON.stringify(manifest,null,2));
 return {version:Blockbench.version,projects:exported,cubes:Cube.all.length,meshes:Mesh.all.length};
})()
