package vn.svframe.svframeitems.item;

import vn.svframe.svframeitems.model.RecipeDefinition;

import java.util.*;

/** Pure recipe reservation planner shared by JUnit and the Minecraft Inventory adapter. */
public final class RecipePlanner {
    public record StackView(String vanillaItemId, String svframeItemId, int count) {
        public StackView {
            vanillaItemId = vanillaItemId == null ? "" : vanillaItemId.trim().toLowerCase(Locale.ROOT);
            svframeItemId = svframeItemId == null ? null : svframeItemId.trim().toLowerCase(Locale.ROOT);
            if (count < 0) throw new IllegalArgumentException("count must be >= 0");
        }
    }
    public record Consumption(int slot, int count) {
        public Consumption { if (slot < 0 || count < 1) throw new IllegalArgumentException("invalid consumption"); }
    }
    private RecipePlanner() {}

    public static Optional<List<Consumption>> plan(List<StackView> slots, RecipeDefinition recipe) {
        Objects.requireNonNull(slots); Objects.requireNonNull(recipe);
        Map<Integer,Integer> reserved = new LinkedHashMap<>();
        for (RecipeDefinition.Ingredient ingredient : recipe.ingredients()) {
            int remaining = ingredient.count();
            for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
                StackView stack = slots.get(slot); if (stack == null || stack.count() == 0 || !matches(stack, ingredient)) continue;
                int available = stack.count() - reserved.getOrDefault(slot, 0); if (available <= 0) continue;
                int take = Math.min(available, remaining); reserved.merge(slot, take, Integer::sum); remaining -= take;
            }
            if (remaining > 0) return Optional.empty();
        }
        List<Consumption> result = new ArrayList<>(); reserved.forEach((slot,count) -> result.add(new Consumption(slot,count)));
        return Optional.of(List.copyOf(result));
    }

    private static boolean matches(StackView stack, RecipeDefinition.Ingredient ingredient) {
        if (ingredient.kind() == RecipeDefinition.IngredientKind.SVFRAME_ITEM) return ingredient.id().equals(stack.svframeItemId());
        return stack.svframeItemId() == null && ingredient.id().equals(stack.vanillaItemId());
    }
}
