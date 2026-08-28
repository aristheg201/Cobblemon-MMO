package vn.svframe.svframeitems.item;

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
    public enum Status { SUCCESS, FAILED, DESTROYED, NOT_AN_ITEM, TARGET_STACKED, NOT_UPGRADABLE, MAX_LEVEL, INVALID_DEFINITION, COST_REQUIRED, MISSING_COST_PROVIDER, INSUFFICIENT_COST, COST_TRANSACTION_FAILED }
    public record Result(Status status, ItemStack item, int oldLevel, int newLevel, double successChance) { public boolean success(){return status==Status.SUCCESS;} }
    public record Charge(UpgradeTemplate.Cost cost, int nextLevel) {
        public Charge { Objects.requireNonNull(cost, "cost"); if (nextLevel < 1) throw new IllegalArgumentException("nextLevel must be >= 1"); }
    }
    public interface Reservation {
        boolean available();
        void commit();
        void rollback();
    }
    public interface CostProvider {
        String id();
        Reservation reserve(ServerPlayerEntity player, List<Charge> charges);
    }

    private final SVFrameItemsRegistry registry; private final ItemGenerator generator;
    private final Map<String,CostProvider> costProviders = new ConcurrentHashMap<>();
    public UpgradeService(SVFrameItemsRegistry registry, ItemGenerator generator) {
        this.registry=Objects.requireNonNull(registry); this.generator=Objects.requireNonNull(generator);
        registerBuiltIn(new MinecraftItemCostProvider());
        registerBuiltIn(new CurrencyUpgradeCostProvider("beconomy"));
        registerBuiltIn(new CurrencyUpgradeCostProvider("cobbledollars"));
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
    public boolean hasCostProvider(String id){return id!=null&&costProviders.containsKey(ItemType.normalize(id));}

    private Result attemptInternal(ServerPlayerEntity player, ItemStack stack, RandomGenerator random) {
        Objects.requireNonNull(stack, "stack"); Objects.requireNonNull(random, "random");
        Optional<ItemInstance> read = ItemCodec.read(stack); if (read.isEmpty()) return new Result(Status.NOT_AN_ITEM, stack.copy(), 0, 0, 0);
        ItemInstance instance=read.get();
        if(stack.getCount()>1)return new Result(Status.TARGET_STACKED,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),0);
        ItemDefinition definition=registry.item(instance.definitionId());
        if(definition==null)return new Result(Status.INVALID_DEFINITION,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),0);
        if(definition.upgradeTemplateId()==null)return new Result(Status.NOT_UPGRADABLE,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),0);
        UpgradeTemplate template=registry.upgrade(definition.upgradeTemplateId()); if(template==null)return new Result(Status.INVALID_DEFINITION,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),0);
        if(instance.upgradeLevel()>=template.maxLevel())return new Result(Status.MAX_LEVEL,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),0);
        int nextLevel=instance.upgradeLevel()+1; double chance=template.chanceForNextLevel(instance.upgradeLevel());
        List<Reservation> reservations=new ArrayList<>();
        if(!template.costs().isEmpty()) {
            if(player==null)return new Result(Status.COST_REQUIRED,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),chance);
            LinkedHashMap<CostProvider,List<Charge>> grouped=new LinkedHashMap<>();
            for(UpgradeTemplate.Cost cost:template.costs()) {
                CostProvider provider=costProviders.get(ItemType.normalize(cost.provider()));
                if(provider==null)return new Result(Status.MISSING_COST_PROVIDER,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),chance);
                grouped.computeIfAbsent(provider,ignored->new ArrayList<>()).add(new Charge(cost,nextLevel));
            }
            try {
                for(Map.Entry<CostProvider,List<Charge>> entry:grouped.entrySet()) {
                    Reservation reservation=Objects.requireNonNull(entry.getKey().reserve(player,List.copyOf(entry.getValue())),"cost provider returned null reservation");
                    reservations.add(reservation);
                    if(!reservation.available()){
                        RuntimeException rollbackFailure=rollbackReservations(reservations,reservations.size());
                        if(rollbackFailure!=null)throw new IllegalStateException("Upgrade cost reservation rollback failed",rollbackFailure);
                        return new Result(Status.INSUFFICIENT_COST,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),chance);
                    }
                }
            } catch(RuntimeException exception) {
                RuntimeException rollbackFailure=rollbackReservations(reservations,reservations.size());
                if(rollbackFailure!=null){exception.addSuppressed(rollbackFailure);throw new IllegalStateException("Upgrade cost reservation rollback failed",exception);}
                return new Result(Status.COST_TRANSACTION_FAILED,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),chance);
            }
        }

        Result outcome;
        try {
            if(random.nextDouble()<chance)outcome=new Result(Status.SUCCESS,generator.rebuild(stack,instance.withUpgradeLevel(nextLevel)),instance.upgradeLevel(),nextLevel,chance);
            else if(template.destroyOnFail())outcome=new Result(Status.DESTROYED,ItemStack.EMPTY,instance.upgradeLevel(),instance.upgradeLevel(),chance);
            else outcome=new Result(Status.FAILED,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),chance);
        } catch(RuntimeException exception) {
            RuntimeException rollbackFailure=rollbackReservations(reservations,reservations.size());
            if(rollbackFailure!=null)exception.addSuppressed(rollbackFailure);
            throw exception;
        }

        int committed=0;
        try {
            for(Reservation reservation:reservations){committed++;reservation.commit();}
        } catch (RuntimeException exception) {
            RuntimeException rollbackFailure=rollbackReservations(reservations,committed);
            if(rollbackFailure!=null){exception.addSuppressed(rollbackFailure);throw new IllegalStateException("Upgrade cost transaction rollback failed",exception);}
            return new Result(Status.COST_TRANSACTION_FAILED,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),chance);
        }
        return outcome;
    }
    public double statMultiplier(ItemInstance instance) {
        ItemDefinition definition=registry.item(instance.definitionId()); if(definition==null||definition.upgradeTemplateId()==null)return 0d;
        UpgradeTemplate template=registry.upgrade(definition.upgradeTemplateId()); return template==null?0d:template.statMultiplierPerLevel();
    }
    public double statMultiplier(EmbeddedGem gem) { return statMultiplier(gem.toItemInstance()); }
    private void registerBuiltIn(CostProvider provider){costProviders.put(ItemType.normalize(provider.id()),provider);}
    private static RuntimeException rollbackReservations(List<Reservation> reservations,int count){RuntimeException first=null;for(int i=Math.min(count,reservations.size())-1;i>=0;i--)try{reservations.get(i).rollback();}catch(RuntimeException exception){if(first==null)first=exception;else first.addSuppressed(exception);}return first;}

    private static final class MinecraftItemCostProvider implements CostProvider {
        @Override public String id(){return "minecraft_item";}
        @Override public Reservation reserve(ServerPlayerEntity player,List<Charge> charges){
            List<MinecraftItemCostPlanner.StackView> views=new ArrayList<>();
            for(int slot=0;slot<player.getInventory().size();slot++){
                ItemStack stack=player.getInventory().getStack(slot);String itemId=stack.isEmpty()?"":Registries.ITEM.getId(stack.getItem()).toString();
                views.add(new MinecraftItemCostPlanner.StackView(slot,itemId,stack.isEmpty()?0:stack.getCount(),ItemCodec.isSVFrameItem(stack)));
            }
            int nextLevel=charges.isEmpty()?1:charges.getFirst().nextLevel();
            List<UpgradeTemplate.Cost> costs=charges.stream().map(Charge::cost).toList();
            Optional<List<MinecraftItemCostPlanner.Consumption>> planned=MinecraftItemCostPlanner.plan(views,costs,nextLevel);
            if(planned.isEmpty())return UnavailableReservation.INSTANCE;
            return new InventoryReservation(player,planned.get());
        }
    }
    private enum UnavailableReservation implements Reservation {
        INSTANCE;
        public boolean available(){return false;} public void commit(){throw new IllegalStateException("Cannot commit unavailable reservation");} public void rollback(){}
    }
    private static final class InventoryReservation implements Reservation {
        private final ServerPlayerEntity player; private final List<MinecraftItemCostPlanner.Consumption> plan; private final Map<Integer,ItemStack> before=new LinkedHashMap<>(); private boolean committed;
        private InventoryReservation(ServerPlayerEntity player,List<MinecraftItemCostPlanner.Consumption> plan){this.player=Objects.requireNonNull(player);this.plan=List.copyOf(plan);}
        public boolean available(){return true;}
        public void commit(){
            if(committed)return;
            Map<Integer,Integer> totals=new LinkedHashMap<>(); for(var c:plan)totals.merge(c.slot(),c.count(),Integer::sum);
            for(var c:plan){ItemStack stack=player.getInventory().getStack(c.slot());Identifier id=Identifier.tryParse(c.itemId());if(id==null||!Registries.ITEM.containsId(id)||stack.isEmpty()||!stack.isOf(Registries.ITEM.get(id))||ItemCodec.isSVFrameItem(stack)||stack.getCount()<totals.get(c.slot()))throw new IllegalStateException("Upgrade cost changed after reservation at slot "+c.slot());before.putIfAbsent(c.slot(),stack.copy());}
            for(var entry:totals.entrySet())player.getInventory().getStack(entry.getKey()).decrement(entry.getValue());
            player.getInventory().markDirty(); committed=true;
        }
        public void rollback(){if(!committed)return;for(var entry:before.entrySet())player.getInventory().setStack(entry.getKey(),entry.getValue().copy());player.getInventory().markDirty();committed=false;}
    }
}
