package vn.svframe.svframeitems.item;

import net.minecraft.item.ItemStack;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class LootService {
    public record Context(int level, RandomGenerator random, Map<String,Object> attributes) {
        public Context { if(level<1)throw new IllegalArgumentException("level must be >= 1"); random=Objects.requireNonNull(random,"random"); attributes=attributes==null?Map.of():Map.copyOf(attributes); }
        public static Context random(int level){return new Context(level,ThreadLocalRandom.current(),Map.of());}
        public Context withLevel(int value){return new Context(value,random,attributes);}
        public Context withAttribute(String key,Object value){Map<String,Object> next=new LinkedHashMap<>(attributes);next.put(Objects.requireNonNull(key),value);return new Context(level,random,next);}
        public Optional<Object> attribute(String key){return Optional.ofNullable(attributes.get(key));}
    }
    @FunctionalInterface public interface Condition { boolean test(Context context, LootTableDefinition.Entry entry); }
    @FunctionalInterface public interface Reward { Collection<ItemStack> generate(Context context, LootTableDefinition.Entry entry, ItemGenerator generator); }

    private final SVFrameItemsRegistry registry; private final ItemGenerator generator;
    private final Map<String,Condition> conditions=new ConcurrentHashMap<>(); private final Map<String,Reward> rewards=new ConcurrentHashMap<>();
    public LootService(SVFrameItemsRegistry registry, ItemGenerator generator){
        this.registry=Objects.requireNonNull(registry);this.generator=Objects.requireNonNull(generator);
        conditions.put("always",(context,entry)->true);
        rewards.put("item",(context,entry,itemGenerator)->{
            int remaining=((Number)context.attributes().getOrDefault("svframeitems:amount",1)).intValue();List<ItemStack> stacks=new ArrayList<>();
            while(remaining>0){ItemStack stack=itemGenerator.generate(entry.itemId(),new ItemGenerator.GenerationContext(context.level(),context.random().nextLong()));int count=Math.min(remaining,stack.getMaxCount());stack.setCount(count);remaining-=count;stacks.add(stack);}
            return List.copyOf(stacks);
        });
    }
    public List<ItemStack> roll(String tableId, int level){return roll(tableId,Context.random(level));}
    public List<ItemStack> roll(String tableId,int level,RandomGenerator random){return roll(tableId,new Context(level,random,Map.of()));}
    public List<ItemStack> roll(String tableId,Context context){
        LootTableDefinition table=registry.lootTable(tableId); if(table==null)throw new IllegalArgumentException("Unknown loot table "+tableId);
        Map<LootTableDefinition.Entry,Boolean> eligibility=new IdentityHashMap<>();
        for(LootTableDefinition.Entry entry:table.entries()){Condition condition=conditions.get(entry.conditionId());if(condition==null)throw new IllegalStateException("Unknown loot condition "+entry.conditionId()+" in "+table.id());eligibility.put(entry,condition.test(context,entry));}
        List<ItemStack> out=new ArrayList<>();
        for(LootPlanner.Roll roll:LootPlanner.plan(table,context.level(),context.random(),entry->eligibility.getOrDefault(entry,false))){
            LootTableDefinition.Entry entry=roll.entry();Reward reward=rewards.get(entry.rewardId());if(reward==null)throw new IllegalStateException("Unknown loot reward "+entry.rewardId()+" in "+table.id());
            Context entryContext=context.withLevel(roll.itemLevel()).withAttribute("svframeitems:amount",roll.amount());Collection<ItemStack> generated=reward.generate(entryContext,entry,generator);if(generated==null)throw new IllegalStateException("Loot reward "+entry.rewardId()+" returned null");for(ItemStack stack:generated)if(stack!=null&&!stack.isEmpty())out.add(stack);
        }
        return List.copyOf(out);
    }
    public AutoCloseable registerCondition(String id,Condition condition){String key=ItemType.normalize(id);Objects.requireNonNull(condition);if(key.equals("always"))throw new IllegalArgumentException("always is built in");if(conditions.putIfAbsent(key,condition)!=null)throw new IllegalStateException("Loot condition already registered: "+key);return ()->conditions.remove(key,condition);}
    public AutoCloseable registerReward(String id,Reward reward){String key=ItemType.normalize(id);Objects.requireNonNull(reward);if(key.equals("item"))throw new IllegalArgumentException("item is built in");if(rewards.putIfAbsent(key,reward)!=null)throw new IllegalStateException("Loot reward already registered: "+key);return ()->rewards.remove(key,reward);}
}
