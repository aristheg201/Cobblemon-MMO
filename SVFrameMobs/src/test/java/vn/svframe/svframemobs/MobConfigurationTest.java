package vn.svframe.svframemobs;

import org.junit.jupiter.api.Test;
import vn.svframe.svframelib.config.YamlLite;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class MobConfigurationTest {
    @Test void sourcePotionAliasesPreserveDurationAmplifierAndHiddenParticles(){
        var skill=SkillLine.parse("potion{type=SLOW;d=999999;l=10;hasParticles=false} @self ~onSpawn 1");
        assertEquals("slowness",skill.potionType());assertEquals(999999,skill.number("duration",0));assertEquals(10,skill.number("level",0));assertFalse(skill.flag("hasparticles",true));
        var shortAliases=SkillLine.parse("potion{t=HEAL;d=20;lvl=2;p=false} @target ~onTimer:20");
        assertEquals("instant_health",shortAliases.potionType());assertEquals(2,shortAliases.number("level",0));
        assertFalse(shortAliases.flag("hasparticles",true));
    }
    @Test void rejectsPotionValuesInsteadOfClampingOrIgnoringConfiguration(){
        for(String values:java.util.List.of("d=NaN","d=-1","d=2147483648","l=256","hasParticles=maybe","unsupported=true","d=10;duration=20"))assertThrows(IllegalArgumentException.class,()->SkillLine.parse("potion{"+values+"} @self ~onSpawn"),values);
    }
    @Test void parsesPokemonActorsAndSpatialEntityTargetsWithoutLosingSpeciesProperties(){
        var mob=MobDefinition.parse("StormPikachu",YamlLite.map(YamlLite.parse("Type: POKEMON\nPokemon: pikachu level=50 shiny\nSkills:\n  - damage{amount=7} @EIR{r=6} ~onTimer:40\n")));
        assertEquals("pikachu level=50 shiny",mob.pokemon());assertEquals("eir",mob.skills().getFirst().target());assertEquals(6,mob.skills().getFirst().radius());
    }
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
