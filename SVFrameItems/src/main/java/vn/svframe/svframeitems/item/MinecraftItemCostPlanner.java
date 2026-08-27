package vn.svframe.svframeitems.item;

import vn.svframe.svframeitems.model.UpgradeTemplate;

import java.util.*;

/** Pure reservation planner for the built-in Minecraft-item upgrade cost provider. */
public final class MinecraftItemCostPlanner {
    public record StackView(int slot, String itemId, int count, boolean svframeItem) {
        public StackView {
            if (slot < 0) throw new IllegalArgumentException("slot must be >= 0");
            itemId = Objects.requireNonNull(itemId, "itemId").trim().toLowerCase(Locale.ROOT);
            if (count < 0) throw new IllegalArgumentException("count must be >= 0");
        }
    }
    public record Consumption(int slot, String itemId, int count) {
        public Consumption {
            if (slot < 0 || count < 1) throw new IllegalArgumentException("invalid consumption");
            itemId = Objects.requireNonNull(itemId, "itemId").trim().toLowerCase(Locale.ROOT);
        }
    }
    private MinecraftItemCostPlanner() {}

    public static Optional<List<Consumption>> plan(List<StackView> stacks, List<UpgradeTemplate.Cost> costs, int nextLevel) {
        Objects.requireNonNull(stacks, "stacks");
        Objects.requireNonNull(costs, "costs");
        if(nextLevel<1)throw new IllegalArgumentException("nextLevel must be >= 1");
        Map<String,Integer> required = new LinkedHashMap<>();
        for (UpgradeTemplate.Cost cost : costs) {
            if (!"minecraft_item".equals(cost.provider())) throw new IllegalArgumentException("Non-minecraft_item charge " + cost.provider());
            required.merge(cost.id(), cost.amountForNextLevel(nextLevel - 1), Math::addExact);
        }
        List<Consumption> result = new ArrayList<>();
        for (Map.Entry<String,Integer> need : required.entrySet()) {
            int remaining = need.getValue();
            for (StackView stack : stacks) {
                if (remaining <= 0) break;
                if (stack == null || stack.svframeItem() || stack.count() <= 0 || !need.getKey().equals(stack.itemId())) continue;
                int take = Math.min(remaining, stack.count());
                result.add(new Consumption(stack.slot(), stack.itemId(), take));
                remaining -= take;
            }
            if (remaining > 0) return Optional.empty();
        }
        return Optional.of(List.copyOf(result));
    }
}
