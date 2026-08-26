package io.lumine.mythic.lib.comp.placeholder.api;
import io.lumine.mythic.lib.module.MMOPlugin; import net.minecraft.server.network.ServerPlayerEntity; import java.util.*;
public abstract class PluginPlaceholderExpansion<T> {
    protected static final String NO_PLAYER_PLACEHOLDER=""; protected static final String PLACEHOLDER_NOT_FOUND="";
    private final MMOPlugin owner; private final String identifier; private final Map<String,PlaceholderEntry<T>> byId=new LinkedHashMap<>();
    public PluginPlaceholderExpansion(MMOPlugin owner){this.owner=Objects.requireNonNull(owner);this.identifier=owner.getNamespacedKey();for(PlaceholderEntry<T> e:getPlaceholderRegistry())byId.put(norm(e.getPrefix()),e);}
    protected abstract Iterable<PlaceholderEntry<T>> getPlaceholderRegistry();
    protected abstract T getPlayerData(ServerPlayerEntity player);
    public boolean persist(){return true;} public boolean canRegister(){return true;}
    public String getAuthor(){return "SVFrameLib";} public String getIdentifier(){return identifier;} public String getVersion(){return "1.7.1-fabric";} public MMOPlugin getOwner(){return owner;}
    public String onRequest(ServerPlayerEntity player,String params){if(player==null)return NO_PLAYER_PLACEHOLDER;return parse(getPlayerData(player),params);}
    private String parse(T data,String input){if(input==null)return PLACEHOLDER_NOT_FOUND;String n=norm(input);for(var e:byId.entrySet())if(n.equals(e.getKey())||n.startsWith(e.getKey()+"_")){try{String v=e.getValue().parse(new PlaceholderMetadata<>(data,input,e.getKey().length()));return v==null?e.getValue().getFallback():v;}catch(RuntimeException ex){return e.getValue().getFallback();}}return PLACEHOLDER_NOT_FOUND;}
    private static String norm(String s){return s.toLowerCase(Locale.ROOT);}
}
