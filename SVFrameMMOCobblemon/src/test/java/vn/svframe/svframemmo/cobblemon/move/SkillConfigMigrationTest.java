package vn.svframe.svframemmo.cobblemon.move;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.svframe.svframemmo.cobblemon.config.IntegrationConfig.PokemonSkillShopConfig;
class SkillConfigMigrationTest {
 @Test void oldCheapDollarPricesAreMigratedAndBothOptionsExist(){var c=new PokemonSkillShopConfig();c.defaultPrice=2500;c.prices.put("lick",50d);c.prices.put("bite",750000d);c.validatePayments();assertEquals(2,c.pricingVersion);assertEquals(0,c.payment(1,"lick","").price().compareTo(java.math.BigDecimal.valueOf(100)));assertEquals(0,c.payment(2,"lick","").price().compareTo(java.math.BigDecimal.valueOf(500000)));assertEquals(0,c.payment(2,"bite","").price().compareTo(java.math.BigDecimal.valueOf(750000)));}
 @Test void reloadRejectsPricesBelowFloorInsteadOfSellingThem(){var c=new PokemonSkillShopConfig();c.pricingVersion=2;c.beastcoinPrices.put("lick",99d);assertThrows(IllegalArgumentException.class,c::validatePayments);}
 @Test void migrationIsIdempotentAndDoesNotCopyPrimaryIntoBeastcoin(){var c=new PokemonSkillShopConfig();c.economyProvider="beconomy";c.currency="primary";c.defaultPrice=100000;c.prices.put("lick",999999d);c.validatePayments();assertTrue(c.beastcoinPrices.isEmpty());c.beastcoinPrices.put("lick",250d);c.validatePayments();assertEquals(0,c.payment(1,"lick","").price().compareTo(java.math.BigDecimal.valueOf(250)));}
}
