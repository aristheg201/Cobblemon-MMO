package vn.svframe.svframeitems;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import org.junit.jupiter.api.*;
import vn.svframe.svframeitems.item.*;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RecipeRuntimeTest {
    private static final List<String> FILES=List.of("types.yml","rarities.yml","upgrades.yml","sets.yml","recipes.yml","loot.yml","items/examples.yml");
    @BeforeAll static void bootstrap(){SharedConstants.createGameVersion();Bootstrap.initialize();}
    @Test void inventoryRecipeConsumesExactlyOnceAndGeneratesPersistentOutput() throws Exception{
        Fixture f=fixture();SimpleInventory inventory=new SimpleInventory(2);inventory.setStack(0,new ItemStack(Items.IRON_INGOT,12));inventory.setStack(1,new ItemStack(Items.DIAMOND,2));
        assertTrue(f.recipes.canCraft(inventory,"trailblazer_blade"));RecipeService.Result result=f.recipes.craft(inventory,"trailblazer_blade");assertTrue(result.success());assertTrue(inventory.getStack(0).isEmpty());assertTrue(inventory.getStack(1).isEmpty());assertEquals("trailblazer_blade",ItemCodec.read(result.output()).orElseThrow().definitionId());
        assertEquals(RecipeService.Status.MISSING_INGREDIENTS,f.recipes.craft(inventory,"trailblazer_blade").status());
    }
    @Test void vanillaIngredientNeverConsumesRpgItemWithSameMinecraftMaterial() throws Exception{
        Fixture f=fixture();f.registry.registerExternal(new ItemDefinition("rpg_ingot","miscellaneous","minecraft:iron_ingot","RPG Ingot",1,1,1,1,Map.of("common",1),List.of(),List.of(),null,null,List.of(),null));
        SimpleInventory inventory=new SimpleInventory(2);ItemStack rpg=f.generator.generate("rpg_ingot",1);rpg.setCount(12);inventory.setStack(0,rpg);inventory.setStack(1,new ItemStack(Items.DIAMOND,2));
        assertFalse(f.recipes.canCraft(inventory,"trailblazer_blade"));assertEquals(12,inventory.getStack(0).getCount());
    }
    private static Fixture fixture() throws Exception{Path root=Files.createTempDirectory("svframeitems-recipe-test");for(String relative:FILES){Path target=root.resolve(relative);Files.createDirectories(target.getParent());try(InputStream input=RecipeRuntimeTest.class.getResourceAsStream("/default/"+relative)){assertNotNull(input);Files.copy(input,target);}}SVFrameItemsRegistry registry=new SVFrameItemsRegistry();registry.reload(root);ItemGenerator generator=new ItemGenerator(registry,new ItemFormatter());return new Fixture(registry,generator,new RecipeService(registry,generator));}
    private record Fixture(SVFrameItemsRegistry registry,ItemGenerator generator,RecipeService recipes){}
}
