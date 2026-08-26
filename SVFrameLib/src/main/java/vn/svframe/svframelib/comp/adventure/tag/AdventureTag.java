package vn.svframe.svframelib.comp.adventure.tag;
import vn.svframe.svframelib.comp.adventure.resolver.AdventureTagResolver;
import java.util.*;
public abstract class AdventureTag {
    private final String name; private final List<String> aliases; private final AdventureTagResolver resolver; private final boolean backwardsCompatible,color;
    public AdventureTag(String name,AdventureTagResolver resolver,boolean backwardsCompatible,boolean color,String...aliases){
        this.name=Objects.requireNonNull(name);this.resolver=Objects.requireNonNull(resolver);this.backwardsCompatible=backwardsCompatible;this.color=color;this.aliases=aliases==null?List.of():List.of(aliases);
    }
    public AdventureTagResolver resolver(){return resolver;} public String name(){return name;} public boolean backwardsCompatible(){return backwardsCompatible;} public List<String> aliases(){return aliases;} public boolean color(){return color;}
}
