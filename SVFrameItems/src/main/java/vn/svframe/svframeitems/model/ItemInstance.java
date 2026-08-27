package vn.svframe.svframeitems.model;

import java.util.*;

public record ItemInstance(
        UUID instanceId,
        String definitionId,
        String typeId,
        String rarityId,
        int itemLevel,
        int upgradeLevel,
        int definitionRevision,
        long seed,
        long stateRevision,
        List<ItemStat> stats,
        List<SocketState> sockets
) {
    public ItemInstance {
        Objects.requireNonNull(instanceId, "instanceId");
        definitionId = ItemType.normalize(definitionId);
        typeId = ItemType.normalize(typeId);
        rarityId = ItemType.normalize(rarityId);
        if (itemLevel < 1) throw new IllegalArgumentException("itemLevel must be >= 1");
        if (upgradeLevel < 0) throw new IllegalArgumentException("upgradeLevel must be >= 0");
        if (definitionRevision < 0 || stateRevision < 0) throw new IllegalArgumentException("revision must be >= 0");
        stats = stats == null ? List.of() : List.copyOf(stats);
        sockets = sockets == null ? List.of() : List.copyOf(sockets);
    }
    public ItemInstance withUpgradeLevel(int level) {
        return new ItemInstance(instanceId, definitionId, typeId, rarityId, itemLevel, level, definitionRevision, seed, stateRevision + 1, stats, sockets);
    }
    public ItemInstance withSockets(List<SocketState> value) {
        return new ItemInstance(instanceId, definitionId, typeId, rarityId, itemLevel, upgradeLevel, definitionRevision, seed, stateRevision + 1, stats, value);
    }
    public ItemInstance withDefinitionRevision(int revision) {
        return new ItemInstance(instanceId, definitionId, typeId, rarityId, itemLevel, upgradeLevel, revision, seed, stateRevision + 1, stats, sockets);
    }
    public List<ItemStat> effectiveStats(double upgradeMultiplierPerLevel) {
        return effectiveStats(upgradeMultiplierPerLevel, gem -> 0d);
    }
    public List<ItemStat> effectiveStats(double upgradeMultiplierPerLevel, java.util.function.ToDoubleFunction<EmbeddedGem> gemUpgradeMultiplier) {
        double multiplier = 1.0d + Math.max(0, upgradeLevel) * upgradeMultiplierPerLevel;
        Map<String,Aggregate> merged = new LinkedHashMap<>();
        for (ItemStat stat : stats) merge(merged, stat.scaled(multiplier));
        for (SocketState socket : sockets) if (socket.gem() != null) {
            EmbeddedGem gem = socket.gem();
            double gemMultiplier = 1.0d + Math.max(0, gem.upgradeLevel()) * gemUpgradeMultiplier.applyAsDouble(gem);
            for (ItemStat stat : gem.stats()) merge(merged, stat.scaled(gemMultiplier));
        }
        List<ItemStat> out = new ArrayList<>();
        for (Aggregate aggregate : merged.values()) out.add(new ItemStat(aggregate.stat, aggregate.value, aggregate.type));
        return List.copyOf(out);
    }
    private static void merge(Map<String,Aggregate> map, ItemStat stat) {
        String key = stat.stat() + "\u0000" + stat.type().name();
        map.compute(key, (ignored, old) -> old == null ? new Aggregate(stat.stat(), stat.type(), stat.value()) : old.add(stat.value()));
    }
    private record Aggregate(String stat, vn.svframe.svframelib.fabric.runtime.NativeStatEngine.ModifierType type, double value) {
        Aggregate add(double amount) { return new Aggregate(stat, type, value + amount); }
    }
}
