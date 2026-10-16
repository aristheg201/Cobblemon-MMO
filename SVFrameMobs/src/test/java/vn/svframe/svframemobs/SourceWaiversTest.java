package vn.svframe.svframemobs;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceWaiversTest {
    private final String missing="skill{s=NinjaDamageEvent;sync=true} @self ~onDamaged 1";
    private final String defined="skill{s=HauntingSlicingProjectile} ~onSpawn 1";
    @Test void removesOnlyTheAuthorizedAbsentReferenceAndPreservesTheOriginal(){
        var source=Map.<String,Object>of("Skills",List.of(missing,defined,"setAI{ai=false} @self ~onSpawn 1"),"Damage",10);
        var result=SourceWaivers.apply("WardenAttackDummy",source,name->false);
        assertEquals(List.of(defined,"setAI{ai=false} @self ~onSpawn 1"),result.get("Skills"));
        assertEquals(10,result.get("Damage"));assertEquals(3,((List<?>)source.get("Skills")).size());
    }
    @Test void doesNotRemoveAProvidedDefinitionOrWaiveOtherOwners(){
        var source=Map.<String,Object>of("Skills",List.of(missing,defined));
        assertSame(source,SourceWaivers.apply("WardenAttackDummy",source,name->true));
        assertSame(source,SourceWaivers.apply("AnotherBoss",source,name->false));
    }
    @Test void doesNotHideOtherUnavailableSkills(){
        var source=Map.<String,Object>of("Skills",List.of("skill{s=OtherMissing} @self ~onSpawn 1"));
        assertSame(source,SourceWaivers.apply("WardenAttackDummy",source,name->false));
    }
}
