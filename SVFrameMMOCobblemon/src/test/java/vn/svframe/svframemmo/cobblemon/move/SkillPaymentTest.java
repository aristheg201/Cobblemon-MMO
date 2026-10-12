package vn.svframe.svframemmo.cobblemon.move;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.Map;
class SkillPaymentTest {
 @Test void distinctCurrencyQuotes(){var beast=SkillPayment.quote(1,null,100);var dollars=SkillPayment.quote(2,null,500000);assertEquals("secondary",beast.currency());assertEquals("beconomy",beast.provider());assertEquals("cobbledollars",dollars.provider());assertEquals(new BigDecimal("1E+2"),beast.price());assertEquals(0,dollars.price().compareTo(new BigDecimal("500000")));}
 @Test void minimumPricesAreEnforcedForEveryOverride(){assertThrows(IllegalArgumentException.class,()->SkillPayment.quote(1,99d,100));assertThrows(IllegalArgumentException.class,()->SkillPayment.quote(2,499999d,500000));}
 @Test void rejectsUnknownClientOptionAndInvalidAmounts(){assertThrows(IllegalArgumentException.class,()->SkillPayment.quote(9,null,100));assertThrows(IllegalArgumentException.class,()->SkillPayment.quote(1,100.5,100));assertThrows(IllegalArgumentException.class,()->SkillPayment.quote(2,Double.NaN,500000));}
 @Test void pricesAreIndependentAndCanonicalKeysWork(){assertEquals(150d,SkillPayment.override(Map.of("COBBLEMON_MOVE_LICK",150d),"lick","COBBLEMON_MOVE_LICK"));assertEquals(1000000d,SkillPayment.override(Map.of("Bite",1000000d),"bite","COBBLEMON_MOVE_BITE"));assertNull(SkillPayment.override(Map.of("bite",200d),"lick","COBBLEMON_MOVE_LICK"));}
}
