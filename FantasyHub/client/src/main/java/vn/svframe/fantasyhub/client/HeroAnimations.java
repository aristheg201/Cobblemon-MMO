package vn.svframe.fantasyhub.client;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
/** Full-body skill poses with anticipation, action and recovery; idle locomotion stays with EMF. */
final class HeroAnimations {
 static void animate(PlayerEntityModel<?> m,LivingEntity e,HeroVisuals.State s,HeroVisuals.Cue c,long tick){
  if(s!=null&&(s.flags()&1)!=0){m.rightArm.pitch=m.leftArm.pitch=.25f;m.rightArm.roll=-.15f;m.leftArm.roll=.15f;m.rightLeg.pitch=m.leftLeg.pitch=.1f;}
  if(s!=null&&(s.flags()&(1<<11))!=0){m.rightArm.pitch=m.leftArm.pitch=-.4f;m.rightArm.roll=-1.35f;m.leftArm.roll=1.35f;m.rightLeg.pitch=m.leftLeg.pitch=.15f;}
  if(s!=null&&(s.flags()&128)!=0){m.rightArm.pitch=m.leftArm.pitch=-1.25f;m.rightArm.roll=-.5f;m.leftArm.roll=.5f;}
  if(s!=null&&(s.flags()&256)!=0&&s.hero().equals("THOR")){m.rightArm.pitch=-1.4f+(float)Math.sin(e.age*.8)*.7f;m.rightArm.roll=-.55f;m.body.yaw=(float)Math.sin(e.age*.35)*.6f;}
  if(c==null){if(s!=null)sync(m);return;}
  float t=(tick-c.start())/(float)Math.max(1,c.expires()-c.start()),w=(float)(Math.sin(Math.PI*Math.clamp(t,0,1))),action=(float)Math.sin(t*Math.PI*2);
  m.body.yaw=0;m.body.pitch=0;
  switch(c.hero()){
   case "SPIDER_MAN"->{
    if(c.ability().equals("WEB_SWING")){m.rightArm.pitch=-2.8f*w;m.leftArm.pitch=-1.9f*w;m.body.pitch=.35f*w;m.rightLeg.pitch=-.9f*w;m.leftLeg.pitch=.6f*w;}
    else if(c.ability().equals("AIR_COMBO")||c.ability().equals("WEB_BLITZ")){float kick=(float)Math.sin(t*24);m.body.yaw=kick*.45f*w;m.rightArm.pitch=(-1.6f+kick*.8f)*w;m.leftArm.pitch=(-1.6f-kick*.8f)*w;m.rightLeg.pitch=kick*w;m.leftLeg.pitch=-kick*w;}
    else {m.body.pitch=.2f*w;m.body.yaw=-.3f*w;m.rightArm.pitch=-1.6f*w;m.rightArm.yaw=-.25f*w;m.leftArm.pitch=-.4f*w;m.rightLeg.pitch=.3f*w;m.leftLeg.pitch=-.3f*w;}
   }
   case "SORCERER_SUPREME"->{m.rightArm.pitch=(-1.25f+.25f*action)*w;m.leftArm.pitch=(-1.25f-.25f*action)*w;m.rightArm.yaw=-.4f*w;m.leftArm.yaw=.4f*w;m.rightArm.roll=-.5f*w;m.leftArm.roll=.5f*w;m.body.yaw=.22f*action*w;m.rightLeg.pitch=-.1f*w;m.leftLeg.pitch=.18f*w;}
   case "IRON_MAN"->{
    m.body.pitch=-.15f*w;m.rightLeg.pitch=.15f*w;m.leftLeg.pitch=-.2f*w;
    if(c.ability().equals("UNIBEAM")||c.ability().equals("ORBITAL_STRIKE")){m.rightArm.roll=-.6f*w;m.leftArm.roll=.6f*w;m.rightArm.pitch=.15f*w;m.leftArm.pitch=.15f*w;}
    else{m.rightArm.pitch=-1.65f*w;m.rightArm.yaw=-.15f*w;m.leftArm.pitch=-.4f*w;m.body.yaw=-.25f*w;}
   }
   case "THOR"->{
    if(c.ability().contains("SLAM")||c.ability().equals("BRING_ME_THANOS")){float lift=t<.6?-2.8f:1.1f;m.rightArm.pitch=lift*w;m.leftArm.pitch=lift*.7f*w;m.body.pitch=t<.6?-.3f*w:.7f*w;m.rightLeg.pitch=-.65f*w;m.leftLeg.pitch=.65f*w;}
    else if(c.ability().equals("LIGHTNING_SPIN")){m.body.yaw=(float)Math.sin(t*30)*1.3f*w;m.rightArm.roll=-1.3f*w;m.leftArm.roll=.8f*w;}
    else{m.body.yaw=action*.5f*w;m.rightArm.pitch=(-2.2f+1.4f*t)*w;m.rightArm.roll=-.35f*w;m.leftArm.pitch=-.4f*w;m.leftLeg.pitch=-.25f*w;}
   }
   case "FLASH"->{float run=(float)Math.sin(t*35);m.body.pitch=.4f*w;m.rightArm.pitch=(-.5f+run)*w;m.leftArm.pitch=(-.5f-run)*w;m.rightLeg.pitch=-run*w;m.leftLeg.pitch=run*w;}
   case "BATMAN"->{float punch=(float)Math.sin(t*20);m.body.yaw=.35f*punch*w;m.body.pitch=.15f*w;m.rightArm.pitch=(-1.5f+.65f*punch)*w;m.leftArm.pitch=(-1.3f-.65f*punch)*w;m.rightLeg.pitch=.35f*w;m.leftLeg.pitch=-.35f*w;}
  }
  sync(m);
 }
 private static void sync(PlayerEntityModel<?> m){m.hat.copyTransform(m.head);m.jacket.copyTransform(m.body);m.rightSleeve.copyTransform(m.rightArm);m.leftSleeve.copyTransform(m.leftArm);m.rightPants.copyTransform(m.rightLeg);m.leftPants.copyTransform(m.leftLeg);}
}
