package vn.svframe.svframeitems.item;

import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class ItemGenerator {
    public record GenerationContext(int level, long seed) {
        public GenerationContext { if (level < 1) throw new IllegalArgumentException("level must be >= 1"); }
        public static GenerationContext random(int level) { return new GenerationContext(level, ThreadLocalRandom.current().nextLong()); }
    }

    private final SVFrameItemsRegistry registry;
    private final ItemFormatter formatter;
    public ItemGenerator(SVFrameItemsRegistry registry, ItemFormatter formatter) { this.registry=Objects.requireNonNull(registry); this.formatter=Objects.requireNonNull(formatter); }

    public ItemStack generate(String definitionId, int level) { return generate(definitionId, GenerationContext.random(level)); }
    public ItemStack generate(String definitionId, GenerationContext context) {
        ItemDefinition definition = requireDefinition(definitionId);
        RandomGenerator random = new SplittableRandom(context.seed());
        int itemLevel = definition.clampLevel(context.level());
        String rarityId = chooseRarity(definition, random);
        ItemRarity rarity = registry.rarity(rarityId);
        if (rarity == null) throw new IllegalStateException("Missing rarity " + rarityId);
        Identifier materialId = Identifier.tryParse(definition.materialId());
        if (materialId == null || !Registries.ITEM.containsId(materialId)) throw new IllegalStateException("Unknown Minecraft item " + definition.materialId() + " for " + definition.id());
        Item item = Registries.ITEM.get(materialId);
        ItemStack stack = new ItemStack(item);
        List<ItemStat> stats = definition.stats().stream().map(spec -> spec.roll(itemLevel, random)).toList();
        List<SocketState> sockets = definition.sockets().stream().map(color -> new SocketState(color, null)).toList();
        UUID instanceId = new UUID(random.nextLong(), random.nextLong());
        ItemInstance instance = new ItemInstance(instanceId, definition.id(), definition.typeId(), rarityId, itemLevel, 0, definition.revision(), context.seed(), 0, stats, sockets);
        ItemCodec.write(stack, instance);
        formatter.apply(stack, definition, instance, rarity);
        return stack;
    }

    public ItemStack rebuild(ItemInstance instance) {
        ItemDefinition definition = requireDefinition(instance.definitionId());
        Identifier materialId = Identifier.tryParse(definition.materialId());
        if (materialId == null || !Registries.ITEM.containsId(materialId)) throw new IllegalStateException("Unknown Minecraft item " + definition.materialId());
        ItemStack stack = new ItemStack(Registries.ITEM.get(materialId));
        ItemCodec.write(stack, instance);
        ItemRarity rarity = registry.rarity(instance.rarityId());
        if (rarity == null) throw new IllegalStateException("Missing rarity " + instance.rarityId());
        formatter.apply(stack, definition, instance, rarity);
        return stack;
    }

    private ItemDefinition requireDefinition(String id) { ItemDefinition definition=registry.item(id); if(definition==null) throw new IllegalArgumentException("Unknown SVFrame item " + id); return definition; }
    private static String chooseRarity(ItemDefinition definition, RandomGenerator random) {
        int total = definition.rarityWeights().values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) throw new IllegalStateException("No positive rarity weights for " + definition.id());
        int roll = random.nextInt(total);
        for (Map.Entry<String,Integer> entry : definition.rarityWeights().entrySet()) { if ((roll -= entry.getValue()) < 0) return entry.getKey(); }
        throw new IllegalStateException("Rarity selection failed for " + definition.id());
    }
}
