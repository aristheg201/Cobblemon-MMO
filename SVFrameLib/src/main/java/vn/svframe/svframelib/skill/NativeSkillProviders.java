package vn.svframe.svframelib.skill;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Optional Fabric runtimes supply real skill execution without a Bukkit API. */
public final class NativeSkillProviders {
    public interface Provider {
        boolean available(String id);
        boolean cast(String id,UUID caster,UUID target,Map<String,?> parameters);
        default List<String> issues(String id){return available(id)?List.of():List.of("Unavailable skill "+id);}
        default void materialize(vn.svframe.svframelib.manager.SkillManager manager) { }
        default void reloadDefinitions() throws java.io.IOException { }
    }
    public interface BackedHandler {String providerNamespace();String sourceSkill();}
    private static final Map<String,Provider> PROVIDERS=new ConcurrentHashMap<>();
    private NativeSkillProviders(){}
    public static void register(String namespace,Provider provider){String key=namespace.toLowerCase(Locale.ROOT);if(PROVIDERS.putIfAbsent(key,Objects.requireNonNull(provider))!=null)throw new IllegalArgumentException("Duplicate skill provider "+key);}
    public static boolean available(String namespace,String id){Provider provider=PROVIDERS.get(namespace.toLowerCase(Locale.ROOT));return provider!=null&&provider.available(id);}
    public static boolean cast(String namespace,String id,UUID caster,UUID target,Map<String,?> parameters){Provider provider=PROVIDERS.get(namespace.toLowerCase(Locale.ROOT));return provider!=null&&provider.cast(id,caster,target,parameters);}
    public static List<String> issues(String namespace,String id){Provider provider=PROVIDERS.get(namespace.toLowerCase(Locale.ROOT));return provider==null?List.of("Missing native provider "+namespace):provider.issues(id);}
    public static void materialize(){var manager=vn.svframe.svframelib.SVFrameLib.inst().getSkills();new TreeMap<>(PROVIDERS).values().forEach(provider->provider.materialize(manager));}
    public static void reloadDefinitions() throws java.io.IOException {for(Provider provider:new TreeMap<>(PROVIDERS).values())provider.reloadDefinitions();}
}
