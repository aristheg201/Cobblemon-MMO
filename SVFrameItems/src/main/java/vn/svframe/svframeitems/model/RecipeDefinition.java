package vn.svframe.svframeitems.model;

import java.util.*;

public record RecipeDefinition(String id, List<Ingredient> ingredients, String outputItemId, int outputAmount, int outputLevel) {
    public enum IngredientKind { VANILLA, SVFRAME_ITEM }
    public record Ingredient(IngredientKind kind, String id, int count) {
        public Ingredient {
            Objects.requireNonNull(kind, "kind");
            id = kind == IngredientKind.SVFRAME_ITEM ? ItemType.normalize(id) : Objects.requireNonNull(id).trim().toLowerCase(Locale.ROOT);
            if (count < 1) throw new IllegalArgumentException("ingredient count must be >= 1");
        }
    }
    public RecipeDefinition {
        id = ItemType.normalize(id);
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        if (ingredients.isEmpty()) throw new IllegalArgumentException("recipe requires ingredients");
        outputItemId = ItemType.normalize(outputItemId);
        if (outputAmount < 1 || outputAmount > 99) throw new IllegalArgumentException("invalid output amount");
        if (outputLevel < 1) throw new IllegalArgumentException("outputLevel must be >= 1");
    }
}
