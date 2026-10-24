package vn.svframe.fantasyhub.client;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import com.google.gson.*;
class ShopIndexTest {
 @Test void walletPatchesReuseListsAndCatalogReplacementReindexes(){var shop=JsonParser.parseString("{\"offers\":[{\"category\":\"classes\"}]}").getAsJsonObject();var index=new ShopView.Index();var first=index.rows(shop,ShopView.Tab.CLASS);shop.addProperty("beastBalance",123);assertSame(first,index.rows(shop,ShopView.Tab.CLASS));shop.add("offers",new JsonArray());assertTrue(index.rows(shop,ShopView.Tab.CLASS).isEmpty());}
 @Test void ellipsisUsesLogarithmicMeasurements(){var calls=new java.util.concurrent.atomic.AtomicInteger();var result=ShopView.fit("あ".repeat(1000),20,s->{calls.incrementAndGet();return s.length();});assertTrue(result.endsWith("…"));assertEquals(20,result.length());assertTrue(calls.get()<20,"measure calls="+calls.get());}
}
