package vn.svframe.fantasyhub.core;
import org.junit.jupiter.api.Test;import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class CommercePolicyTest {
 @Test void ownedSwitchIsFreeAfterExpiryEvenWhenBypassRequested(){assertEquals(BigDecimal.ZERO,CommercePolicy.switchCharge(true,false,true,new BigDecimal("500")));}
 @Test void earlySwitchRequiresExplicitBeastcoinBypass(){assertThrows(IllegalArgumentException.class,()->CommercePolicy.switchCharge(true,true,false,new BigDecimal("500")));assertEquals(new BigDecimal("500"),CommercePolicy.switchCharge(true,true,true,new BigDecimal("500")));}
 @Test void noSwitchForUnownedClass(){assertThrows(IllegalArgumentException.class,()->CommercePolicy.switchCharge(false,false,false,BigDecimal.ZERO));}
 @Test void itemShopRejectsFractionalOrUnderMinimumHuntercoin(){for(String s:new String[]{"0","0.5","1","2.5"})assertThrows(IllegalArgumentException.class,()->CommercePolicy.validateItemPrices(new BigDecimal("500"),new BigDecimal(s)));}
 @Test void itemShopRejectsBeastcoinOutsideRange(){for(String s:new String[]{"499","3001","500.5"})assertThrows(IllegalArgumentException.class,()->CommercePolicy.validateItemPrices(new BigDecimal(s),new BigDecimal("2")));}
 @Test void currenciesAreIndependent(){CommercePolicy.validateItemPrices(new BigDecimal("3000"),new BigDecimal("2"));assertEquals("beconomy:secondary",CommercePolicy.currency(1));assertEquals("beconomy:primary",CommercePolicy.currency(2));assertThrows(IllegalArgumentException.class,()->CommercePolicy.currency(3));}
}
