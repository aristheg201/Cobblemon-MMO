package vn.svframe.svframeitems;

import org.junit.jupiter.api.Test;
import vn.svframe.svframeitems.item.*;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GenerationPersistenceTest {
    private static final List<String> FILES=List.of("types.yml","rarities.yml","upgrades.yml","sets.yml","recipes.yml","loot.yml","items/examples.yml");

    @Test void generationStateIsDeterministicPersistentAndRarityScaled() throws Exception {
        SVFrameItemsRegistry registry=registry();ItemRoller roller=new ItemRoller(registry);
        ItemInstance a=roller.roll("trailblazer_blade",10,42),b=roller.roll("trailblazer_blade",10,42);
        assertEquals(a,b);
        ItemRarity rarity=registry.rarity(a.rarityId());
        double attack=a.stats().stream().filter(stat->stat.stat().equals("ATTACK_DAMAGE")).findFirst().orElseThrow().value();
        assertTrue(attack>=6.35*rarity.statMultiplier()-1e-9&&attack<=9.35*rarity.statMultiplier()+1e-9);
        var encoded=ItemStateNbtCodec.encode(a.withMetadata("integration:owner","alpha"));
        ItemInstance decoded=ItemStateNbtCodec.decode(encoded).orElseThrow();
        assertEquals(a.withMetadata("integration:owner","alpha"),decoded);
        assertEquals(decoded,ItemStateNbtCodec.decode(encoded.copy()).orElseThrow());
    }

    @Test void socketAndGemMetadataRoundTripThroughPersistentState() throws Exception {
        SVFrameItemsRegistry registry=registry();ItemRoller roller=new ItemRoller(registry);
        ItemInstance blade=roller.roll("trailblazer_blade",10,7).withMetadata("integration:owner","alpha");
        ItemInstance gem=roller.roll("ruby_gem",10,11).withMetadata("integration:origin","external");
        EmbeddedGem embedded=EmbeddedGem.from(gem,registry.item("ruby_gem").gemColor());
        List<SocketState> sockets=new ArrayList<>(blade.sockets());assertTrue(sockets.getFirst().accepts(embedded.color()));sockets.set(0,sockets.getFirst().insert(embedded));
        ItemInstance socketed=blade.withSockets(sockets).withUpgradeLevel(1);
        ItemInstance decoded=ItemStateNbtCodec.decode(ItemStateNbtCodec.encode(socketed)).orElseThrow();
        assertEquals("alpha",decoded.metadata().get("integration:owner"));assertEquals("external",decoded.sockets().getFirst().gem().metadata().get("integration:origin"));assertEquals(1,decoded.upgradeLevel());
    }

    @Test void contextualLootPlanningClampsLevelAndZeroChanceNeverDrops() throws Exception {
        SVFrameItemsRegistry registry=registry();LootTableDefinition table=registry.lootTable("starter_drops");
        List<LootPlanner.Roll> high=LootPlanner.plan(table,99,new SplittableRandom(3),entry->true);assertFalse(high.isEmpty());for(var roll:high)assertEquals(20,roll.itemLevel());
        LootTableDefinition never=new LootTableDefinition("never",1,List.of(new LootTableDefinition.Entry("trailblazer_blade",1,0,1,1,1,100)));
        for(int seed=0;seed<20;seed++)assertTrue(LootPlanner.plan(never,10,new SplittableRandom(seed),entry->true).isEmpty());
        assertTrue(LootPlanner.plan(table,10,new SplittableRandom(1),entry->false).isEmpty());
    }

    private static SVFrameItemsRegistry registry() throws Exception {
        Path root=Files.createTempDirectory("svframeitems-persistence-test");
        for(String relative:FILES){Path target=root.resolve(relative);Files.createDirectories(target.getParent());try(InputStream input=GenerationPersistenceTest.class.getResourceAsStream("/default/"+relative)){assertNotNull(input);Files.copy(input,target);}}
        SVFrameItemsRegistry registry=new SVFrameItemsRegistry();registry.reload(root);return registry;
    }
}
