package vn.svframe.fantasyhub.core;
import org.junit.jupiter.api.Test;import java.util.concurrent.atomic.AtomicInteger;import static org.junit.jupiter.api.Assertions.*;
class PreviewCacheTest {
 @Test void repeatedOffersShareOneEncodingUntilRevisionChanges(){var c=new PreviewCache<String,String>(4);var n=new AtomicInteger();for(int i=0;i<1000;i++)assertEquals("native-1",c.get(1,"armor",()->"native-"+n.incrementAndGet()));assertEquals(1,n.get());assertEquals("native-2",c.get(2,"armor",()->"native-"+n.incrementAndGet()));}
 @Test void reloadAndBoundedEvictionCannotReturnOldPreviews(){var c=new PreviewCache<String,String>(2);c.get(1,"a",()->"old");c.clear();assertEquals("new",c.get(1,"a",()->"new"));c.get(1,"b",()->"b");c.get(1,"c",()->"c");assertEquals("reloaded",c.get(1,"a",()->"reloaded"));}
}
