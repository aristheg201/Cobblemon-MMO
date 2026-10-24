package vn.svframe.fantasyhub.core.sync;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class CaptureAdmissionTest {
 @Test void measuredBulkCostMustFitRemainingBudgetAndOutlierCannotStarveForever(){var a=new CaptureAdmission();a.record("profile",900_000);assertFalse(a.admit("profile",1_500_000,2_000_000));assertTrue(a.admit("profile",100_000,2_000_000));a.record("profile",100_000_000);assertTrue(a.admit("profile",0,2_000_000));}
 @Test void cheapSlicesCanRunAndIndependentScopesRetainSeparateCosts(){var a=new CaptureAdmission();a.record("profile",1_000_000);a.record("classes",50_000);assertTrue(a.admit("classes",1_600_000,2_000_000));assertFalse(a.admit("profile",1_600_000,2_000_000));assertFalse(a.admit("classes",2_000_000,2_000_000));}
}
