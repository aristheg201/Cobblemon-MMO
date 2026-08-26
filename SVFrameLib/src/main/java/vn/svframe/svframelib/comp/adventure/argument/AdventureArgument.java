package vn.svframe.svframelib.comp.adventure.argument;
import java.util.*;
public final class AdventureArgument {
    private final String value;
    public AdventureArgument(String value){this.value=value==null?"":value;}
    public String toLowerCase(){return value.toLowerCase(Locale.ROOT);}
    public String value(){return value;}
    public boolean isTrue(){return Boolean.parseBoolean(value);}
    public boolean isFalse(){return "false".equalsIgnoreCase(value);}
    public OptionalInt asInt(){try{return OptionalInt.of(Integer.parseInt(value));}catch(Exception e){return OptionalInt.empty();}}
    public OptionalDouble asDouble(){try{return OptionalDouble.of(Double.parseDouble(value));}catch(Exception e){return OptionalDouble.empty();}}
    @Override public String toString(){return value;}
}
