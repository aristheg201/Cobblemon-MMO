package vn.svframe.svframelib.fabric;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Optional native integrations for original script command aliases and class policy presentation. */
public final class ScriptCompatibilityHooks {
    private ScriptCompatibilityHooks(){}
    private static final Map<String,Consumer<ServerPlayerEntity>> MENUS=new HashMap<>();
    public interface ClassPolicy {boolean request(ServerPlayerEntity player,String classId);Map<String,Object> details(ServerPlayerEntity player);default boolean selectable(String classId){return true;}}
    private static ClassPolicy classPolicy;
    public static void registerMenu(String id,Consumer<ServerPlayerEntity> menu){MENUS.put(id,Objects.requireNonNull(menu));}
    public static boolean openMenu(String id,ServerPlayerEntity player){var menu=MENUS.get(id);if(menu==null)return false;menu.accept(player);return true;}
    public static void classPolicy(ClassPolicy policy){classPolicy=policy;}
    public static ClassPolicy classPolicy(){return classPolicy;}
}
