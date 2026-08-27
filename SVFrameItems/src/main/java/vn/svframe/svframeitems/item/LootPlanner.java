package vn.svframe.svframeitems.item;

import vn.svframe.svframeitems.model.LootTableDefinition;

import java.util.*;
import java.util.random.RandomGenerator;

/** Pure weighted/chance/context planner; reward materialization stays in LootService. */
public final class LootPlanner {
    public record Roll(LootTableDefinition.Entry entry, int itemLevel, int amount) {}
    private LootPlanner() {}

    public static List<Roll> plan(LootTableDefinition table, int contextLevel, RandomGenerator random, java.util.function.Predicate<LootTableDefinition.Entry> eligible) {
        Objects.requireNonNull(table); Objects.requireNonNull(random); Objects.requireNonNull(eligible);
        if (contextLevel < 1) throw new IllegalArgumentException("contextLevel must be >= 1");
        List<Roll> result = new ArrayList<>();
        for (int i=0;i<table.rolls();i++) {
            List<LootTableDefinition.Entry> candidates = table.entries().stream().filter(eligible).toList();
            if (candidates.isEmpty()) continue;
            LootTableDefinition.Entry entry = choose(candidates, random);
            if (random.nextDouble() >= entry.chance()) continue;
            result.add(new Roll(entry, entry.clampLevel(contextLevel), entry.rollAmount(random)));
        }
        return List.copyOf(result);
    }

    private static LootTableDefinition.Entry choose(List<LootTableDefinition.Entry> entries, RandomGenerator random) {
        long total = entries.stream().mapToLong(LootTableDefinition.Entry::weight).sum();
        if (total <= 0 || total > Integer.MAX_VALUE) throw new IllegalStateException("Invalid total loot weight " + total);
        int roll = random.nextInt((int) total);
        for (LootTableDefinition.Entry entry : entries) if ((roll -= entry.weight()) < 0) return entry;
        throw new IllegalStateException("Weighted loot selection failed");
    }
}
