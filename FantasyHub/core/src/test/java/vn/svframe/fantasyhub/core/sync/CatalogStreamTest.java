package vn.svframe.fantasyhub.core.sync;
import static org.junit.jupiter.api.Assertions.*;import org.junit.jupiter.api.Test;import java.util.*;
class CatalogStreamTest {
 @Test void acknowledgedCatalogReopenDoesNotRetransmitAndInvalidationRestarts(){
  var stream=new CatalogStream();CatalogStream.Source source=(start,limit)->Map.of("revision",1L,"total",1,"next",1,"rows",List.of(Map.of("id","one")));
  stream.next(source,8);stream.delivered();stream.reopen();assertFalse(stream.next((a,b)->{throw new AssertionError("unchanged catalog recaptured");},8).containsKey("pokemonShopPage"));
  stream.invalidate();assertTrue(stream.next(source,8).containsKey("pokemonShopPage"));
 }
 @Test void cancelledFinalPageMustReplayOnReopen(){
  var stream=new CatalogStream();CatalogStream.Source source=(start,limit)->Map.of("revision",1L,"total",1,"next",1,"rows",List.of(Map.of("id","one")));
  stream.next(source,8);stream.reopen();assertTrue(stream.next(source,8).containsKey("pokemonShopPage"));
 }
 @Test void slicesAreBoundedCachedAndReopenStartsAtZero(){
  var s=new CatalogStream();var calls=new java.util.concurrent.atomic.AtomicInteger();
  CatalogStream.Source source=(at,limit)->{calls.incrementAndGet();return Map.of("revision",1L,"total",3,"next",Math.min(3,at+limit),"rows",List.of(Map.of("id","r"+at)));};
  var first=s.next(source,1);assertEquals(0,((Map<?,?>)first.get("pokemonShopPage")).get("start"));assertEquals(true,first.get("pokemonCatalogLoading"));
  s.next(source,1);assertEquals(false,s.next(source,1).get("pokemonCatalogLoading"));assertEquals(3,calls.get());s.reopen();s.next(source,1);assertEquals(3,calls.get());
 }
 @Test void invalidationRestartsAndProviderRevisionCannotMixRows(){
  var s=new CatalogStream();var revision=new java.util.concurrent.atomic.AtomicLong(1);
  CatalogStream.Source source=(at,limit)->Map.of("revision",revision.get(),"total",3,"next",Math.min(3,at+1),"rows",List.of(Map.of("id",revision.get()+":"+at)));
  s.next(source,1);revision.incrementAndGet();var changed=s.next(source,1);assertEquals(0,((Map<?,?>)changed.get("pokemonShopPage")).get("start"));s.invalidate();assertEquals(0,((Map<?,?>)s.next(source,1).get("pokemonShopPage")).get("start"));
 }
}
