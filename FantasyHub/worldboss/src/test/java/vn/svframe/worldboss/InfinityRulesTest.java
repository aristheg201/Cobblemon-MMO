package vn.svframe.worldboss;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class InfinityRulesTest {
 @Test void allTenOutcomesAreReachableAndWeightsAreValidated(){int[] w={1,1,1,1,1,1,1,1,1,1};for(int i=0;i<10;i++)assertEquals(i,InfinityRules.select(w,i));assertThrows(IllegalArgumentException.class,()->InfinityRules.select(new int[]{1,-1},0));}
 @Test void guardianRoutingAndCooldown(){assertEquals("power",InfinityRules.guardianStone("THOR"));assertEquals("space",InfinityRules.guardianStone("FLASH"));assertEquals("reality",InfinityRules.guardianStone("COLLECTOR"));assertEquals("soul",InfinityRules.guardianStone("GAUNTLET_TRIAL"));assertEquals(1800000L,InfinityRules.SNAP_COOLDOWN);}
}
