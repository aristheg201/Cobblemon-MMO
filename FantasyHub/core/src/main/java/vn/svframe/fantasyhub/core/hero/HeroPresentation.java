package vn.svframe.fantasyhub.core.hero;

import net.minecraft.particle.*;
import com.google.gson.*;
import net.fabricmc.fabric.api.networking.v1.*;
import vn.svframe.fantasyhub.protocol.Protocol;
import net.minecraft.sound.*;
import net.minecraft.util.math.Vec3d;

/** Server-validated effect cues. Client actor/animation rendering consumes these same phases. */
final class HeroPresentation {
  static ParticleEffect particle(HeroRuntime.Session s) {
    return switch(s.hero.id()) {
      case "SPIDER_MAN" -> ParticleTypes.END_ROD;
      case "SORCERER_SUPREME" -> ParticleTypes.ENCHANT;
      case "IRON_MAN" -> ParticleTypes.ELECTRIC_SPARK;
      case "THOR","FLASH" -> ParticleTypes.ELECTRIC_SPARK;
      default -> ParticleTypes.SMOKE;
    };
  }
  static void cue(HeroRuntime.Cast c,String phase,Vec3d from,Vec3d to,double duration) {
    if(!c.valid())return;
    if(!phase.equals("HIT")||c.once("hit-cue:"+(HeroRuntime.tick/4),c.owner.player.getUuid())) {
      var data=base(c.owner);data.addProperty("kind","cue");data.addProperty("ability",c.ability.key());data.addProperty("phase",phase);data.addProperty("duration",duration);data.addProperty("cast",c.id);
      data.add("from",point(from));data.add("to",point(to));send(c.owner,data);
    }
    c.owner.world.spawnParticles(particle(c.owner),from.x,from.y+1,from.z,phase.equals("IMPACT")?14:5,.25,.35,.25,.025);
    if(phase.equals("CAST")||phase.equals("IMPACT")) {
      SoundEvent sound=switch(c.owner.hero.id()) {
        case "SPIDER_MAN" -> SoundEvents.ENTITY_SNOWBALL_THROW;
        case "SORCERER_SUPREME" -> SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE;
        case "IRON_MAN" -> SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH;
        case "THOR" -> SoundEvents.ITEM_TRIDENT_THUNDER.value();
        case "FLASH" -> SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP;
        default -> SoundEvents.ENTITY_BAT_TAKEOFF;
      };
      c.owner.world.playSound(null,from.x,from.y,from.z,sound,SoundCategory.PLAYERS,.55f,phase.equals("IMPACT")?.8f:1.2f);
    }
  }
  static void trail(HeroRuntime.Cast c,Vec3d from,Vec3d to) {
    int count=Math.min(18,Math.max(1,(int)(from.distanceTo(to)*3)));
    for(int i=0;i<count;i++){var p=from.lerp(to,i/(double)count);c.owner.world.spawnParticles(particle(c.owner),p.x,p.y,p.z,1,0,0,0,0);}
  }
  static void ring(HeroRuntime.Cast c,Vec3d center,double radius) {
    for(int i=0;i<24;i++){double a=i*Math.PI/12;var p=center.add(Math.cos(a)*radius,.12,Math.sin(a)*radius);c.owner.world.spawnParticles(particle(c.owner),p.x,p.y,p.z,1,0,0,0,0);}
  }
  private static JsonArray point(Vec3d p){var a=new JsonArray();a.add(p.x);a.add(p.y);a.add(p.z);return a;}
  private static JsonObject base(HeroRuntime.Session s){var d=new JsonObject();d.addProperty("entity",s.player.getId());d.addProperty("hero",s.hero.id());return d;}
  private static void send(HeroRuntime.Session s,JsonObject data) {
    var packet=new Protocol.Hero(data.toString());var players=new java.util.HashSet<>(PlayerLookup.tracking(s.player));players.add(s.player);
    for(var p:players)if(p.squaredDistanceTo(s.player)<4096&&ServerPlayNetworking.canSend(p,Protocol.Hero.ID))ServerPlayNetworking.send(p,packet);
  }
  static void publish(HeroRuntime.Session s) {
    if(HeroRuntime.tick%10!=0)return;var data=base(s);data.addProperty("kind","state");int flags=0;
    String[] keys={"flight","astral","phase","super_speed","overdrive","awakened","web_shield","mandalas","spin","drones","busy"};
    for(int i=0;i<keys.length;i++)if(s.active(keys[i]))flags|=1<<i;
    data.addProperty("flags",flags);data.addProperty("speed",s.speedLevel);data.addProperty("heat",s.heat);data.addProperty("stacks",s.power.stacks(s.hero.id().equals("SPIDER_MAN")?"instinct":s.hero.id().equals("BATMAN")?"combo":s.hero.id().equals("THOR")?"static":"mystic",HeroRuntime.tick));send(s,data);
  }
  static void remove(HeroRuntime.Session s) {var data=base(s);data.addProperty("kind","remove");send(s,data);}

}
