package vn.svframe.svframemobs.mythic;

import org.junit.jupiter.api.Test;
import vn.svframe.svframemobs.PotionParameters;
import static org.junit.jupiter.api.Assertions.*;

class PotionGraphTest {
    @Test void actualSourceAliasesDoNotTruncatePersistentBuffsOrChangeHiddenParticles() {
        var source=MythicLine.parse("potion{type=DAMAGE_RESISTANCE;duration=999999;lvl=10;hasParticles=false} @self");
        assertEquals("resistance",PotionParameters.type(source.value("type","")));
        assertEquals("999999",source.value("duration",""));
        assertEquals("10",source.value("level",""));assertEquals("false",source.value("hasparticles",""));
        var hidden=MythicLine.parse("potion{t=INVISIBILITY;d=200;l=1;p=false;repeat=2} @self");
        assertEquals("INVISIBILITY",hidden.value("type",""));assertEquals("false",hidden.value("hasparticles",""));
        assertEquals("2",hidden.value("repeat",""));
    }
    @Test void dynamicDurationRemainsAnExpressionButLiteralsAndFlagsCannotBeSilentlyClamped() {
        var dynamic=MythicLine.parse("potion{t=SLOW;d=\"<modifier.duration> * 20\";lvl=2;icon=false}");
        assertEquals("<modifier.duration> * 20",dynamic.value("duration",""));
        assertEquals(Integer.MAX_VALUE,PotionParameters.integer(Integer.MAX_VALUE,1,Integer.MAX_VALUE));
        for(String invalid:new String[]{"p=HEAL","d=NaN","l=256","d=20.5","l=-1","d=10;duration=20","ambient=maybe"})
            assertThrows(IllegalArgumentException.class,()->MythicLine.parse("potion{"+invalid+"}"),invalid);
        assertThrows(IllegalArgumentException.class,()->PotionParameters.integer(Double.POSITIVE_INFINITY,1,Integer.MAX_VALUE));
    }
}
