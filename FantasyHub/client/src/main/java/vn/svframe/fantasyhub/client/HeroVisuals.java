package vn.svframe.fantasyhub.client;
import com.google.gson.*;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.*;
import net.minecraft.util.math.*;
import org.joml.Vector3f;
import vn.svframe.fantasyhub.protocol.Protocol;

/** Presentation of server-confirmed casts, collision points and states. No client combat authority. */
public final class HeroVisuals {
 record State(String hero,int flags,int stacks,int speed,int heat,long expires){}
 record Cue(int entity,String hero,String ability,String phase,Vec3d from,Vec3d to,long start,long expires,long cast,double radius){}
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
 static void accept(Protocol.Hero packet){
  var d=JsonParser.parseString(packet.json()).getAsJsonObject();int entity=d.get("entity").getAsInt();String kind=d.get("kind").getAsString(),hero=d.get("hero").getAsString();
  if(kind.equals("remove")){STATES.remove(entity);CASTS.remove(entity);EFFECTS.removeIf(c->c.entity()==entity);return;}
  if(STATES.size()>=128&&!STATES.containsKey(entity))return;
  if(kind.equals("state")){STATES.put(entity,new State(hero,d.get("flags").getAsInt(),d.get("stacks").getAsInt(),d.get("speed").getAsInt(),d.get("heat").getAsInt(),ticks+40));return;}
  if(!kind.equals("cue"))return;
  String phase=d.get("phase").getAsString();long duration=Math.clamp((int)(d.get("duration").getAsDouble()*20),2,240);
  var c=new Cue(entity,hero,d.get("ability").getAsString(),phase,point(d,"from"),point(d,"to"),ticks,ticks+duration,d.has("cast")?d.get("cast").getAsLong():0,d.has("radius")?Math.clamp(d.get("radius").getAsDouble(),0,16):0);
  if(phase.equals("CAST")){
   int poseTicks=switch(c.ability()){case "AIR_COMBO","LIGHTNING_SPIN"->36;case "UNIBEAM","BRING_ME_THANOS","INFINITE_MASS_PUNCH"->48;default->22;};
   CASTS.put(entity,new Cue(entity,c.hero,c.ability,c.phase,c.from,c.to,c.start,Math.min(c.expires,ticks+poseTicks),c.cast,c.radius));BetterCombatCompat.skillStarted(entity);
  }
  if(phase.equals("IMPACT")&&c.ability().equals("COCOON_SLAM"))EFFECTS.removeIf(old->old.cast()==c.cast()&&old.phase().equals("ZONE"));
  if(HeroEffectPolicy.retained(phase)){if(EFFECTS.size()>=HeroEffectPolicy.MAX_CUES)EFFECTS.removeFirst();EFFECTS.addLast(c);}
  if(phase.equals("IMPACT"))impact(c);
  HeroEffects.burst(c,ticks,quality());
 }
 static void tick(MinecraftClient client,long now){
  ticks=now;BetterCombatCompat.tick(now);if(!installed){WorldRenderEvents.AFTER_ENTITIES.register(HeroVisuals::render);installed=true;}
  STATES.values().removeIf(s->s.expires<=ticks);CASTS.values().removeIf(c->c.expires<=ticks);EFFECTS.removeIf(c->c.expires<=ticks);
  if(client.player==null||client.world==null)return;
  String id=FantasyHubClient.string("classId");boolean hero=Set.of("SPIDER_MAN","SORCERER_SUPREME","IRON_MAN","THOR","FLASH","BATMAN").contains(id);
  if(hero){int flags=0;if(client.currentScreen==null){if(client.options.jumpKey.isPressed())flags|=1;if(client.options.forwardKey.isPressed())flags|=2;if(client.options.sneakKey.isPressed())flags|=4;if(client.options.sprintKey.isPressed())flags|=8;}if(flags!=lastFlags||now%4==0){FantasyHubClient.send("hero_input","",flags);lastFlags=flags;}}
  if(!hero)lastFlags=-1;
  if(now%3==0&&density()>0)for(var entry:STATES.entrySet()){
   var e=client.world.getEntityById(entry.getKey());if(e==null||client.player.squaredDistanceTo(e)>1600)continue;
   var s=entry.getValue();if((s.flags&(1|8|16|32))!=0&&e.getVelocity().lengthSquared()>.005)for(int i=0;i<(quality()+1)*2;i++)client.world.addParticle(dust(s.hero),e.getX()+(client.world.random.nextDouble()-.5)*.6,e.getY()+client.world.random.nextDouble()*1.6,e.getZ()+(client.world.random.nextDouble()-.5)*.6,0,0,0);
  }
 }
 static int quality(){return FantasyHubClient.options==null?1:switch(FantasyHubClient.options.visualQuality){case "low"->0;case "high"->2;default->1;};}
 static double density(){return FantasyHubClient.options==null?1:FantasyHubClient.options.effectsDensity;}
 static int color(String hero){return switch(hero){case "SPIDER_MAN"->0xe9f2ff;case "SORCERER_SUPREME"->0xff922b;case "IRON_MAN"->0x83eaff;case "THOR"->0x80bbff;case "FLASH"->0xffc326;default->0x819bb5;};}
 private static DustParticleEffect dust(String hero){int color=color(hero);return new DustParticleEffect(new Vector3f(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f),1.1f);}
 private static void impact(Cue c){
  var client=MinecraftClient.getInstance();if(density()<=0||client.world==null||client.player==null||client.player.squaredDistanceTo(c.from)>1600)return;
  int n=quality()==0?10:quality()==1?24:40;for(int i=0;i<n;i++){double a=i*2*Math.PI/n;client.world.addParticle(dust(c.hero),c.from.x,c.from.y+.5,c.from.z,Math.cos(a)*.24,.04,Math.sin(a)*.24);}
  if(c.hero.equals("THOR"))client.world.addParticle(ParticleTypes.FLASH,c.from.x,c.from.y+.7,c.from.z,0,0,0);
 }
 private static void render(WorldRenderContext context){HeroEffects.render(context,EFFECTS,STATES,ticks,quality());}
 public static void animate(PlayerEntityModel<?> model,LivingEntity entity,float age){HeroAnimations.animate(model,entity,STATES.get(entity.getId()),cue(entity.getId()),ticks);}
}
