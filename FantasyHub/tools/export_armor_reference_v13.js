// Screenshots from actual Blockbench geometry. Native game captures are separate.
(async()=>{
 const root='/workspace/integration',assets=root+'/FantasyHub/client/src/imported/resources/assets/fantasyhub',dir=root+'/docs/qa/armor-reference-v13/blockbench';
 const read=p=>new Promise(resolve=>Blockbench.read([p],{readtype:'text'},f=>resolve(f[0].content)));
 const write=(p,v)=>Blockbench.writeFile(p,{content:JSON.stringify(v,null,2),savetype:'text'});
 const manifest=JSON.parse(await read(assets+'/riders-models.json'));
 for(const name of ['ryuki','ryuki_survive','decade']){
  const project=ModelProject.all.filter(p=>p.name===name).at(-1);if(!project)throw Error(name);project.select();
  if(Animation.selected)Animation.selected.togglePlayingState(false);
  const view=Preview.selected,elements=Outliner.elements.slice();elements.forEach(e=>e.visibility=true);
  for(const [angle,pos]of Object.entries({front:[0,18,65],rear:[0,18,-65],left:[65,18,0],right:[-65,18,0],'three-quarter':[40,24,60],'helmet-front':[0,name==='ryuki_survive'?30:28,23],'helmet-side':[23,name==='ryuki_survive'?30:28,0],belt:[0,14,18]})){
   const target=angle.startsWith('helmet')?(name==='ryuki_survive'?30:28):angle==='belt'?14:18;
   view.camera.position.set(...pos);view.controls.target.set(0,target,0);view.controls.update();Canvas.updateAll();
   const png=await new Promise(resolve=>view.screenshot({width:768,height:768},resolve));Blockbench.writeFile(dir+'/'+name+'-'+angle+'.png',{content:png,savetype:'image'});
  }
  for(const [slot,bones]of Object.entries({helmet:['head'],chestplate:['body','left_arm','right_arm'],leggings:['left_leg','right_leg'],boots:['left_leg','right_leg']})){
   elements.forEach(e=>{const boot=e.name.includes('boot_')||e.name.includes('ankle_');e.visibility=bones.includes(e.parent.name)&&(!['leggings','boots'].includes(slot)||boot===(slot==='boots'));});
   view.camera.position.set(35,26,60);view.controls.target.set(0,16,0);view.controls.update();Canvas.updateAll();
   const png=await new Promise(resolve=>view.screenshot({width:128,height:128},resolve)),id=name+'_'+slot;
   Blockbench.writeFile(assets+'/textures/item/riders/'+id+'.png',{content:png,savetype:'image'});write(assets+'/models/item/riders/'+id+'.json',{parent:'minecraft:item/generated',textures:{layer0:'fantasyhub:item/riders/'+id}});manifest.push('fantasyhub:item/riders/'+id);
  }
  elements.forEach(e=>e.visibility=true);Canvas.updateAll();
 }
 write(assets+'/riders-models.json',[...new Set(manifest)]);
 return {tool:'Blockbench '+Blockbench.version,models:['ryuki','ryuki_survive','decade'],views:24,nativeRuntime:false};
})()
