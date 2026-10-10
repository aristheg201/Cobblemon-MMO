package vn.svframe.svframemobs;

import org.junit.jupiter.api.Test;
import vn.svframe.svframelib.config.YamlLite;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class MobConfigurationTest {
    @Test void separatesMechanicAndTargetParameterMaps() {
        SkillLine skill = SkillLine.parse("damage{amount=7;type=MAGIC} @PlayersInRadius{r=9} ~onTimer:40 0.5");
        assertEquals(7,skill.number("amount",0)); assertEquals(9,skill.radius()); assertEquals(40,skill.interval()); assertEquals(0.5,skill.chance());
        assertFalse(skill.parameters().containsKey("r"));
    }
    @Test void retainsQuotedMessagesAndRejectsMalformedSkills() {
        assertEquals("Come closer, hero!",SkillLine.parse("message{m=\"Come closer, hero!\"} @trigger ~onDamaged").value("m",""));
        assertThrows(IllegalArgumentException.class,() -> SkillLine.parse("damage{amount=NaN} @self ~onSpawn"));
        assertThrows(IllegalArgumentException.class,() -> SkillLine.parse("damage{} @self ~onTimer:0"));
        assertThrows(IllegalArgumentException.class,() -> SkillLine.parse("summon{} @self ~onSpawn"));
    }
    @Test void rejectsUnsupportedAndNonFiniteDefinitions() {
        assertThrows(IllegalArgumentException.class,() -> MobDefinition.parse("Mob",Map.of("Health",Double.NaN)));
        assertThrows(IllegalArgumentException.class,() -> MobDefinition.parse("Mob",Map.of("BossBar",true)));
        assertThrows(IllegalArgumentException.class,() -> MobDefinition.parse("Mob",Map.of("Options",Map.of("Invincible",true))));
    }
    @Test void loadsEquipmentDropsAndTriggersFromYaml() {
        var map = YamlLite.map(YamlLite.parse("Type: ZOMBIE\nHealth: 80\nEquipment:\n  - minecraft:iron_sword HAND\nDrops:\n  - minecraft:emerald 2 0.5\nSkills:\n  - damage{amount=3} @PlayersInRadius{r=5} ~onTimer:80\n"));
        var mob = MobDefinition.parse("StormSentinel",map);
        assertEquals(80,mob.health()); assertEquals("minecraft:iron_sword",mob.equipment().get("HAND")); assertEquals(2,mob.drops().getFirst().amount()); assertEquals(80,mob.skills().getFirst().interval());
    }
}
