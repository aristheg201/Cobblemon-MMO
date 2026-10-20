package vn.svframe.fantasyhub.core.hero;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.effect.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;
import vn.svframe.svframelib.damage.*;
import vn.svframe.svframelib.player.modifier.*;
import vn.svframe.svframelib.player.skillmod.SkillModifier;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerResourceUpdateEvent;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;

/** Runtime-only passive leases; native MMO owns all resources and persistence. */
final class HeroPassives {
  static void install() {
    PlayerResourceUpdateEvent.EVENT.register(e->{
      if(e.getUpdateReason()!=ResourceUpdateReason.REGENERATION||e.getPlayer()==null)return;
      var s=HeroRuntime.session(e.getPlayer());if(s==null)return;
      if(e.getResource()==PlayerResource.HEALTH&&s.hero.id().equals("FLASH"))e.setNewAmount(e.getOldAmount()+e.getDifference()*s.passive("health_regen_multiplier"));
      else if(e.getResource().name().equals(s.hero.resource())) {
        double desired=e.getData().isInCombat()?s.hero.regenCombat():s.hero.regenIdle();
        if(s.hero.id().equals("IRON_MAN")&&(s.active("flight")||s.active("busy")))desired=0;
        if(s.hero.id().equals("FLASH")&&s.active("super_speed"))desired=0;
        e.setNewAmount(e.getOldAmount()+e.getDifference()*(s.hero.regenIdle()==0?0:desired/s.hero.regenIdle()));
      }
    });
  }
  static void input(HeroRuntime.Session s,int flags) {
    boolean jump=(flags&1)!=0;
    if(jump&&!s.jumping) {
      boolean pair=!s.player.isOnGround()&&HeroRuntime.tick-s.lastJump<=HeroRuntime.flightRules().doubleTapTicks()&&!HeroControl.mobilityLocked(s.player);
      if(pair){
        if(s.active("flight"))flight(s,false);
        else if(s.hero.id().equals("IRON_MAN")||HeroFlight.eligible(s))flight(s,true);
        else if(HeroRuntime.flightRules().rule(s.hero.id())!=null)s.player.sendMessage(net.minecraft.text.Text.translatable("fantasyhub.flight.requirements."+s.hero.id().toLowerCase(Locale.ROOT)),true);
      }
      s.lastJump=pair?-100:HeroRuntime.tick;
    }s.jumping=jump;
  }
  static void castStarted(HeroRuntime.Cast c) {
    var s=c.owner;
    if(s.hero.id().equals("IRON_MAN")&&!s.active("overdrive")) {
      s.heat+=(int)s.passive("heat_per_skill");if(s.heat>=s.passive("heat_max")){s.heat=0;s.flag("overdrive",s.passive("overdrive_duration"));}
    }
    if(c.empowered){s.power.take("mystic",HeroRuntime.tick);refresh(s);
      if(c.ability.phases().isEmpty()){c.burstDone=true;c.later(.1,()->{for(var t:HeroWorld.near(s.player,s.player.getPos(),3,c.cap()))HeroCombat.hit(c,t,"MAGIC",s.passive("mystic_burst"));});}
    }
  }
  static void skillConnected(HeroRuntime.Cast c) {
    if(c.owner.hero.id().equals("SORCERER_SUPREME")&&!c.empowered){var s=c.owner;s.power.gain("mystic",(int)s.passive("mystic_max"),0,HeroRuntime.tick);refresh(s);}
  }
  static void instinct(HeroRuntime.Session s){s.power.gain("instinct",(int)s.passive("instinct_max"),(int)(s.passive("instinct_duration")*20),HeroRuntime.tick);refresh(s);}
  static void dashed(HeroRuntime.Session s){if(s.hero.id().equals("SPIDER_MAN"))instinct(s);}
  static void normalAttack(HeroRuntime.Session s,LivingEntity target,DamageMetadata damage) {
    if(s.hero.id().equals("FLASH"))damage.multiplicativeModifier(1+s.speedLevel*s.passive("punch_per_level"));
    if(s.active("awakened"))damage.multiplicativeModifier(1+HeroRuntime.catalog().ability("THOR_GOD_OF_THUNDER").effect("damage_bonus"));
    if(s.hero.id().equals("IRON_MAN")&&s.active("overdrive")){damage.multiplicativeModifier(s.passive("repulsor_attack"));}
    if(s.hero.id().equals("BATMAN")&&s.active("stealth_crit")){
      // Native combat may already have applied a random crit. Normalize it before guaranteed 250%.
      if(damage.isWeaponCriticalStrike())damage.multiplicativeModifier(1/Math.max(1,MMOPlayerData.setup(s.player).getStatMap().getStat("CRITICAL_STRIKE_POWER")/100));
      damage.multiplicativeModifier(HeroRuntime.catalog().ability("BATMAN_SMOKE_TAKEDOWN").effect("critical_scale"));damage.registerWeaponCriticalStrike();s.power.unset("stealth_crit");
    }
  }
  static void connected(HeroRuntime.Session s,LivingEntity target,boolean ordinary) {
    if(!ordinary)return;
    if(s.hero.id().equals("BATMAN")){s.power.gain("combo",(int)s.passive("combo_max"),(int)(s.passive("combo_expiry")*20),HeroRuntime.tick);refresh(s);}
    if(s.hero.id().equals("THOR")&&HeroRuntime.tick-s.lastHit>=s.passive("force_hit_cooldown")*20) {
      s.lastHit=HeroRuntime.tick;s.power.gain("static",(int)s.passive("static_max"),100,HeroRuntime.tick);gain(s,s.passive("force_per_hit"));
      if(s.active("awakened"))for(var other:HeroWorld.near(s.player,target.getPos(),4,4))if(other!=target)HeroActions.proc(s,other,"GOD_OF_THUNDER","PHYSICAL",.5);
      refresh(s);
    }
    if(s.hero.id().equals("IRON_MAN")&&s.active("overdrive"))for(var other:HeroWorld.near(s.player,target.getPos(),s.passive("repulsor_radius"),4))if(other!=target)HeroActions.proc(s,other,"REPULSOR_BLAST","PHYSICAL",s.passive("repulsor_attack"));
  }
  static void emergency(HeroRuntime.Session s) {
    if(!s.player.isAlive()||HeroRuntime.tick<s.emergencyReady)return;
    double hp=s.player.getHealth()/s.player.getMaxHealth();
    if(s.hero.id().equals("SORCERER_SUPREME")&&hp<s.passive("emergency_threshold")) {
      s.emergencyReady=HeroRuntime.tick+(long)(s.passive("emergency_cooldown")*20);
      HeroBarriers.add(s.player.getUuid(),s.player.getUuid(),"cloak_emergency",s.player.getMaxHealth(),s.passive("emergency_shield"),HeroRuntime.tick+(long)(s.passive("emergency_duration")*20));
      for(var t:HeroWorld.near(s.player,s.player.getPos(),4,12))HeroWorld.push(s.player,t,s.player.getPos(),s.passive("emergency_knockback"),.2);
    } else if(s.hero.id().equals("BATMAN")&&hp<s.passive("escape_threshold")) {
      s.emergencyReady=HeroRuntime.tick+(long)(s.passive("escape_cooldown")*20);s.flag("escape",s.passive("escape_duration"));HeroActions.effect(s.player,StatusEffects.INVISIBILITY,s.passive("escape_duration"),0);refresh(s);
    }
  }
  static void tick(HeroRuntime.Session s) {
    var p=s.player;long tick=HeroRuntime.tick;
    if(tick-s.lastInput>10){s.inputFlags=0;s.jumping=false;}
    double moved=p.getPos().subtract(s.lastPosition).multiply(1,0,1).length();s.lastPosition=p.getPos();
    if(p.isOnGround()||HeroWorld.safe(p,p.getPos(),true))s.lastSafe=p.getPos();
    if(HeroControl.mobilityLocked(p)){if(s.active("flight"))flight(s,false);}
    HeroFlight.tick(s);
    if(s.hero.id().equals("FLASH")) {
      if(moved>.025&&moved<.7){s.movementTicks++;s.standingTicks=0;}else if(++s.standingTicks>40)s.movementTicks=Math.max(0,s.movementTicks-2);
      s.speedLevel=Math.min((int)s.passive("speed_max"),(int)(s.movementTicks/(s.passive("speed_level_seconds")*20)));
      if(tick%20==0&&moved>.025)gain(s,s.passive("moving_gain"));
      if(s.active("super_speed")&&tick%20==0&&!spend(s,HeroRuntime.catalog().ability("FLASH_SUPER_SPEED").upkeep()))s.power.unset("super_speed");
      if(s.active("super_speed"))HeroActions.effect(p,StatusEffects.JUMP_BOOST,.3,2);
      if(s.active("phase")) {
        if(!HeroWorld.safe(p,p.getPos(),false)||HeroControl.mobilityLocked(p)){s.power.unset("phase");}
        else {p.noClip=true;if((s.inputFlags&2)!=0){var desired=p.getPos().add(p.getRotationVec(1).multiply(.4));if(HeroWorld.safe(p,desired,false))p.networkHandler.requestTeleport(desired.x,desired.y,desired.z,p.getYaw(),p.getPitch());}}
      }
      if(!s.active("phase")&&p.noClip!=s.priorNoClip){p.noClip=s.priorNoClip;if(!HeroWorld.safe(p,p.getPos(),true))HeroWorld.teleport(p,s.lastSafe);}
      if(s.speedLevel>=s.passive("projectile_catch_level")&&tick%2==0) {
        var at=p.getPos();for(var projectile:s.world.getEntitiesByClass(net.minecraft.entity.projectile.ProjectileEntity.class,new Box(at,at).expand(s.passive("projectile_catch_radius")),e->e.getOwner()!=p).stream().limit(24).toList()) {
          if(projectile.getOwner() instanceof LivingEntity owner&&!HeroWorld.legal(p,owner))continue;
          if(projectile.getVelocity().dotProduct(p.getEyePos().subtract(projectile.getPos()))>0)projectile.discard();
        }
      }
    }
    if(s.hero.id().equals("IRON_MAN")&&s.active("flight")&&tick%20==0&&!spend(s,s.passive("flight_upkeep")))flight(s,false);
    if(s.active("astral")){p.fallDistance=0;}else if(s.returnAnchor!=null){
      HeroWorld.teleport(p,s.returnAnchor);s.returnAnchor=null;if(s.active("flight"))HeroFlight.claim(s,HeroFlight.speed(s));else HeroFlight.release(s);gain(s,s.hero.maximum()*HeroRuntime.catalog().ability("SORCERER_SUPREME_ASTRAL_PROJECTION").effect("mana_refund"));
    }
    if(s.active("flight")||s.active("astral")){p.fallDistance=0;HeroFlight.claim(s,s.active("astral")?.12f:HeroFlight.speed(s));}
    boolean input=(s.inputFlags&1)!=0,forward=(s.inputFlags&2)!=0,sneak=(s.inputFlags&4)!=0;
    if(s.hero.id().equals("SPIDER_MAN")&&forward&&!p.isOnGround()&&!HeroControl.mobilityLocked(p)) {
      var box=p.getBoundingBox().expand(s.passive("wall_range"),0,s.passive("wall_range"));
      if(!s.world.isSpaceEmpty(p,box)){HeroWorld.velocity(p,new Vec3d(p.getVelocity().x,s.passive("wall_rise"),p.getVelocity().z));p.fallDistance=0;}
    }
    if(!p.isOnGround()&&!HeroControl.mobilityLocked(p)) {
      if(s.hero.id().equals("SORCERER_SUPREME")&&sneak&&!s.active("flight")&&!s.active("astral")&&HeroFlight.eligible(s)||s.hero.id().equals("BATMAN")&&input){Vec3d facing=HeroWorld.forward(p);HeroWorld.velocity(p,facing.multiply(s.hero.id().equals("BATMAN")?.45:.12).add(0,-.06,0));p.fallDistance=0;}
    }
    if(tick%4==0)refresh(s);
  }
  static void refresh(HeroRuntime.Session s) {
    long tick=HeroRuntime.tick;double speed=0,attackSpeed=0,crit=0,critPower=0,armor=0,cdr=0;
    switch(s.hero.id()) {
      case "SPIDER_MAN" -> {int n=s.power.stacks("instinct",tick);speed=n*s.passive("instinct_speed");crit=n*s.passive("instinct_critical")*100;}
      case "IRON_MAN" -> {if(s.active("overdrive")){speed=s.passive("overdrive_speed");cdr=s.passive("overdrive_cooldown_reduction")*100;}}
      case "THOR" -> {armor=s.power.stacks("static",tick)*s.passive("static_armor");if(s.active("spin"))speed=HeroRuntime.catalog().ability("THOR_LIGHTNING_SPIN").effect("speed");if(s.active("awakened"))attackSpeed=HeroRuntime.catalog().ability("THOR_GOD_OF_THUNDER").effect("attack_speed");}
      case "FLASH" -> {speed=s.speedLevel*s.passive("speed_per_level")+(s.active("super_speed")?HeroRuntime.catalog().ability("FLASH_SUPER_SPEED").effect("speed"):0)+(s.active("siphon")?HeroRuntime.catalog().ability("FLASH_SPEED_SIPHON").effect("speed_steal"):0);}
      case "BATMAN" -> {int n=s.power.stacks("combo",tick);attackSpeed=n*s.passive("combo_attack_speed");critPower=n*s.passive("combo_critical_damage")*100;if(s.active("escape"))speed=s.passive("escape_speed");}
    }
    attribute(s.player,EntityAttributes.GENERIC_MOVEMENT_SPEED,"speed",speed);attribute(s.player,EntityAttributes.GENERIC_ATTACK_SPEED,"attack_speed",attackSpeed);attribute(s.player,EntityAttributes.GENERIC_ARMOR,"armor",armor);
    stat(s,"critical","CRITICAL_STRIKE_CHANCE",crit);stat(s,"critical_power","CRITICAL_STRIKE_POWER",critPower);stat(s,"cdr","COOLDOWN_REDUCTION",cdr);
    String cooldown=s.hero.id().equals("SORCERER_SUPREME")&&s.power.stacks("mystic",tick)>=s.passive("mystic_max")?"mystic":s.active("awakened")?"awakened":"";
    if(!cooldown.equals(s.cooldownSignature)) {
      if(s.skillCooldown!=null)s.skillCooldown.unregister(MMOPlayerData.setup(s.player));s.skillCooldown=null;s.cooldownSignature=cooldown;
      if(!cooldown.isEmpty()) {
        var abilities=cooldown.equals("mystic")?s.hero.abilities():s.hero.abilities().stream().filter(a->a.key().equals("MJOLNIR_THROW")||a.key().equals("THUNDEROUS_SLAM")).toList();
        java.util.List<vn.svframe.svframelib.skill.handler.SkillHandler<?>> handlers=new java.util.ArrayList<>();for(var a:abilities)handlers.add(SVFrameLib.inst().getSkills().getHandler(a.id()));
        s.skillCooldown=new SkillModifier("fantasyhub_hero_cooldown","cooldown",handlers,-100*(cooldown.equals("mystic")?s.passive("mystic_cooldown_reduction"):HeroRuntime.catalog().ability("THOR_GOD_OF_THUNDER").effect("throw_cooldown_reduction")),ModifierType.RELATIVE);
        s.skillCooldown.register(MMOPlayerData.setup(s.player));
      }
    }
  }
  private static void stat(HeroRuntime.Session s,String key,String stat,double value) {
    if(s.appliedStats.getOrDefault(key,0d)==value)return;
    var previous=s.stats.remove(key);if(previous!=null)previous.unregister(MMOPlayerData.setup(s.player));s.appliedStats.put(key,value);
    if(value!=0){var m=new StatModifier(UUID.nameUUIDFromBytes((s.player.getUuid()+":hero:"+key).getBytes(StandardCharsets.UTF_8)),"fantasyhub_hero_"+key,stat,value,ModifierType.FLAT,EquipmentSlot.OTHER,ModifierSource.OTHER);m.register(MMOPlayerData.setup(s.player));s.stats.put(key,m);}
  }
  private static void attribute(LivingEntity p,RegistryEntry<EntityAttribute> attr,String key,double value) {
    var instance=p.getAttributeInstance(attr);if(instance==null)return;var id=Identifier.of("fantasyhub","hero_"+key);var current=instance.getModifier(id);
    if(current!=null&&current.value()==value||current==null&&value==0)return;
    instance.removeModifier(id);if(value!=0)instance.addTemporaryModifier(new EntityAttributeModifier(id,value,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
  }
  static void flight(HeroRuntime.Session s,boolean on) {
    boolean changed=s.active("flight")!=on;
    if(on){if(!s.hero.id().equals("IRON_MAN")&&!HeroFlight.eligible(s))return;s.flag("flight",0);HeroFlight.claim(s,HeroFlight.speed(s));}
    else {s.power.unset("flight");if(!s.active("astral"))HeroFlight.release(s);}
    if(changed)s.player.sendMessage(net.minecraft.text.Text.translatable("fantasyhub.flight."+(on?"enabled":"disabled")),true);
  }
  static void restoreMovement(HeroRuntime.Session s){
    if(s.returnAnchor!=null&&s.world==s.player.getWorld())HeroWorld.teleport(s.player,s.returnAnchor);
    if(s.player.noClip&&!s.priorNoClip&&!HeroWorld.safe(s.player,s.player.getPos(),true))HeroWorld.teleport(s.player,s.lastSafe);
    s.player.noClip=s.priorNoClip;HeroFlight.release(s);
    attribute(s.player,EntityAttributes.GENERIC_MOVEMENT_SPEED,"speed",0);attribute(s.player,EntityAttributes.GENERIC_ATTACK_SPEED,"attack_speed",0);attribute(s.player,EntityAttributes.GENERIC_ARMOR,"armor",0);
  }
  static boolean spend(HeroRuntime.Session s,double amount) {
    if(!Double.isFinite(amount)||amount<0)return false;
    // Native setResource returns false for a no-op; free upkeep is still paid.
    if(amount==0)return true;
    var data=SVFrameMMO.playerData().get(s.player);var resource=PlayerResource.valueOf(s.hero.resource());if(resource.getCurrent(data)<amount)return false;return resource.give(data,-amount,ResourceUpdateReason.SKILL);
  }
  static void gain(HeroRuntime.Session s,double amount){PlayerResource.valueOf(s.hero.resource()).give(SVFrameMMO.playerData().get(s.player),amount,ResourceUpdateReason.SKILL);}
}
