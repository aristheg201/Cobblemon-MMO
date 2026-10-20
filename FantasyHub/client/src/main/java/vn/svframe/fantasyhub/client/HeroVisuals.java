package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.*;
import net.minecraft.util.math.*;
import org.joml.Vector3f;
import vn.svframe.fantasyhub.protocol.Protocol;

/** Server-driven presentation only; no client damage, teleport or resource authority. */
public final class HeroVisuals {
  record State(String hero,int flags,int stacks,int speed,int heat,long expires){}
  record Cue(int entity,String hero,String ability,String phase,Vec3d from,Vec3d to,long start,long expires){}
  private static final Map<Integer,State> STATES=new HashMap<>();
  private static final Map<Integer,Cue> CASTS=new HashMap<>();
  private static final ArrayDeque<Cue> EFFECTS=new ArrayDeque<>();
  private static long ticks;
  private static int lastFlags=-1;
  private static boolean installed;
  static Cue cue(int entity){var c=CASTS.get(entity);return c!=null&&c.expires>ticks?c:null;}
  static State state(int entity){return STATES.get(entity);}
  static void clear(){STATES.clear();CASTS.clear();EFFECTS.clear();HeroEffects.clear();BetterCombatCompat.clear();lastFlags=-1;}
  private static Vec3d point(JsonObject d,String key){var a=d.getAsJsonArray(key);return new Vec3d(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
  static void accept(Protocol.Hero packet) {
    var d=JsonParser.parseString(packet.json()).getAsJsonObject();int entity=d.get("entity").getAsInt();String kind=d.get("kind").getAsString(),hero=d.get("hero").getAsString();
    if(kind.equals("remove")){STATES.remove(entity);CASTS.remove(entity);return;}
    if(STATES.size()>128&&!STATES.containsKey(entity))return;
    if(kind.equals("state")){STATES.put(entity,new State(hero,d.get("flags").getAsInt(),d.get("stacks").getAsInt(),d.get("speed").getAsInt(),d.get("heat").getAsInt(),ticks+40));return;}
    if(!kind.equals("cue"))return;
    String phase=d.get("phase").getAsString();long duration=Math.max(4,Math.min(160,(int)(d.get("duration").getAsDouble()*20)));
    var c=new Cue(entity,hero,d.get("ability").getAsString(),phase,point(d,"from"),point(d,"to"),ticks,ticks+duration);
    if(phase.equals("CAST")){CASTS.put(entity,new Cue(entity,c.hero,c.ability,c.phase,c.from,c.to,c.start,Math.min(c.expires,ticks+(c.ability.equals("AIR_COMBO")?36:24))));BetterCombatCompat.skillStarted(entity);}
    if(phase.equals("BEAM")||phase.equals("CAST")||phase.equals("WEAPON_THROW")){if(EFFECTS.size()>=96)EFFECTS.removeFirst();EFFECTS.addLast(c);}
    if(phase.equals("IMPACT"))impact(c);
    HeroEffects.burst(c,ticks,quality());
  }
  static void tick(MinecraftClient client,long now) {
    ticks=now;BetterCombatCompat.tick(now);if(!installed){WorldRenderEvents.AFTER_ENTITIES.register(HeroVisuals::render);installed=true;}
    STATES.values().removeIf(s->s.expires<=ticks);CASTS.values().removeIf(c->c.expires<=ticks);EFFECTS.removeIf(c->c.expires<=ticks);
    if(client.player==null||client.world==null)return;
    String id=FantasyHubClient.string("classId");boolean hero=Set.of("SPIDER_MAN","SORCERER_SUPREME","IRON_MAN","THOR","FLASH","BATMAN").contains(id);
    if(hero){int flags=0;if(client.currentScreen==null){if(client.options.jumpKey.isPressed())flags|=1;if(client.options.forwardKey.isPressed())flags|=2;if(client.options.sneakKey.isPressed())flags|=4;if(client.options.sprintKey.isPressed())flags|=8;}if(flags!=lastFlags||now%4==0){FantasyHubClient.send("hero_input","",flags);lastFlags=flags;}}
    if(!hero)lastFlags=-1;
    if(now%2==0)for(var entry:STATES.entrySet()) {
      var entity=client.world.getEntityById(entry.getKey());if(entity==null||client.player.squaredDistanceTo(entity)>2304)continue;
      var s=entry.getValue();if((s.flags&(1|8|16|32))!=0&&entity.getVelocity().lengthSquared()>.005) {
        int count=quality()==0?1:quality()==1?2:4;for(int i=0;i<count;i++)client.world.addParticle(dust(s.hero),entity.getX()+(client.world.random.nextDouble()-.5)*.6,entity.getY()+client.world.random.nextDouble()*1.6,entity.getZ()+(client.world.random.nextDouble()-.5)*.6,0,0,0);
      }
    }
  }
  private static int quality(){return switch(FantasyHubClient.options.visualQuality){case "low"->0;case "high"->2;default->1;};}
  private static int color(String hero){return switch(hero){case "SPIDER_MAN"->0xffe9eef5;case "SORCERER_SUPREME"->0xffff8a24;case "IRON_MAN"->0xff8cecff;case "THOR"->0xff9dcaff;case "FLASH"->0xffffc336;default->0xff8397b0;};}
  private static DustParticleEffect dust(String hero){int color=color(hero);return new DustParticleEffect(new Vector3f(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f),.8f);}
  private static void impact(Cue c){var client=MinecraftClient.getInstance();if(client.world==null||client.player==null||client.player.squaredDistanceTo(c.from)>1600)return;
    int n=quality()==0?8:quality()==1?18:32;for(int i=0;i<n;i++){double angle=i*2*Math.PI/n;client.world.addParticle(dust(c.hero),c.from.x,c.from.y+.6,c.from.z,Math.cos(angle)*.18,.025,Math.sin(angle)*.18);}
  }
  private static void render(WorldRenderContext context) {
    if(context.matrixStack()==null||context.consumers()==null)return;var matrices=context.matrixStack();Vec3d camera=context.camera().getPos();
    HeroEffects.render(context,EFFECTS,ticks,quality());
    matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);var vertices=context.consumers().getBuffer(RenderLayer.getLines());
    for(var cue:EFFECTS){if(cue.from.squaredDistanceTo(camera)>4096)continue;
      double age=(ticks-cue.start)/20d;int color=color(cue.hero);
      if(cue.phase.equals("BEAM"))continue;
      if(cue.ability.equals("WORLD_WEB_TRAP")){for(int i=0;i<10;i++){double theta=i*Math.PI/5;var edge=cue.to.add(Math.cos(theta)*6,.12,Math.sin(theta)*6);line(matrices,vertices,cue.to.add(0,.12,0),edge,color);}for(int i=1;i<5;i++)ring(matrices,vertices,cue.to.add(0,.12,0),i*1.5,1,0,color);}
      else if(cue.ability.equals("ORBITAL_STRIKE")||cue.ability.equals("BATWING_STRIKE")){ring(matrices,vertices,cue.to.add(0,.1,0),5,1,age,color);line(matrices,vertices,cue.to,cue.to.add(0,20,0),color);}
    }
    var client=MinecraftClient.getInstance();if(client.world!=null)for(var row:STATES.entrySet()){
      var state=row.getValue();var e=client.world.getEntityById(row.getKey());if(e==null||e.squaredDistanceTo(camera)>2304)continue;
      if((state.flags&64)!=0)for(int i=0;i<4;i++)ring(matrices,vertices,e.getPos().add(0,.3+i*.4,0),.7,1,ticks*.02,color(state.hero));
    }
    matrices.pop();
  }
  private static void mandala(MatrixStack m,VertexConsumer v,Vec3d center,double scale,double angle,int color){
    // Original Fisk tao_mandala: concentric circles and counter-rotating square seals.
    for(double radius:new double[]{.3,.625,.775,.875,.95,1})ring(m,v,center,radius*scale,0,angle,color);
    polygon(m,v,center,4,.625*scale,angle,color);polygon(m,v,center,4,.625*scale,angle+Math.PI/4,color);polygon(m,v,center,4,.3*scale,-angle,color);
  }
  private static void ring(MatrixStack m,VertexConsumer v,Vec3d center,double r,int horizontal,double angle,int color){int n=quality()==0?20:quality()==1?32:48;for(int i=0;i<n;i++){double a=i*2*Math.PI/n+angle,b=(i+1)*2*Math.PI/n+angle;Vec3d p=horizontal==1?new Vec3d(Math.cos(a)*r,0,Math.sin(a)*r):new Vec3d(Math.cos(a)*r,Math.sin(a)*r,0),q=horizontal==1?new Vec3d(Math.cos(b)*r,0,Math.sin(b)*r):new Vec3d(Math.cos(b)*r,Math.sin(b)*r,0);line(m,v,center.add(p),center.add(q),color);}}
  private static void polygon(MatrixStack m,VertexConsumer v,Vec3d center,int n,double r,double angle,int color){for(int i=0;i<n;i++){double a=i*2*Math.PI/n+angle,b=(i+1)*2*Math.PI/n+angle;line(m,v,center.add(Math.cos(a)*r,Math.sin(a)*r,0),center.add(Math.cos(b)*r,Math.sin(b)*r,0),color);}}
  private static void line(MatrixStack m,VertexConsumer v,Vec3d a,Vec3d b,int color){Vec3d n=b.subtract(a).normalize();v.vertex(m.peek(),(float)a.x,(float)a.y,(float)a.z).color(color).normal(m.peek(),(float)n.x,(float)n.y,(float)n.z);v.vertex(m.peek(),(float)b.x,(float)b.y,(float)b.z).color(color).normal(m.peek(),(float)n.x,(float)n.y,(float)n.z);}
  public static void animate(PlayerEntityModel<?> model,LivingEntity entity,float age) {
    var s=STATES.get(entity.getId());if(s!=null&&(s.flags&1)!=0){model.rightArm.pitch=model.leftArm.pitch=.25f;model.rightArm.roll=-.15f;model.leftArm.roll=.15f;model.rightLeg.pitch=.1f;model.leftLeg.pitch=.1f;}
    var cue=cue(entity.getId());if(cue==null)return;float time=(ticks-cue.start)/20f,fade=(float)Math.min(1,Math.min(time*8,(cue.expires-ticks)/4f));
    if(cue.ability.contains("SLAM")||cue.ability.equals("WEB_SWING")){model.rightArm.pitch-=fade*1.8f;model.leftArm.pitch-=fade*1.8f;model.rightLeg.pitch-=fade*.6f;return;}
    if(cue.hero.equals("SPIDER_MAN")){model.rightArm.pitch-=fade*1.5f;model.rightArm.yaw-=fade*.2f;}
    else if(cue.hero.equals("SORCERER_SUPREME")){model.rightArm.pitch-=fade*1.25f;model.leftArm.pitch-=fade*1.25f;model.rightArm.roll-=fade*.35f;model.leftArm.roll+=fade*.35f;}
    else if(cue.hero.equals("IRON_MAN")){if(cue.ability.equals("UNIBEAM")){model.rightArm.roll-=fade*.3f;model.leftArm.roll+=fade*.3f;}else model.rightArm.pitch-=fade*1.6f;}
    else if(cue.hero.equals("THOR")){model.rightArm.pitch-=fade*(1.2f+(float)Math.sin(time*9)*.5f);}
    else {model.rightArm.pitch-=fade*(1+(float)Math.sin(time*18)*.6f);model.leftArm.pitch-=fade*(1-(float)Math.sin(time*18)*.6f);}
  }
}
