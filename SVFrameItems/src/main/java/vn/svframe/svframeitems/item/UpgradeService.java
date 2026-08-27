package vn.svframe.svframeitems.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class UpgradeService {
    public enum Status { SUCCESS, FAILED, DESTROYED, NOT_AN_ITEM, NOT_UPGRADABLE, MAX_LEVEL, INVALID_DEFINITION, COST_REQUIRED, MISSING_COST_PROVIDER, INSUFFICIENT_COST }
    public record Result(Status status, ItemStack item, int oldLevel, int newLevel, double successChance) { public boolean success(){return status==Status.SUCCESS;} }
    public interface CostProvider {
        String id();
        boolean canPay(ServerPlayerEntity player, UpgradeTemplate.Cost cost, int nextLevel);
        void pay(ServerPlayerEntity player, UpgradeTemplate.Cost cost, int nextLevel);
    }

    private final SVFrameItemsRegistry registry; private final ItemGenerator generator;
    private final Map<String,CostProvider> costProviders = new ConcurrentHashMap<>();
    public UpgradeService(SVFrameItemsRegistry registry, ItemGenerator generator) {
        this.registry=Objects.requireNonNull(registry); this.generator=Objects.requireNonNull(generator);
        registerBuiltIn(new MinecraftItemCostProvider());
    }
    public Result attempt(ItemStack stack) { return attempt(stack, ThreadLocalRandom.current()); }
    public Result attempt(ItemStack stack, RandomGenerator random) { return attemptInternal(null, stack, random); }
    public Result attempt(ServerPlayerEntity player, ItemStack stack) { return attempt(player, stack, ThreadLocalRandom.current()); }
    public Result attempt(ServerPlayerEntity player, ItemStack stack, RandomGenerator random) { return attemptInternal(Objects.requireNonNull(player, "player"), stack, random); }

    public AutoCloseable registerCostProvider(CostProvider provider) {
        Objects.requireNonNull(provider, "provider"); String id=ItemType.normalize(provider.id());
        if (id.equals("minecraft_item")) throw new IllegalArgumentException("minecraft_item provider is built in");
        CostProvider previous=costProviders.putIfAbsent(id,provider); if(previous!=null)throw new IllegalStateException("Upgrade cost provider already registered: "+id);
        return ()->costProviders.remove(id,provider);
    }

    private Result attemptInternal(ServerPlayerEntity player, ItemStack stack, RandomGenerator random) {
        Objects.requireNonNull(stack, "stack"); Objects.requireNonNull(random, "random");
        Optional<ItemInstance> read = ItemCodec.read(stack); if (read.isEmpty()) return new Result(Status.NOT_AN_ITEM, stack.copy(), 0, 0, 0);
        ItemInstance instance=read.get(); ItemDefinition definition=registry.item(instance.definitionId());
        if(definition==null)return new Result(Status.INVALID_DEFINITION,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),0);
        if(definition.upgradeTemplateId()==null)return new Result(Status.NOT_UPGRADABLE,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),0);
        UpgradeTemplate template=registry.upgrade(definition.upgradeTemplateId()); if(template==null)return new Result(Status.INVALID_DEFINITION,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),0);
        if(instance.upgradeLevel()>=template.maxLevel())return new Result(Status.MAX_LEVEL,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),0);
        int nextLevel=instance.upgradeLevel()+1;
        if(!template.costs().isEmpty()) {
            if(player==null)return new Result(Status.COST_REQUIRED,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),template.chanceForNextLevel(instance.upgradeLevel()));
            List<Payment> payments=new ArrayList<>();
            for(UpgradeTemplate.Cost cost:template.costs()) {
                CostProvider provider=costProviders.get(ItemType.normalize(cost.provider()));
                if(provider==null)return new Result(Status.MISSING_COST_PROVIDER,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),template.chanceForNextLevel(instance.upgradeLevel()));
                if(!provider.canPay(player,cost,nextLevel))return new Result(Status.INSUFFICIENT_COST,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),template.chanceForNextLevel(instance.upgradeLevel()));
                payments.add(new Payment(provider,cost));
            }
            for(Payment payment:payments)payment.provider.pay(player,payment.cost,nextLevel);
        }
        double chance=template.chanceForNextLevel(instance.upgradeLevel());
        if(random.nextDouble()<chance)return new Result(Status.SUCCESS,generator.rebuild(stack,instance.withUpgradeLevel(nextLevel)),instance.upgradeLevel(),nextLevel,chance);
        if(template.destroyOnFail())return new Result(Status.DESTROYED,ItemStack.EMPTY,instance.upgradeLevel(),instance.upgradeLevel(),chance);
        return new Result(Status.FAILED,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),chance);
    }
    public double statMultiplier(ItemInstance instance) {
        ItemDefinition definition=registry.item(instance.definitionId()); if(definition==null||definition.upgradeTemplateId()==null)return 0d;
        UpgradeTemplate template=registry.upgrade(definition.upgradeTemplateId()); return template==null?0d:template.statMultiplierPerLevel();
    }
    public double statMultiplier(EmbeddedGem gem) { return statMultiplier(gem.toItemInstance()); }
    private void registerBuiltIn(CostProvider provider){costProviders.put(ItemType.normalize(provider.id()),provider);}
    private record Payment(CostProvider provider,UpgradeTemplate.Cost cost){}

    private static final class MinecraftItemCostProvider implements CostProvider {
        @Override public String id(){return "minecraft_item";}
        @Override public boolean canPay(ServerPlayerEntity player,UpgradeTemplate.Cost cost,int nextLevel){
            Item item=item(cost);int needed=cost.amountForNextLevel(nextLevel-1);int found=0;
            for(int slot=0;slot<player.getInventory().size();slot++){ItemStack stack=player.getInventory().getStack(slot);if(!stack.isEmpty()&&stack.isOf(item)&&!ItemCodec.isSVFrameItem(stack)){found+=stack.getCount();if(found>=needed)return true;}}
            return false;
        }
        @Override public void pay(ServerPlayerEntity player,UpgradeTemplate.Cost cost,int nextLevel){
            Item item=item(cost);int remaining=cost.amountForNextLevel(nextLevel-1);
            for(int slot=0;slot<player.getInventory().size()&&remaining>0;slot++){ItemStack stack=player.getInventory().getStack(slot);if(stack.isEmpty()||!stack.isOf(item)||ItemCodec.isSVFrameItem(stack))continue;int take=Math.min(remaining,stack.getCount());stack.decrement(take);remaining-=take;}
            if(remaining!=0)throw new IllegalStateException("Upgrade cost changed after preflight for "+cost.id());
        }
        private static Item item(UpgradeTemplate.Cost cost){Identifier id=Identifier.tryParse(cost.id());if(id==null||!Registries.ITEM.containsId(id))throw new IllegalArgumentException("Unknown Minecraft upgrade cost item "+cost.id());return Registries.ITEM.get(id);}
    }
}
