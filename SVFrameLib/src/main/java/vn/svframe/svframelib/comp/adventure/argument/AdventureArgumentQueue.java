package vn.svframe.svframelib.comp.adventure.argument;
import java.util.*; import java.util.function.Supplier;
public final class AdventureArgumentQueue {
    private final List<AdventureArgument> args; private int ptr;
    public AdventureArgumentQueue(List<AdventureArgument> args){this.args=args==null?List.of():List.copyOf(args);}
    public AdventureArgument pop(){if(!hasNext())throw new NoSuchElementException("No more arguments");return args.get(ptr++);}
    public AdventureArgument popOr(String fallback){return hasNext()?pop():new AdventureArgument(fallback);}
    public AdventureArgument popOr(Supplier<String> fallback){return hasNext()?pop():new AdventureArgument(fallback.get());}
    public AdventureArgument peek(){if(!hasNext())throw new NoSuchElementException("No more arguments");return args.get(ptr);}
    public boolean hasNext(){return ptr<args.size();}
    public void reset(){ptr=0;}
    @Override public String toString(){return args.subList(Math.min(ptr,args.size()),args.size()).toString();}
}
