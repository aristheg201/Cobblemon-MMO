package vn.svframe.svframeitems;

import org.junit.jupiter.api.Test;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframeitems.config.DefinitionParser;
import vn.svframe.svframeitems.model.ItemDefinition;

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
        ItemDefinition item=DefinitionParser.item("blade",vn.svframe.svframeitems.config.ConfigValues.map(root.get("blade")));
        assertEquals("sword",item.typeId());assertEquals(2,item.sockets().size());assertEquals(1,item.stats().size());assertEquals("rare",item.rarityWeights().keySet().iterator().next());
    }
}
