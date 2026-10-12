package vn.svframe.fantasyhub.core;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class ClassPricingTest {
 @Test void ordinaryDoesNotUsePrimary(){var p=ClassPricing.parse(Map.of()).price("PALADIN");assertEquals("beconomy:secondary",p.currency());assertEquals(new BigDecimal("3000"),p.amount());}
 @Test void bothPremiumClassesCostExactlyTwoHuntercoin(){for(String id:new String[]{"dragonborn","DEATH_KNIGHT"}){var p=ClassPricing.parse(Map.of()).price(id);assertEquals("beconomy:primary",p.currency());assertEquals(new BigDecimal("2"),p.amount());}}
 @Test void inclusiveBoundsAndPerClassPrices(){var p=ClassPricing.parse(Map.of("fee",1000,"class-prices",Map.of("mage",5000)));assertEquals(new BigDecimal("1000"),p.price("PALADIN").amount());assertEquals(new BigDecimal("5000"),p.price("MAGE").amount());}
 @Test void rejectsWrongCurrencyAndPrimary(){for(String c:new String[]{"beconomy:primary","cobbledollars","beconomy:beastcoin"})assertThrows(IllegalArgumentException.class,()->ClassPricing.parse(Map.of("currency",c)));}
 @Test void rejectsOutOfBoundsOrFractionalOrdinaryPrices(){for(Object n:new Object[]{999,5001,3000.5})assertThrows(IllegalArgumentException.class,()->ClassPricing.parse(Map.of("fee",n)));}
 @Test void premiumCannotBeOverriddenIntoOrdinaryPrice(){assertThrows(IllegalArgumentException.class,()->ClassPricing.parse(Map.of("premium-fee",3)));assertThrows(IllegalArgumentException.class,()->ClassPricing.parse(Map.of("class-prices",Map.of("DRAGONBORN",3000))));}
}
