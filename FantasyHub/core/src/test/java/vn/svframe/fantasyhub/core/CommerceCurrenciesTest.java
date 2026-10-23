package vn.svframe.fantasyhub.core;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class CommerceCurrenciesTest {
 @Test void usesActualBeastCoinEvenWhenLegacyAliasAlsoExists(){assertEquals("beastcoin",CommerceCurrencies.beastId(Set.of("beastcoin","secondary","huntercoin")::contains));}
 @Test void acceptsExistingLegacySecondaryIdWithoutAssumingAnyOtherCurrency(){assertEquals("secondary",CommerceCurrencies.beastId(Set.of("secondary","huntercoin")::contains));assertEquals("beastcoin",CommerceCurrencies.beastId(Set.of("huntercoin","cobblecoins")::contains));}
}
