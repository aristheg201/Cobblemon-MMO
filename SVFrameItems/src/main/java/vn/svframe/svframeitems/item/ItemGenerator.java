package vn.svframe.svframeitems.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class ItemGenerator {
    public record GenerationContext(int level, long seed) {
        public GenerationContext { if (level < 1) throw new IllegalArgumentException("level must be >= 1"); }
        public static GenerationContext random(int level) { return new GenerationContext(level, ThreadLocalRandom.current().nextLong()); }
    }
    public enum Phase { GENERATED, REBUILT }
    @FunctionalInterface public interface Mechanic {
        void apply(ItemStack stack, ItemDefinition definition, ItemInstance instance, Phase phase);
    }

    private final SVFrameItemsRegistry registry;
    private final ItemFormatter formatter;
    private final ItemRoller roller;
    private final CopyOnWriteArrayList<Mechanic> mechanics = new CopyOnWriteArrayList<>();
    public ItemGenerator(SVFrameItemsRegistry registry, ItemFormatter formatter) { this.registry=Objects.requireNonNull(registry); this.formatter=Objects.requireNonNull(formatter); this.roller=new ItemRoller(registry); }

    public ItemStack generate(String definitionId, int level) { return generate(definitionId, GenerationContext.random(level)); }
    public ItemStack generate(String definitionId, GenerationContext context) {
        ItemDefinition definition = requireDefinition(definitionId);
        ItemInstance instance = roller.roll(definitionId, context.level(), context.seed());
        ItemRarity rarity = requireRarity(instance.rarityId());
        return finalizeStack(new ItemStack(requireMaterial(definition)), definition, instance, rarity, Phase.GENERATED);
    }

    public ItemStack rebuild(ItemInstance instance) {
        ItemDefinition definition = requireDefinition(instance.definitionId());
        return finalizeStack(new ItemStack(requireMaterial(definition)), definition, instance, requireRarity(instance.rarityId()), Phase.REBUILT);
    }

    public ItemStack rebuild(ItemStack source, ItemInstance instance) {
        Objects.requireNonNull(source, "source");
        ItemDefinition definition = requireDefinition(instance.definitionId());
        Item expected = requireMaterial(definition);
        if (source.isEmpty() || !source.isOf(expected)) throw new IllegalArgumentException("Source stack material does not match " + definition.id());
        ItemStack copy = source.copy();
        return finalizeStack(copy, definition, instance, requireRarity(instance.rarityId()), Phase.REBUILT);
    }

    public AutoCloseable registerMechanic(Mechanic mechanic) {
        Objects.requireNonNull(mechanic, "mechanic"); mechanics.add(mechanic); return () -> mechanics.remove(mechanic);
    }

    private ItemStack finalizeStack(ItemStack stack, ItemDefinition definition, ItemInstance instance, ItemRarity rarity, Phase phase) {
        ItemType type = registry.type(definition.typeId());
        if (type == null) throw new IllegalStateException("Missing item type " + definition.typeId());
        stack.set(DataComponentTypes.MAX_STACK_SIZE, type.maxStackSize());
        if (stack.getCount() > type.maxStackSize()) stack.setCount(type.maxStackSize());
        ItemCodec.write(stack, instance);
        formatter.apply(stack, definition, instance, rarity);
        for (Mechanic mechanic : mechanics) mechanic.apply(stack, definition, instance, phase);
        return stack;
    }

    private ItemDefinition requireDefinition(String id) { ItemDefinition definition=registry.item(id); if(definition==null) throw new IllegalArgumentException("Unknown SVFrame item " + id); return definition; }
    private ItemRarity requireRarity(String id) { ItemRarity rarity=registry.rarity(id); if(rarity==null) throw new IllegalStateException("Missing rarity " + id); return rarity; }
    private static Item requireMaterial(ItemDefinition definition) {
        Identifier materialId = Identifier.tryParse(definition.materialId());
        if (materialId == null || !Registries.ITEM.containsId(materialId)) throw new IllegalStateException("Unknown Minecraft item " + definition.materialId() + " for " + definition.id());
        return Registries.ITEM.get(materialId);
    }
}
