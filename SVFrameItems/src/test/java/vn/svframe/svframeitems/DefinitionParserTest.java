package vn.svframe.svframeitems;

import org.junit.jupiter.api.Test;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframeitems.config.*;
import vn.svframe.svframeitems.model.*;

import static org.junit.jupiter.api.Assertions.*;

class DefinitionParserTest {
    @Test void parsesUsefulItemDefinitionWithoutLegacySchema(){
        var root=YamlLite.map(YamlLite.parse("""
                blade:
                  type: sword
                  material: minecraft:iron_sword
                  level: 10
                  min-level: 1
                  max-level: 100
                  rarity: rare
                  sockets: [red, any]
                  stats:
                    ATTACK_DAMAGE:
                      min: 5
                      max: 8
                      per-level: 0.2
                      type: FLAT
                """));
        ItemDefinition item=DefinitionParser.item("blade",ConfigValues.map(root.get("blade")));
        assertEquals("sword",item.typeId());assertEquals(2,item.sockets().size());assertEquals(1,item.stats().size());assertEquals("rare",item.rarityWeights().keySet().iterator().next());
    }
    @Test void parsesRarityUpgradeCostsAndLootExtensions(){
        var rarity=DefinitionParser.rarity("rare",ConfigValues.map(YamlLite.map(YamlLite.parse("rare:\n  stat-multiplier: 1.25\n")).get("rare")));
        assertEquals(1.25,rarity.statMultiplier(),1e-9);
        var upgrade=DefinitionParser.upgrade("paid",ConfigValues.map(YamlLite.map(YamlLite.parse("""
                paid:
                  max-level: 3
                  base-success: 75
                  costs:
                    - provider: minecraft_item
                      id: minecraft:diamond
                      amount: 2
                      per-level: 1
                """)).get("paid")));
        assertEquals(1,upgrade.costs().size());assertEquals(4,upgrade.costs().getFirst().amountForNextLevel(2));
        var loot=DefinitionParser.loot("boss",ConfigValues.map(YamlLite.map(YamlLite.parse("""
                boss:
                  entries:
                    - item: external_reward_key
                      condition: integration_gate
                      reward: integration_reward
                """)).get("boss")));
        assertEquals("integration_gate",loot.entries().getFirst().conditionId());assertEquals("integration_reward",loot.entries().getFirst().rewardId());
    }
    @Test void rejectsDuplicateItemCooldownRuntime(){
        var section=ConfigValues.map(YamlLite.map(YamlLite.parse("""
                blade:
                  type: sword
                  material: minecraft:iron_sword
                  abilities:
                    - trigger: USE
                      skill: slash
                      cooldown-ticks: 20
                """)).get("blade"));
        IllegalArgumentException error=assertThrows(IllegalArgumentException.class,()->DefinitionParser.item("blade",section));
        assertTrue(error.getMessage().contains("SVFrameLib"));
    }
}
