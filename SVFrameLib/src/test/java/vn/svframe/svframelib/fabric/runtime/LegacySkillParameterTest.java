package vn.svframe.svframelib.fabric.runtime;

import org.junit.jupiter.api.Test;
import vn.svframe.svframelib.fabric.runtime.skill.LegacySkillDefinition;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LegacySkillParameterTest {
    private final LegacySkillDefinition skill = LegacySkillDefinition.from("HEAL", Map.of("source", "default:HEAL",
            "parameters", Map.of("heal", Map.of("player", 10, "item", 4))));
    @Test void classValuesAreCompleteAndDoNotGetTheFormulaAddedAgain() {
        assertEquals(15d, skill.resolveParameters(Map.of("heal", 15d)).get("heal"));
    }
    @Test void itemAndPlayerDefaultsAreSeparateContexts() {
        assertEquals(10d, skill.resolveParameters(Map.of()).get("heal"));
        assertEquals(4d, skill.resolveParameters(Map.of("item_id", "wand")).get("heal"));
        assertEquals(7d, skill.resolveParameters(Map.of("item_id", "wand", "heal", 7d)).get("heal"));
    }
}
