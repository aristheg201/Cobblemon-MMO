package vn.svframe.svframelib.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Optional native entity integrations shared by skills, scripted effects and encounters. */
public final class RpgEntityAdapters {
    public enum Effect { DAMAGE, HEAL, STATUS, MOVEMENT }
    public interface Adapter {
        boolean supports(LivingEntity target);
        boolean allows(Entity actor,LivingEntity target,Effect effect);
        default boolean allowsFrom(Entity actor,Effect effect){return true;}
        default boolean allowsFrom(Entity actor,LivingEntity target,Effect effect){return allowsFrom(actor,effect);}
        default void heal(LivingEntity target,float amount){target.heal(amount);}
        default Map<String,Object> describe(LivingEntity target){return Map.of();}
    }
    private static final Map<String,Adapter> ADAPTERS=new ConcurrentHashMap<>();
    private static volatile List<Adapter> ordered=List.of();
    private RpgEntityAdapters(){}
    public static synchronized void register(String owner,Adapter adapter){ADAPTERS.put(Objects.requireNonNull(owner),Objects.requireNonNull(adapter));ordered=List.copyOf(new TreeMap<>(ADAPTERS).values());}
    public static Adapter find(LivingEntity target){for(var entry:ordered)if(entry.supports(target))return entry;return null;}
    public static boolean allows(Entity actor,LivingEntity target,Effect effect){
        if(target==null||!target.isAlive()||target.isSpectator())return false;
        if(actor!=null&&actor.getWorld()!=target.getWorld())return false;
        for(var integration:ordered)if(!integration.allowsFrom(actor,target,effect))return false;
        boolean harmful=effect!=Effect.HEAL;
        if(harmful&&target instanceof ServerPlayerEntity player){
            if(player.isCreative())return false;
            if(actor instanceof ServerPlayerEntity attacker&&attacker!=player&&(!player.getServer().isPvpEnabled()||attacker.isTeammate(player)))return false;
        }
        var adapter=find(target);return adapter==null||adapter.allows(actor,target,effect);
    }
    public static boolean heal(Entity actor,LivingEntity target,double amount){
        if(!Double.isFinite(amount)||amount<=0||!allows(actor,target,Effect.HEAL))return false;
        var adapter=find(target);if(adapter==null)target.heal((float)Math.min(Float.MAX_VALUE,amount));else adapter.heal(target,(float)Math.min(Float.MAX_VALUE,amount));return true;
    }
}
