package vn.svframe.svframelib.entity;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Native integrations construct entities through their actual mod APIs. */
public final class NativeMobFactories {
    public interface Factory { void validate(String properties); MobEntity create(ServerWorld world,String properties); }
    private static final Map<String,Factory> FACTORIES=new ConcurrentHashMap<>();
    private NativeMobFactories(){}
    public static void register(String id,Factory factory){if(id==null||factory==null)throw new IllegalArgumentException("Factory needs an ID and implementation");FACTORIES.put(id,factory);}
    public static Factory get(String id){return FACTORIES.get(id);}
}
