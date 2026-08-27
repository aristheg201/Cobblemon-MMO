package vn.svframe.svframeitems.item;

import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.util.*;
import java.util.random.RandomGenerator;

/** Deterministic definition -> persistent item-state generation, independent of Minecraft registries. */
public final class ItemRoller {
    private final SVFrameItemsRegistry registry;
    public ItemRoller(SVFrameItemsRegistry registry) { this.registry = Objects.requireNonNull(registry); }

    public ItemInstance roll(String definitionId, int requestedLevel, long seed) {
        ItemDefinition definition = registry.item(definitionId);
        if (definition == null) throw new IllegalArgumentException("Unknown SVFrame item " + definitionId);
        if (requestedLevel < 1) throw new IllegalArgumentException("level must be >= 1");
        RandomGenerator random = new SplittableRandom(seed);
        int itemLevel = definition.clampLevel(requestedLevel);
        String rarityId = chooseRarity(definition, random);
        ItemRarity rarity = registry.rarity(rarityId);
        if (rarity == null) throw new IllegalStateException("Missing rarity " + rarityId);
        List<ItemStat> stats = definition.stats().stream().map(spec -> spec.roll(itemLevel, random).scaled(rarity.statMultiplier())).toList();
        List<SocketState> sockets = definition.sockets().stream().map(color -> new SocketState(color, null)).toList();
        UUID instanceId = new UUID(random.nextLong(), random.nextLong());
        return new ItemInstance(instanceId, definition.id(), definition.typeId(), rarityId, itemLevel, 0, definition.revision(), seed, 0, stats, sockets, Map.of());
    }

    private static String chooseRarity(ItemDefinition definition, RandomGenerator random) {
        int total = definition.rarityWeights().values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) throw new IllegalStateException("No positive rarity weights for " + definition.id());
        int roll = random.nextInt(total);
        for (Map.Entry<String,Integer> entry : definition.rarityWeights().entrySet()) if ((roll -= entry.getValue()) < 0) return entry.getKey();
        throw new IllegalStateException("Rarity selection failed for " + definition.id());
    }
}
