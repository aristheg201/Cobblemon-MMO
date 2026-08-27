package vn.svframe.svframeitems;

import org.junit.jupiter.api.Test;
import vn.svframe.svframeitems.item.RecipePlanner;
import vn.svframe.svframeitems.model.RecipeDefinition;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RecipeRuntimeTest {
    private static final RecipeDefinition RECIPE=new RecipeDefinition("blade",List.of(
            new RecipeDefinition.Ingredient(RecipeDefinition.IngredientKind.VANILLA,"minecraft:iron_ingot",12),
            new RecipeDefinition.Ingredient(RecipeDefinition.IngredientKind.VANILLA,"minecraft:diamond",2)),"trailblazer_blade",1,10);

    @Test void plannerReservesIngredientsExactlyOnce() {
        var plan=RecipePlanner.plan(List.of(
                new RecipePlanner.StackView("minecraft:iron_ingot",null,8),
                new RecipePlanner.StackView("minecraft:iron_ingot",null,4),
                new RecipePlanner.StackView("minecraft:diamond",null,2)),RECIPE).orElseThrow();
        assertEquals(3,plan.size());assertEquals(14,plan.stream().mapToInt(RecipePlanner.Consumption::count).sum());
    }

    @Test void vanillaIngredientNeverConsumesRpgItemWithSameMinecraftMaterial() {
        assertTrue(RecipePlanner.plan(List.of(
                new RecipePlanner.StackView("minecraft:iron_ingot","rpg_ingot",12),
                new RecipePlanner.StackView("minecraft:diamond",null,2)),RECIPE).isEmpty());
    }

    @Test void svframeIngredientMatchesPersistentDefinitionIdentity() {
        RecipeDefinition recipe=new RecipeDefinition("gemmed",List.of(new RecipeDefinition.Ingredient(RecipeDefinition.IngredientKind.SVFRAME_ITEM,"ruby_gem",2)),"trailblazer_blade",1,1);
        assertTrue(RecipePlanner.plan(List.of(new RecipePlanner.StackView("minecraft:redstone","ruby_gem",2)),recipe).isPresent());
        assertTrue(RecipePlanner.plan(List.of(new RecipePlanner.StackView("minecraft:redstone","other_gem",2)),recipe).isEmpty());
    }
}
