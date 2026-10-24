package vn.svframe.fantasyhub.core.sync;
import java.util.*;
/** Main-thread paged capture; workers receive only frozen rows. No player/provider is retained. */
public final class CatalogStream {
 @FunctionalInterface public interface Source{Map<String,Object> page(int start,int limit);}
 private final List<FrozenValues.Shared> cached=new ArrayList<>();
 private long revision=-1;private int cursor,total;private boolean complete;
 public void invalidate(){cached.clear();cursor=total=0;revision=-1;complete=false;}
 public void reopen(){cursor=0;if(!complete)invalidate();}
 public Map<String,Object> next(Source source,int limit){
  if(limit<1||limit>32)throw new IllegalArgumentException("Invalid catalog page size");
  int start=cursor;List<FrozenValues.Shared> rows;
  if(complete){int end=Math.min(total,start+limit);rows=List.copyOf(cached.subList(start,end));cursor=end;}
  else {
   Map<String,Object> page=source.page(start,limit);
   long incoming=((Number)page.get("revision")).longValue();
   if(revision>=0&&revision!=incoming){invalidate();start=0;page=source.page(0,limit);incoming=((Number)page.get("revision")).longValue();}
   int end=((Number)page.get("next")).intValue(),size=((Number)page.get("total")).intValue();
   var raw=(List<?>)page.get("rows");
   if(size<0||size>4096||end<start||end>size||end-start>limit||raw.size()!=end-start||(end==start&&end<size))throw new IllegalArgumentException("Invalid provider catalog page");
   revision=incoming;total=size;rows=new ArrayList<>(raw.size());for(Object row:raw)rows.add(FrozenValues.share(row));cached.addAll(rows);cursor=end;complete=end==total;
  }
  return Map.of("pokemonShopPage",Map.of("start",start,"rows",rows,"total",total),"pokemonCatalogLoading",cursor<total,"pokemonCatalogTotal",total);
 }
}
