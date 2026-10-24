package vn.svframe.fantasyhub.core.sync;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.util.*;
import vn.svframe.fantasyhub.protocol.EquipmentPriority;
class EquipmentPriorityTest {
 @Test void itemPriorityPrecedesBackendPriorityAndPreservesFirstSlot(){
  assertEquals("zecter:trinkets",EquipmentPriority.first(List.of("zecter","agito"),List.of(List.of("agito:accessories"),List.of("zecter:trinkets","zecter:second")),s->s.split(":")[0],""));
  assertEquals("agito:accessories",EquipmentPriority.first(List.of("zecter","agito"),List.of(List.of("agito:accessories"),List.of("agito:trinkets")),s->s.split(":")[0],""));
 }
 @Test void eachBackendIsEvaluatedOnceAndLiveChangesAreNeverCached(){
  var rows=new ArrayList<>(List.of("agito"));var backends=List.of(rows);assertEquals("agito",EquipmentPriority.first(List.of("zecter","agito"),backends,s->s,""));rows.clear();assertEquals("",EquipmentPriority.first(List.of("zecter","agito"),backends,s->s,""));
 }
}
