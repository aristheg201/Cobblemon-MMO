// Real Blockbench renders used as item icons and authoring review, not in-game QA.
(async()=>{
 const root='/workspace/integration',assets=root+'/FantasyHub/client/src/imported/resources/assets/fantasyhub';
 const read=p=>new Promise(resolve=>Blockbench.read([p],{readtype:'text'},f=>resolve(f[0].content)));
 const write=(p,v)=>Blockbench.writeFile(p,{content:JSON.stringify(v,null,2),savetype:'text'});
 const manifest=JSON.parse(await read(assets+'/riders-models.json'));
 const names=['zolda','agito_ground','agito_storm','agito_flame','agito_trinity','agito_shining','ryuki','knight','decade','magnugiga','dragreder','dragranzer','darkwing'];
 for(const name of names){
  const project=ModelProject.all.filter(p=>p.name===name).at(-1);project.select();const view=Preview.selected;
  const visible=element=>!element.name.startsWith('cross_horn_expanded_');
  const elements=Outliner.elements.slice();
  for(const [angle,position] of Object.entries({front:[35,26,60],rear:[-35,26,-60],left:[65,25,-12],right:[-65,25,12]})){
   elements.forEach(e=>e.visibility=visible(e));
   const monster=["dragreder","dragranzer","darkwing"].includes(name),factor=monster?1.45:1;
   view.camera.position.set(position[0]*factor,monster?38:position[1],position[2]*factor);view.controls.target.set(0,monster?32:16,0);view.controls.update();Canvas.updateAll();
   const png=await new Promise(resolve=>view.screenshot({width:512,height:512},resolve));
   Blockbench.writeFile(root+'/docs/qa/rider-models/blockbench/'+name+'-'+angle+'.png',{content:png,savetype:'image'});
  }
  if(['magnugiga','dragreder','dragranzer','darkwing'].includes(name))continue;
  for(const [slot,bones]of Object.entries({helmet:['head'],chestplate:['body','left_arm','right_arm'],leggings:['left_leg','right_leg'],boots:['left_leg','right_leg']})){
   for(const e of elements){const boot=e.name.includes('boot_')||e.name.includes('ankle_');e.visibility=visible(e)&&bones.includes(e.parent.name)&&(!['leggings','boots'].includes(slot)||boot===(slot==='boots'));}
   view.camera.position.set(35,26,60);view.controls.target.set(0,16,0);view.controls.update();Canvas.updateAll();
   const png=await new Promise(resolve=>view.screenshot({width:128,height:128},resolve));
   const id=name+'_'+slot;Blockbench.writeFile(assets+'/textures/item/riders/'+id+'.png',{content:png,savetype:'image'});
   write(assets+'/models/item/riders/'+id+'.json',{parent:'minecraft:item/generated',textures:{layer0:'fantasyhub:item/riders/'+id}});manifest.push('fantasyhub:item/riders/'+id);
  }
  elements.forEach(e=>e.visibility=visible(e));Canvas.updateAll();
 }
 for(const name of ['magnugiga','dragreder','dragranzer','darkwing']){write(assets+'/models/item/riders/'+name+'.json',{parent:'minecraft:builtin/entity',textures:{particle:'fantasyhub:author/rider_metals'}});manifest.push('fantasyhub:item/riders/'+name);}
 write(assets+'/riders-models.json',[...new Set(manifest)]);
 return {armorIcons:36,authoringReviewViews:52,models:manifest.length};
})()
