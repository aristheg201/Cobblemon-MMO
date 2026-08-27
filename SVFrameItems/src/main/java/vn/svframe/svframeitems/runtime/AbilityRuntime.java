package vn.svframe.svframeitems.runtime;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;
import vn.svframe.svframeitems.item.ItemCodec;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Item trigger adapter. Skill cooldown/resource semantics remain owned by SVFrameLib. */
public final class AbilityRuntime {
    public record ResolvedAbility(String skill, Map<String,Object> parameters) { public ResolvedAbility { skill=Objects.requireNonNull(skill);parameters=Map.copyOf(parameters); } }
    private final SVFrameItemsRegistry registry;
    public AbilityRuntime(SVFrameItemsRegistry registry){this.registry=Objects.requireNonNull(registry);}
    public void initialize(){
        AttackEntityCallback.EVENT.register((player,world,hand,entity,hit)->{if(!world.isClient()&&player instanceof ServerPlayerEntity server)trigger(server,server.getStackInHand(hand),ItemAbility.Trigger.ATTACK,entity);return ActionResult.PASS;});
        UseItemCallback.EVENT.register((player,world,hand)->{if(!world.isClient()&&player instanceof ServerPlayerEntity server)trigger(server,server.getStackInHand(hand),ItemAbility.Trigger.USE,null);return TypedActionResult.pass(player.getStackInHand(hand));});
    }
    public int triggerEquip(ServerPlayerEntity player,ItemStack stack){return trigger(player,stack,ItemAbility.Trigger.EQUIP,null);}
    public int triggerUnequip(ServerPlayerEntity player,ItemStack stack){return trigger(player,stack,ItemAbility.Trigger.UNEQUIP,null);}
    public int trigger(ServerPlayerEntity player,ItemStack stack,ItemAbility.Trigger trigger,Entity target){return trigger(player,stack,trigger,target,ThreadLocalRandom.current());}
    public int trigger(ServerPlayerEntity player,ItemStack stack,ItemAbility.Trigger trigger,Entity target,RandomGenerator random){
        Objects.requireNonNull(player);Objects.requireNonNull(trigger);Objects.requireNonNull(random);
        Optional<ItemInstance> read=ItemCodec.read(stack);if(read.isEmpty())return 0;ItemInstance instance=read.get();ItemDefinition definition=registry.item(instance.definitionId());if(definition==null)return 0;
        int casts=0;
        for(ResolvedAbility ability:resolve(instance,definition,trigger,random))if(SVFrameLibFabricMod.castSkill(ability.skill(),player.getUuid(),target==null?player.getUuid():target.getUuid(),ability.parameters()))casts++;
        return casts;
    }
    public static List<ResolvedAbility> resolve(ItemInstance instance,ItemDefinition definition,ItemAbility.Trigger trigger,RandomGenerator random){
        Objects.requireNonNull(instance);Objects.requireNonNull(definition);Objects.requireNonNull(trigger);Objects.requireNonNull(random);List<ResolvedAbility> resolved=new ArrayList<>();
        for(ItemAbility ability:definition.abilities()){if(ability.trigger()!=trigger||random.nextDouble()>=ability.chance())continue;Map<String,Object> params=new LinkedHashMap<>();params.putAll(ability.parameters());params.put("item_level",instance.itemLevel());params.put("upgrade_level",instance.upgradeLevel());params.put("item_seed",instance.seed());params.put("item_id",instance.definitionId());params.put("item_instance_id",instance.instanceId().toString());params.put("item_metadata",instance.metadata());resolved.add(new ResolvedAbility(ability.skill(),params));}
        return List.copyOf(resolved);
    }
}
