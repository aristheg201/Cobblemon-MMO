package vn.svframe.svframeitems.runtime;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframeitems.item.UpgradeService;
import vn.svframe.svframeitems.item.LootService;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.util.Objects;

/** Minecraft-runtime validation kept outside the pure registry so JUnit can stay loader-independent. */
public final class RuntimeDefinitionValidator {
    private RuntimeDefinitionValidator() {}

    public static void validate(SVFrameItemsRegistry registry, UpgradeService upgrades, LootService loot) {
        Objects.requireNonNull(registry,"registry"); Objects.requireNonNull(upgrades,"upgrades"); Objects.requireNonNull(loot,"loot");
        for(ItemDefinition item:registry.items()){
            requireMinecraftItem(item.materialId(),"item "+item.id()+" material");
            for(ItemAbility ability:item.abilities())if(SVFrameLib.inst().getSkills().getHandler(ability.skill())==null)throw new IllegalStateException("Item "+item.id()+" references unknown SVFrameLib skill "+ability.skill());
        }
        for(RecipeDefinition recipe:registry.recipes()) for(RecipeDefinition.Ingredient ingredient:recipe.ingredients())
            if(ingredient.kind()==RecipeDefinition.IngredientKind.VANILLA)requireMinecraftItem(ingredient.id(),"recipe "+recipe.id()+" ingredient");
        for(UpgradeTemplate template:registry.upgrades()) for(UpgradeTemplate.Cost cost:template.costs()) {
            if(!upgrades.hasCostProvider(cost.provider()))throw new IllegalStateException("Upgrade template "+template.id()+" references missing cost provider "+cost.provider());
            if("minecraft_item".equals(cost.provider()))requireMinecraftItem(cost.id(),"upgrade template "+template.id()+" cost");
        }
        for(LootTableDefinition table:registry.lootTables())for(LootTableDefinition.Entry entry:table.entries()){
            if(!loot.hasCondition(entry.conditionId()))throw new IllegalStateException("Loot table "+table.id()+" references missing condition provider "+entry.conditionId());
            if(!loot.hasReward(entry.rewardId()))throw new IllegalStateException("Loot table "+table.id()+" references missing reward provider "+entry.rewardId());
        }
    }
    private static void requireMinecraftItem(String value,String context){Identifier id=Identifier.tryParse(value);if(id==null||!Registries.ITEM.containsId(id))throw new IllegalStateException("Unknown Minecraft item "+value+" for "+context);}
}
