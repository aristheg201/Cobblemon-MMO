package vn.svframe.fantasyhub.core.hero;

import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.*;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.event.*;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.damage.*;
import vn.svframe.svframelib.entity.RpgEntityAdapters;
import vn.svframe.svframelib.player.PlayerMetadata;

/** The only skill damage gateway. Every projectile, DoT and recast keeps its activation budget. */
final class HeroCombat {
  private record Context(HeroRuntime.Cast cast,LivingEntity target) {}
  private record Mark(UUID owner,LivingEntity target,long until,double value) {}
  private static Context context;
  private record Reflection(HeroRuntime.Cast cast,net.minecraft.entity.projectile.ProjectileEntity projectile){}
  private static final Map<UUID,Reflection> REFLECTIONS=new HashMap<>();
  static void reflected(HeroRuntime.Cast c,net.minecraft.entity.projectile.ProjectileEntity p){if(REFLECTIONS.size()<128)REFLECTIONS.put(p.getUuid(),new Reflection(c,p));else p.discard();}
  static void tick(){REFLECTIONS.values().removeIf(r->{if(!r.cast.valid()){r.projectile.discard();return true;}return r.projectile.isRemoved();});MARKS.values().removeIf(m->m.until<=HeroRuntime.tick||!m.target.isAlive());}
  private static final Map<String,Mark> MARKS=new HashMap<>();
  static void install() {
    FinalDamageEvent.EVENT.register(HeroCombat::incoming);
    PlayerAttackEvent.EVENT.register(e->{
      if(HeroControl.stunned(e.getPlayer())){e.setCancelled(true);return;}
      var s=HeroRuntime.session(e.getPlayer());
      if(s!=null&&!e.isCancelled()&&HeroWorld.legal(s.player,e.getEntity())) {
        if(s.active("astral")||HeroControl.stunned(s.player)||context==null&&s.active("busy")){e.setCancelled(true);return;}
        if(context==null&&!e.getDamage().hasType(DamageType.SKILL))HeroPassives.normalAttack(s,e.getEntity(),e.getDamage());
      }
    });
    ServerLivingEntityEvents.AFTER_DAMAGE.register((victim,source,base,amount,blocked)->{
      var reflection=source.getSource()==null?null:REFLECTIONS.get(source.getSource().getUuid());
      if(amount>0&&reflection!=null&&targetIsPlayer(victim)){reflection.cast.budget.record(victim.getUuid(),amount);}
      if(amount>0&&reflection!=null&&!reflection.cast.award){reflection.cast.award=true;HeroPassives.skillConnected(reflection.cast);}
      if(amount>0&&source.getAttacker() instanceof ServerPlayerEntity p){var s=HeroRuntime.session(p);if(s!=null)HeroPassives.connected(s,victim,context==null);}
      if(victim instanceof ServerPlayerEntity p){var s=HeroRuntime.session(p);if(s!=null&&amount>0)HeroPassives.emergency(s);}
    });
  }
  static boolean hit(HeroRuntime.Cast c,LivingEntity target,HeroCatalog.Phase phase) {return hit(c,target,phase.kind(),phase.scale());}
  static boolean hit(HeroRuntime.Cast c,LivingEntity target,String kind,double coefficient) {
    if(!c.valid()||!HeroWorld.legal(c.owner.player,target)||coefficient<=0)return false;
    boolean pvp=target instanceof ServerPlayerEntity;
    double value=kind.equals("MAX_HEALTH")?target.getMaxHealth()*coefficient:c.attack*HeroBalance.coefficient(coefficient,c.total(),c.ability.ultimate(),pvp,HeroBalance.Rules.from(HeroRuntime.catalog()));
    if(kind.equals("MAX_HEALTH")&&pvp)value*=HeroRuntime.catalog().setting("pvp_multiplier");
    var s=c.owner;
    if(s.active("awakened"))value*=1+HeroRuntime.catalog().ability("THOR_GOD_OF_THUNDER").effect("damage_bonus");
    if(s.hero.id().equals("THOR"))value*=1+s.power.stacks("static",HeroRuntime.tick)*s.passive("static_lightning");
    var types=List.of(kind.contains("MAGIC")?DamageType.MAGIC:DamageType.PHYSICAL,DamageType.SKILL);
    var element=s.hero.id().equals("THOR")||s.hero.id().equals("FLASH")?SVFrameLib.inst().getElements().getOrNull("THUNDER"):null;
    var packet=new DamageMetadata(value,element,types);
    var attack=new AttackMetadata(packet,target,new PlayerMetadata(MMOPlayerData.setup(s.player).getStatMap(),EquipmentSlot.MAIN_HAND));
    DamageSource source=s.world.getDamageSources().playerAttack(s.player);
    if(kind.startsWith("TRUE"))source=new DamageSource(s.world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(RegistryKey.of(RegistryKeys.DAMAGE_TYPE,Identifier.of("fantasyhub","hero_true"))),s.player);
    var armor=target.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);var id=Identifier.of("fantasyhub","hero_pierce");
    double penetration=c.ability.effect("armor_penetration");
    if(armor!=null&&penetration>0)armor.addTemporaryModifier(new EntityAttributeModifier(id,-penetration,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    var old=context;context=new Context(c,target);
    boolean accepted;
    try {accepted=SVFrameLib.inst().getDamage().registerAttack(attack,false,true,source);}
    finally {context=old;if(armor!=null&&penetration>0)armor.removeModifier(id);}
    double applied=attack.getAppliedDamage();
    if(accepted&&applied>0) {
      HeroQa.hit(c,target,applied);
      if(pvp)c.budget.record(target.getUuid(),applied);
      HeroPresentation.cue(c,"HIT",target.getPos(),target.getPos(),.2);
      if(!c.award&&!c.proc){c.award=true;HeroPassives.skillConnected(c);}
      if(c.empowered&&!c.burstDone){c.burstDone=true;for(var enemy:HeroWorld.near(s.player,target.getPos(),3,c.cap()))hit(c,enemy,"MAGIC",s.passive("mystic_burst"));}
    }
    return accepted&&applied>0;
  }
  static float incoming(LivingEntity target,DamageSource source,float original) {
    double amount=original;
    var reflected=source.getSource()==null?null:REFLECTIONS.get(source.getSource().getUuid());
    Context active=context;
    if(active==null&&reflected!=null){if(!reflected.cast.valid()||!HeroWorld.legal(reflected.cast.owner.player,target))return 0;active=new Context(reflected.cast,target);if(targetIsPlayer(target))amount*=HeroRuntime.catalog().setting("pvp_multiplier");}
    MARKS.values().removeIf(m->m.until<=HeroRuntime.tick||!m.target.isAlive());
    double vulnerability=MARKS.values().stream().filter(m->m.target==target).mapToDouble(Mark::value).max().orElse(0);amount*=1+vulnerability;
    HeroRuntime.Session s=target instanceof ServerPlayerEntity p?HeroRuntime.session(p):null;
    if(s!=null) {
      boolean ordinary=ordinary(source,target),melee=ordinary&&source.getSource()==source.getAttacker();
      if(source.isIn(DamageTypeTags.IS_FALL)&&!s.hero.id().equals("FLASH"))return 0;
      if(s.active("astral")||s.active("phase")&&melee)return 0;
      var attacker=source.getAttacker() instanceof LivingEntity l?l:null;
      if(s.hero.id().equals("SPIDER_MAN")&&ordinary&&s.player.getRandom().nextDouble()<s.passive("evasion")){HeroPassives.instinct(s);return 0;}
      if(s.hero.id().equals("BATMAN")&&melee&&attacker!=null&&front(s.player,attacker,120)&&s.player.getRandom().nextDouble()<s.passive("parry")){HeroControl.apply(s,attacker,HeroControl.Kind.STUN,s.passive("parry_stun"));return 0;}
      if(s.hero.id().equals("FLASH")&&s.speedLevel>=s.passive("projectile_catch_level")&&source.isIn(DamageTypeTags.IS_PROJECTILE))return 0;
      if(s.active("web_shield")) {
        amount*=1-HeroRuntime.catalog().ability("SPIDER_MAN_WEB_SHIELD").effect("reduction");
        if(melee&&attacker!=null&&!s.active("web_countered")){s.flag("web_countered",2);HeroActions.effect(s.player,StatusEffects.INVISIBILITY,1,0);if(HeroWorld.behind(s.player,attacker,1.2))HeroControl.apply(s,attacker,HeroControl.Kind.ROOT,2);}
      }
      if(s.active("mandalas")&&attacker!=null&&front(s.player,attacker,120)) {
        amount*=1-HeroRuntime.catalog().ability("SORCERER_SUPREME_TAO_MANDALAS").effect("reduction");
        if(context==null||!context.cast.proc)HeroActions.proc(s,attacker,"TAO_MANDALAS","MAGIC",.8);
      }
      if(s.active("dash_shield"))amount*=1-HeroRuntime.catalog().ability("IRON_MAN_THRUSTER_DASH").effect("reduction");
      if(s.active("drones"))amount*=1-HeroRuntime.catalog().ability("IRON_MAN_HOUSE_PARTY").effect("reduction");
      if(s.hero.id().equals("THOR")&&melee&&attacker!=null&&(context==null||!context.cast.proc))HeroActions.proc(s,attacker,"MJOLNIR_THROW","PHYSICAL",s.passive("static_counter"));
    }
    amount=HeroBarriers.absorb(target.getUuid(),amount);
    if(active!=null&&active.target==target&&target instanceof ServerPlayerEntity)amount=active.cast.budget.allowed(target.getUuid(),target.getMaxHealth(),amount);
    return (float)Math.max(0,amount);
  }
  private static boolean targetIsPlayer(LivingEntity t){return t instanceof ServerPlayerEntity;}
  static boolean ordinary(DamageSource source,LivingEntity target) {
    var attack=SVFrameLib.inst().getDamage().getRegisteredAttackMetadata(target);
    return attack==null||!attack.getDamage().hasType(DamageType.SKILL);
  }
  static boolean front(LivingEntity self,LivingEntity source,double cone){return HeroWorld.forward(self).dotProduct(source.getPos().subtract(self.getPos()).multiply(1,0,1).normalize())>=Math.cos(Math.toRadians(cone/2));}
  static void mark(HeroRuntime.Session s,LivingEntity t,double seconds,double value){MARKS.put(s.player.getUuid()+":"+t.getUuid(),new Mark(s.player.getUuid(),t,HeroRuntime.tick+(int)(seconds*20),value));}
  static void shields(LivingEntity t){HeroBarriers.destroy(t.getUuid());t.setAbsorptionAmount(0);}
}
