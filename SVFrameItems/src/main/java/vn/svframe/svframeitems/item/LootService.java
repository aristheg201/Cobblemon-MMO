package vn.svframe.svframeitems.item;

import net.minecraft.item.ItemStack;
import vn.svframe.svframeitems.model.LootTableDefinition;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class LootService {
    private final SVFrameItemsRegistry registry; private final ItemGenerator generator;
    public LootService(SVFrameItemsRegistry registry, ItemGenerator generator){this.registry=Objects.requireNonNull(registry);this.generator=Objects.requireNonNull(generator);}
    public List<ItemStack> roll(String tableId, int level){return roll(tableId,level,ThreadLocalRandom.current());}
    public List<ItemStack> roll(String tableId,int level,RandomGenerator random){
        LootTableDefinition table=registry.lootTable(tableId); if(table==null)throw new IllegalArgumentException("Unknown loot table "+tableId);
        List<ItemStack> out=new ArrayList<>(); for(int i=0;i<table.rolls();i++){LootTableDefinition.Entry entry=choose(table.entries(),random); if(random.nextDouble()>entry.chance())continue;
            int itemLevel=Math.max(entry.minLevel(),Math.min(entry.maxLevel(),level)); if(entry.maxLevel()>entry.minLevel()) itemLevel=entry.minLevel()+random.nextInt(entry.maxLevel()-entry.minLevel()+1);
            int amount=entry.minAmount()==entry.maxAmount()?entry.minAmount():entry.minAmount()+random.nextInt(entry.maxAmount()-entry.minAmount()+1);
            ItemStack stack=generator.generate(entry.itemId(),new ItemGenerator.GenerationContext(itemLevel,random.nextLong())); stack.setCount(amount); out.add(stack);}
        return List.copyOf(out);
    }
    private static LootTableDefinition.Entry choose(List<LootTableDefinition.Entry> entries,RandomGenerator random){int total=entries.stream().mapToInt(LootTableDefinition.Entry::weight).sum();int roll=random.nextInt(total);for(var entry:entries)if((roll-=entry.weight())<0)return entry;throw new IllegalStateException();}
}
