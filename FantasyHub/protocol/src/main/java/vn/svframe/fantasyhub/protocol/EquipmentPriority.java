package vn.svframe.fantasyhub.protocol;
import java.util.*;import java.util.function.Function;
/** Item rank first, then backend order, then native slot order. No live state is retained. */
public final class EquipmentPriority {
 private EquipmentPriority(){}
 public static <I,S> S first(List<I> priority,Iterable<? extends Iterable<S>> backends,Function<S,I> item,S empty){
  int rank=priority.size();S chosen=empty;
  for(var backend:backends)for(S stack:backend){int next=priority.indexOf(item.apply(stack));if(next>=0&&next<rank){chosen=stack;rank=next;if(rank==0)return chosen;}}
  return chosen;
 }
}
