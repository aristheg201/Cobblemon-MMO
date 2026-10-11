package vn.svframe.svframelib.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class SourceYamlTest {
    @Test void foldedMechanicAndLaterSkillsAreNotTruncated(){
        var root=YamlLite.map(YamlLite.parse("""
            first:
              Skills:
              - aura{onTick=[ - particles{color=#450917;
                  amount=5} ];duration=130} @self
            second:
              Skills:
              - damage{a=12} @target
            """));
        assertEquals(2,root.size());assertEquals(List.of("aura{onTick=[ - particles{color=#450917; amount=5} ];duration=130} @self"),YamlLite.map(root.get("first")).get("Skills"));
    }
    @Test void duplicateIdsFailInsteadOfDiscardingDefinitions(){assertThrows(RuntimeException.class,()->YamlLite.parse("skill: 1\nskill: 2\n"));}
    @Test void numericSkillSlotsUseTheExistingStringKeyApi(){assertEquals("passive",YamlLite.map(YamlLite.parse("1: passive\n")).get("1"));}
}
