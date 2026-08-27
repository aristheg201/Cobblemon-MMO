package vn.svframe.svframeitems;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.*;
import vn.svframe.svframeitems.item.*;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GenerationPersistenceTest {
    private static final List<String> FILES=List.of("types.yml","rarities.yml","upgrades.yml","sets.yml","recipes.yml","loot.yml","items/examples.yml");
    @BeforeAll static void bootstrap(){SharedConstants.createGameVersion();Bootstrap.initialize();}
    @Test void generationIsDeterministicPersistentAndHonorsTypeStackLimit() throws Exception{
        Fixture f=fixture();ItemStack a=f.generator.generate("trailblazer_blade",new ItemGenerator.GenerationContext(10,42));ItemStack b=f.generator.generate("trailblazer_blade",new ItemGenerator.GenerationContext(10,42));
        ItemInstance ia=ItemCodec.read(a).orElseThrow(),ib=ItemCodec.read(b).orElseThrow();assertEquals(ia,ib);assertEquals(1,a.getMaxCount());assertEquals(64,f.generator.generate("ruby_gem",1).getMaxCount());
        ItemStack copy=a.copy();assertEquals(ia,ItemCodec.read(copy).orElseThrow());
        ItemRarity rarity=f.registry.rarity(ia.rarityId());double attack=ia.stats().stream().filter(stat->stat.stat().equals("ATTACK_DAMAGE")).findFirst().orElseThrow().value();assertTrue(attack>=6.35*rarity.statMultiplier()-1e-9&&attack<=9.35*rarity.statMultiplier()+1e-9);
    }
    @Test void upgradeAndSocketsPreserveMetadataAndForeignComponents() throws Exception{
        Fixture f=fixture();ItemStack blade=f.generator.generate("trailblazer_blade",new ItemGenerator.GenerationContext(10,7));assertTrue(ItemCodec.setMetadata(blade,"integration:owner","alpha"));blade.set(DataComponentTypes.DAMAGE,3);
        UpgradeService.Result upgraded=f.upgrades.attempt(blade,new SplittableRandom(1));assertTrue(upgraded.success());assertEquals(3,upgraded.item().get(DataComponentTypes.DAMAGE));assertEquals("alpha",ItemCodec.metadata(upgraded.item(),"integration:owner").orElseThrow());
        ItemStack gem=f.generator.generate("ruby_gem",new ItemGenerator.GenerationContext(10,11));ItemCodec.setMetadata(gem,"integration:origin","external");SocketService.InsertResult inserted=f.sockets.insert(upgraded.item(),gem);assertTrue(inserted.success());assertEquals(3,inserted.target().get(DataComponentTypes.DAMAGE));
        SocketService.UnsocketResult removed=f.sockets.unsocket(inserted.target(),inserted.socketIndex());assertTrue(removed.success());assertEquals("external",ItemCodec.metadata(removed.gem(),"integration:origin").orElseThrow());assertEquals("alpha",ItemCodec.metadata(removed.target(),"integration:owner").orElseThrow());
    }
    @Test void lootUsesCallerLevelContextAndExtensionHooks() throws Exception{
        Fixture f=fixture();List<ItemStack> high=f.loot.roll("starter_drops",99,new SplittableRandom(3));assertFalse(high.isEmpty());for(ItemStack stack:high)assertEquals(20,ItemCodec.read(stack).orElseThrow().itemLevel());
        f.registry.registerExternal(new LootTableDefinition("gated",1,List.of(new LootTableDefinition.Entry("trailblazer_blade",1,1,1,1,1,100,"integration_gate","item"))));
        try(AutoCloseable ignored=f.loot.registerCondition("integration_gate",(context,entry)->Boolean.TRUE.equals(context.attributes().get("allowed")))){
            assertTrue(f.loot.roll("gated",new LootService.Context(15,new SplittableRandom(4),Map.of("allowed",false))).isEmpty());
            assertEquals(15,ItemCodec.read(f.loot.roll("gated",new LootService.Context(15,new SplittableRandom(4),Map.of("allowed",true))).getFirst()).orElseThrow().itemLevel());
        }
        f.registry.registerExternal(new LootTableDefinition("custom_reward",1,List.of(new LootTableDefinition.Entry("opaque_payload",1,1,1,1,1,100,"always","integration_reward"))));
        try(AutoCloseable ignored=f.loot.registerReward("integration_reward",(context,entry,generator)->List.of(generator.generate("ruby_gem",context.level())))){
            assertEquals("ruby_gem",ItemCodec.read(f.loot.roll("custom_reward",10).getFirst()).orElseThrow().definitionId());
        }
    }
    @Test void zeroChanceLootNeverDrops() throws Exception{
        Fixture f=fixture();f.registry.registerExternal(new LootTableDefinition("never",1,List.of(new LootTableDefinition.Entry("trailblazer_blade",1,0,1,1,1,100))));
        for(int seed=0;seed<20;seed++)assertTrue(f.loot.roll("never",10,new SplittableRandom(seed)).isEmpty());
    }
    private static Fixture fixture() throws Exception{Path root=Files.createTempDirectory("svframeitems-runtime-test");for(String relative:FILES){Path target=root.resolve(relative);Files.createDirectories(target.getParent());try(InputStream input=GenerationPersistenceTest.class.getResourceAsStream("/default/"+relative)){assertNotNull(input);Files.copy(input,target);}}SVFrameItemsRegistry registry=new SVFrameItemsRegistry();registry.reload(root);ItemGenerator generator=new ItemGenerator(registry,new ItemFormatter());UpgradeService upgrades=new UpgradeService(registry,generator);return new Fixture(registry,generator,upgrades,new SocketService(registry,generator),new LootService(registry,generator));}
    private record Fixture(SVFrameItemsRegistry registry,ItemGenerator generator,UpgradeService upgrades,SocketService sockets,LootService loot){}
}
