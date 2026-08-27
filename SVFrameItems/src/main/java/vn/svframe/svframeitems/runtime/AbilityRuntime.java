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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class AbilityRuntime {
    private final SVFrameItemsRegistry registry;
    private final Map<CooldownKey,Long> cooldowns=new ConcurrentHashMap<>();
    public AbilityRuntime(SVFrameItemsRegistry registry){this.registry=Objects.requireNonNull(registry);}
    public void initialize(){
        AttackEntityCallback.EVENT.register((player,world,hand,entity,hit)->{ if(!world.isClient()&&player instanceof ServerPlayerEntity server) trigger(server,server.getStackInHand(hand),ItemAbility.Trigger.ATTACK,entity); return ActionResult.PASS; });
        UseItemCallback.EVENT.register((player,world,hand)->{ if(!world.isClient()&&player instanceof ServerPlayerEntity server) trigger(server,server.getStackInHand(hand),ItemAbility.Trigger.USE,null); return TypedActionResult.pass(player.getStackInHand(hand)); });
    }
    public void triggerEquip(ServerPlayerEntity player,ItemStack stack){trigger(player,stack,ItemAbility.Trigger.EQUIP,null);}
    public void clear(UUID player){cooldowns.keySet().removeIf(key->key.player.equals(player));}
    private void trigger(ServerPlayerEntity player,ItemStack stack,ItemAbility.Trigger trigger,Entity target){
        Optional<ItemInstance> read=ItemCodec.read(stack);if(read.isEmpty())return;ItemInstance instance=read.get();ItemDefinition definition=registry.item(instance.definitionId());if(definition==null)return;long tick=SVFrameLibFabricMod.currentTick();
        for(ItemAbility ability:definition.abilities()){if(ability.trigger()!=trigger)continue;CooldownKey key=new CooldownKey(player.getUuid(),instance.instanceId(),trigger,ability.skill());if(cooldowns.getOrDefault(key,0L)>tick)continue;if(ThreadLocalRandom.current().nextDouble()>ability.chance())continue;
            Map<String,Object> params=new LinkedHashMap<>();params.putAll(ability.parameters());params.put("item_level",instance.itemLevel());params.put("upgrade_level",instance.upgradeLevel());params.put("item_seed",instance.seed());
            boolean cast=SVFrameLibFabricMod.castSkill(ability.skill(),player.getUuid(),target==null?player.getUuid():target.getUuid(),params);if(cast&&ability.cooldownTicks()>0)cooldowns.put(key,tick+ability.cooldownTicks());}
        cooldowns.entrySet().removeIf(entry->entry.getValue()<=tick);
    }
    private record CooldownKey(UUID player,UUID item,ItemAbility.Trigger trigger,String skill){}
}
