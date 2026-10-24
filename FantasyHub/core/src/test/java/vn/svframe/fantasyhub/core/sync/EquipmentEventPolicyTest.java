package vn.svframe.fantasyhub.core.sync;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.util.*;
class EquipmentEventPolicyTest {
 @Test void emptyContainerNotificationDoesNotInvalidateEveryTick(){assertFalse(EquipmentEventPolicy.containerChanged(Map.of()));assertTrue(EquipmentEventPolicy.containerChanged(Map.of("belt",true)));}
 @Test void equalStacksAreNotChangesButRealComponentMutationIs(){assertFalse(EquipmentEventPolicy.stackChanged(true));assertTrue(EquipmentEventPolicy.stackChanged(false));}
}
