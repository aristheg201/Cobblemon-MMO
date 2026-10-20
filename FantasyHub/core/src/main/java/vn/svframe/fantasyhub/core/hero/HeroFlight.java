package vn.svframe.fantasyhub.core.hero;

import java.util.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import vn.svframe.fantasyhub.protocol.BackEquipment;
import vn.svframe.svframeitems.item.ItemCodec;

/** Uses native flight abilities and existing class sessions, never a second movement engine. */
final class HeroFlight {
  static Map<String,String> armor(HeroRuntime.Session s){
    for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){
      var stack=s.player.getEquippedStack(slot);var component=stack.isEmpty()?null:stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
      if(!s.armorComponents.containsKey(slot)||s.armorComponents.get(slot)!=component){
        s.armorComponents.put(slot,component);s.armorIds.put(slot.name(),ItemCodec.read(stack).map(i->i.definitionId()).orElse(""));
      }
    }
    return s.armorIds;
  }
  static String backItem(HeroRuntime.Session s){
    var rule=HeroRuntime.flightRules().rule(s.hero.id());if(rule==null)return "";
    var id=Identifier.of(rule.capeItem());if(!Registries.ITEM.containsId(id))return "";
    var cape=BackEquipment.equipped(s.player,Registries.ITEM.get(id));return cape.isEmpty()?"":Registries.ITEM.getId(cape.getItem()).toString();
  }
  static boolean eligible(HeroRuntime.Session s){return HeroRuntime.flightRules().allows(s.hero.id(),backItem(s),armor(s));}
  static float speed(HeroRuntime.Session s){var r=HeroRuntime.flightRules().rule(s.hero.id());return r==null?.08f:r.speed();}
  static void tick(HeroRuntime.Session s){
    var rule=HeroRuntime.flightRules().rule(s.hero.id());
    if(rule!=null&&s.active("flight")&&(!eligible(s)||HeroRuntime.tick%20==0&&!HeroPassives.spend(s,rule.upkeepPerSecond())))HeroPassives.flight(s,false);
    if(s.active("flight")&&!s.player.getAbilities().flying&&s.player.isOnGround())HeroPassives.flight(s,false);
  }
  private static HeroFlightLease.Abilities state(ServerPlayerEntity p){var a=p.getAbilities();return new HeroFlightLease.Abilities(a.allowFlying,a.flying,a.getFlySpeed());}
  static void claim(HeroRuntime.Session s,float speed){
    var p=s.player;if(p.isCreative()||p.isSpectator())return;
    boolean initial=!s.flightLease.owned();s.flightLease.acquire(state(p));var a=p.getAbilities();
    boolean changed=!a.allowFlying||initial||a.getFlySpeed()!=speed;
    a.allowFlying=true;if(initial)a.flying=true;a.setFlySpeed(speed);if(changed)p.sendAbilitiesUpdate();
  }
  static void release(HeroRuntime.Session s){
    var p=s.player;boolean owned=s.flightLease.owned();var before=state(p);var restore=s.flightLease.release(before,p.isCreative()||p.isSpectator());
    if(!owned)return;
    var a=p.getAbilities();a.allowFlying=restore.allowed();a.flying=restore.flying();a.setFlySpeed(restore.speed());if(p.isAlive()&&!p.isRemoved())p.sendAbilitiesUpdate();
    p.fallDistance=0;
    int grace=HeroRuntime.flightRules().landingGraceTicks();
    if(before.flying()&&!restore.flying()&&p.isAlive()&&!p.isOnGround()&&grace>0)p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING,grace,0,false,false,true));
  }
  static void saveBaseline(HeroRuntime.Session s,NbtCompound nbt){
    if(!s.flightLease.owned()||s.player.isCreative()||s.player.isSpectator())return;var saved=s.flightLease.saved(state(s.player));var a=nbt.getCompound("abilities");
    a.putBoolean("mayfly",saved.allowed());a.putBoolean("flying",saved.flying());a.putFloat("flySpeed",saved.speed());
    nbt.put("abilities",a);
  }
  static void restoreForRespawn(HeroRuntime.Session s,ServerPlayerEntity player){
    if(!s.flightLease.owned()||player.isCreative()||player.isSpectator())return;
    var baseline=s.flightLease.saved(state(s.player));var a=player.getAbilities();a.allowFlying=baseline.allowed();a.flying=baseline.flying();a.setFlySpeed(baseline.speed());player.sendAbilitiesUpdate();
  }
}
