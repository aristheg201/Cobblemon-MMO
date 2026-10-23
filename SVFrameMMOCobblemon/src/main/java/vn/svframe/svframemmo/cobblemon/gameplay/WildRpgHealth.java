package vn.svframe.svframemmo.cobblemon.gameplay;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;import com.cobblemon.mod.common.api.pokemon.stats.Stats;import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.minecraft.entity.attribute.*;import net.minecraft.entity.damage.DamageSource;import net.minecraft.server.network.ServerPlayerEntity;import net.minecraft.component.DataComponentTypes;import net.minecraft.util.Identifier;import net.fabricmc.fabric.api.event.lifecycle.v1.*;import java.util.*;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
/** Only a temporary living-entity modifier. Never writes Pokemon stats or Pokemon currentHealth. */
public final class WildRpgHealth {
 private static final Identifier MODIFIER=Identifier.of("svframemmo_cobblemon","rpg_wild_hp");private static final Map<PokemonEntity,State> STATES=new WeakHashMap<>();
 private static final class State {long expires;float hp;boolean suspended;}
 public static boolean active(PokemonEntity p){var s=STATES.get(p);return s!=null&&!s.suspended;}
 private static boolean eligible(PokemonEntity e){var p=e.getPokemon();return !e.getWorld().isClient()&&p.getOwnerUUID()==null&&!p.isBattleClone()&&!p.isNPCOwned()&&!e.getCommandTags().contains("svframe_worldboss")&&!e.getCommandTags().contains("svframe_cosmic_minion");}
 public static void beforeDamage(PokemonEntity e,DamageSource source){if(!eligible(e)||!e.isAlive()||e.isBattling()||e.isBusy()||e.getBeamMode()!=0)return;var cfg=SVFrameMMOCobblemon.config().gameplay.rpgHealth;if(!cfg.enabled)return;var actor=source.getAttacker();if(!(actor instanceof ServerPlayerEntity p))return;
  var registered=vn.svframe.svframelib.SVFrameLib.inst().getDamage().getRegisteredAttackMetadata(e);var custom=p.getMainHandStack().get(DataComponentTypes.CUSTOM_DATA);boolean weapon=custom!=null&&custom.copyNbt().contains("SVFrameItems");boolean skill=registered!=null&&registered.getDamage().hasAnyType(List.of(vn.svframe.svframelib.damage.DamageType.SKILL));if(!weapon&&!skill)return;
  var s=STATES.computeIfAbsent(e,key->{var n=new State();n.hp=e.getHealth()/e.getMaxHealth();return n;});s.expires=e.getWorld().getTime()+cfg.combatTimeoutTicks;if(s.suspended||!e.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).hasModifier(MODIFIER))apply(e,s);
 }
 private static void apply(PokemonEntity e,State s){var cfg=SVFrameMMOCobblemon.config().gameplay.rpgHealth;var a=e.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);a.removeModifier(MODIFIER);double base=e.getPokemon().getForm().getBaseStats().getOrDefault(Stats.HP,50);double max=RpgHealthRules.maximum(base,e.getPokemon().getLevel(),e.getPokemon().isAlpha(),cfg.alphaMultiplier,cfg.cap);a.addTemporaryModifier(new EntityAttributeModifier(MODIFIER,max-a.getValue(),EntityAttributeModifier.Operation.ADD_VALUE));s.suspended=false;e.setHealth(Math.max(1,(float)(e.getMaxHealth()*s.hp)));}
 public static void suspend(PokemonEntity e){var s=STATES.get(e);if(s==null||s.suspended)return;s.hp=e.getMaxHealth()>0?e.getHealth()/e.getMaxHealth():1;s.suspended=true;e.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).removeModifier(MODIFIER);if(e.isAlive())e.setHealth(Math.min(e.getMaxHealth(),e.getMaxHealth()*e.getPokemon().getCurrentHealth()/Math.max(1f,e.getPokemon().getMaxHealth())));}
 public static void install(){
  CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event->{for(var actor:event.getBattle().getActors())for(var battler:actor.getPokemonList()){var e=battler.getOriginalPokemon().getEntity();if(e!=null)suspend(e);}});
  ServerTickEvents.END_SERVER_TICK.register(server->{for(var entry:List.copyOf(STATES.entrySet())){var e=entry.getKey();var s=entry.getValue();if(!eligible(e)||e.isRemoved()||e.getWorld().getTime()>=s.expires){suspend(e);STATES.remove(e);continue;}if(e.isBattling()||e.isBusy()||e.getBeamMode()!=0)suspend(e);else if(s.suspended&&e.isAlive())apply(e,s);}});
  ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{if(entity instanceof PokemonEntity e){suspend(e);STATES.remove(e);}});ServerLifecycleEvents.SERVER_STOPPED.register(server->STATES.clear());
 }
}
