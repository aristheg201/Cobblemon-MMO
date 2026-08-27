package vn.svframe.svframeitems;

import org.junit.jupiter.api.Test;
import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RegistryReloadTest {
    private static final List<String> FILES=List.of("types.yml","rarities.yml","upgrades.yml","sets.yml","recipes.yml","loot.yml","items/examples.yml");
    @Test void failedReloadKeepsLastGoodSnapshot() throws Exception{
        Path root=defaults();SVFrameItemsRegistry registry=new SVFrameItemsRegistry();registry.reload(root);long revision=registry.revision();ItemDefinition original=registry.item("trailblazer_blade");
        Files.writeString(root.resolve("items/examples.yml"),"broken:\n  type: does_not_exist\n  material: minecraft:stone\n  rarity: common\n");
        assertThrows(IllegalStateException.class,()->registry.reload(root));
        assertSame(original,registry.item("trailblazer_blade"));assertEquals(revision,registry.revision());
    }
    @Test void externalRegistrationsAreImmediatelyVisibleAndStrictlyValidatable() throws Exception{
        Path root=defaults();SVFrameItemsRegistry registry=new SVFrameItemsRegistry();registry.reload(root);long before=registry.revision();
        ItemType type=new ItemType("integration_relic",NativeStatEngine.ModifierSource.OTHER,Set.of(NativeStatEngine.EquipmentSlot.ACCESSORY),1);registry.registerExternal(type);
        registry.registerExternal(new ItemRarity("integration", "Integration",1,100,1.5));
        ItemDefinition item=new ItemDefinition("external_charm","integration_relic","minecraft:stone","External Charm",1,1,1,1,Map.of("integration",1),List.of(),List.of(),null,null,List.of(),null);
        registry.registerExternal(item);
        assertSame(item,registry.item("external_charm"));assertTrue(registry.revision()>=before+3);assertDoesNotThrow(registry::validateSnapshot);
    }

    @Test void explicitSnapshotRestoreRollsBackPostReloadFailures() throws Exception{
        Path root=defaults();SVFrameItemsRegistry registry=new SVFrameItemsRegistry();registry.reload(root);
        var before=registry.snapshot();long revision=registry.revision();ItemDefinition original=registry.item("trailblazer_blade");
        registry.registerExternal(new ItemRarity("temporary","Temporary",1,0));
        assertNotNull(registry.rarity("temporary"));assertTrue(registry.revision()>revision);
        registry.restore(before);
        assertNull(registry.rarity("temporary"));assertSame(original,registry.item("trailblazer_blade"));assertEquals(revision,registry.revision());
    }
    private static Path defaults() throws Exception{Path root=Files.createTempDirectory("svframeitems-registry-test");for(String relative:FILES){Path target=root.resolve(relative);Files.createDirectories(target.getParent());try(InputStream input=RegistryReloadTest.class.getResourceAsStream("/default/"+relative)){assertNotNull(input);Files.copy(input,target);}}return root;}
}
