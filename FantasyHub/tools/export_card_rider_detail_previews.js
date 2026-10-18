// Authoring evidence from real Blockbench geometry, never a replacement for native Minecraft QA.
(async()=>{
 const root='/workspace/integration',dir=root+'/docs/qa/rider-models/blockbench/master-detail';
 const names=['rider_card','drag_shield','drag_visor','drag_claw','drag_visor_z','ktouch','kuuga_ultimate','blade_king','kiva_emperor','knight_cloak','decade_belt','dragranzer','darkwing'];
 for(const name of names){
   const project=ModelProject.all.filter(p=>p.name===name).at(-1);if(!project)throw Error('Missing model '+name);project.select();
   if(Animation.selected)Animation.selected.togglePlayingState(false);
   const view=Preview.selected,monster=['dragranzer','darkwing'].includes(name),item=name==='decade_belt';
   view.camera.position.set(monster?68:35,monster?50:38,monster?100:item?-60:60);view.controls.target.set(0,monster?32:item?8:24,0);view.controls.update();Canvas.updateAll();
   const png=await new Promise(resolve=>view.screenshot({width:640,height:640},resolve));
   Blockbench.writeFile(dir+'/'+name+'.png',{content:png,savetype:'image'});
 }
 return {tool:'Blockbench '+Blockbench.version,views:names.length,nativeRuntimeQA:false};
})()
