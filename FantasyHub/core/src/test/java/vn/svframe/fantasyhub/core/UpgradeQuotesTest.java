package vn.svframe.fantasyhub.core;
import org.junit.jupiter.api.Test;import java.util.UUID;import static org.junit.jupiter.api.Assertions.*;
class UpgradeQuotesTest {
 @Test void refreshKeepsConfirmationButMutationInvalidatesIt(){var book=new UpgradeQuotes();var p=UUID.randomUUID();String first=book.issue(p,3,"item+0","rules-v1",100);assertEquals(first,book.issue(p,3,"item+0","rules-v1",200));String changed=book.issue(p,3,"item+1","rules-v1",300);assertNotEquals(first,changed);assertNull(book.take(p,first,301));assertNotNull(book.take(p,changed,301));}
 @Test void quotesAreSingleUsePlayerBoundAndExpire(){var book=new UpgradeQuotes();var p=UUID.randomUUID();String id=book.issue(p,2,"item","rules",10);assertNull(book.take(UUID.randomUUID(),id,11));assertNotNull(book.take(p,id,11));assertNull(book.take(p,id,12));id=book.issue(p,2,"item","rules",20);assertNull(book.take(p,id,120021));}
 @Test void openScreenLeaseKeepsCachedQuotesValidButNeverReusesAConsumedQuote(){var book=new UpgradeQuotes();var p=UUID.randomUUID();String id=book.issue(p,2,"item","rules",10);book.keepAlive(p,120_000);assertNotNull(book.take(p,id,200_000));assertNull(book.take(p,id,200_001));id=book.issue(p,2,"item","rules",200_000);book.clear(p);assertNull(book.take(p,id,200_001));}
}
