(async()=>{
 const root=globalThis.FANTASYHUB_ROOT||'/workspace/integration/FantasyHub',source=root+'/assets-source/kabuto/kunai_gun.bbmodel',target=root+'/client/src/imported/resources/assets/fantasyhub/models/item/additions/kunai_gun.json';
 const read=p=>new Promise(resolve=>Blockbench.read([p],{readtype:'text'},files=>resolve(JSON.parse(files[0].content))));
 const model=await read(source),resource=await read(target);
 newProject(Formats.java_block);Codecs.project.parse(model,source);
 const display=structuredClone(resource.display);
 for(const hand of ['righthand','lefthand']){
  display['thirdperson_'+hand].rotation=[0,-90,0];display['thirdperson_'+hand].translation=[0,1.8,-3.15];
  display['firstperson_'+hand].rotation=[0,-90,0];display['firstperson_'+hand].translation=[0,2,-3.5];
 }
 DisplayMode.loadJSON(display);Canvas.updateAll();
 const compiled=Codecs.project.compile({compressed:false});Blockbench.writeFile(source,{content:typeof compiled==='string'?compiled:JSON.stringify(compiled,null,2),savetype:'text'});
 resource.display=JSON.parse(typeof compiled==='string'?compiled:JSON.stringify(compiled)).display;
 Blockbench.writeFile(target,{content:JSON.stringify(resource,null,2),savetype:'text'});
 return {editor:Blockbench.version,cubes:Cube.all.length,display:resource.display};
})()
