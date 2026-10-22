package vn.svframe.fantasyhub.core.hero;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class NpcSkillPolicyTest {
 @Test void configAcceptsShortAndFullOwnedSkillIds()throws Exception{var c=HeroCatalog.defaults();var list=NpcSkillPolicy.resolve(c,"THOR",List.of("MJOLNIR_THROW","THOR_BIFROST"));assertEquals(List.of("THOR_MJOLNIR_THROW","THOR_BIFROST"),list.stream().map(HeroCatalog.Ability::id).toList());}
 @Test void emptyPhaseRestoresActualDefaultRotation()throws Exception{var c=HeroCatalog.defaults();var custom=NpcSkillPolicy.resolve(c,"THOR",List.of("BIFROST"));var defaults=NpcSkillPolicy.resolve(c,"THOR",List.of());assertTrue(defaults.size()>custom.size());assertTrue(defaults.stream().anyMatch(a->a.key().equals("MJOLNIR_THROW")));}
 @Test void wrongClassUnknownAndTooManySkillsReject()throws Exception{var c=HeroCatalog.defaults();assertThrows(IllegalArgumentException.class,()->NpcSkillPolicy.resolve(c,"THOR",List.of("BATMAN_BATARANG")));assertThrows(IllegalArgumentException.class,()->NpcSkillPolicy.resolve(c,"THOR",List.of("NOPE")));assertThrows(IllegalArgumentException.class,()->NpcSkillPolicy.resolve(c,"THOR",Collections.nCopies(33,"BIFROST")));}
 @Test void recoveryStartsAfterWindupAndLastHitAcrossEverySkill()throws Exception{
  for(var a:HeroCatalog.defaults().abilities().values()){
   long cycle=NpcSkillPolicy.cycleTicks(a,40,100,.5);
   long last=a.phases().stream().mapToLong(p->(long)((Math.max(0,p.start())+(p.hits()-1)*p.interval())*20)).max().orElse(0);
   assertTrue(cycle>=40+last+50,a.id());
  }
 }
}
