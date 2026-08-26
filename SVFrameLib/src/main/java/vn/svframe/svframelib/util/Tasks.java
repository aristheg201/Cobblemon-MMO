package vn.svframe.svframelib.util;
import vn.svframe.svframelib.module.MMOPlugin;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;
import java.util.concurrent.*;
import java.util.function.Consumer;
public final class Tasks {
    private Tasks(){}
    public static CompletableFuture<Void> runAsync(MMOPlugin plugin,Runnable task){
        return CompletableFuture.runAsync(task).whenComplete((v,t)->{if(t!=null && plugin!=null)plugin.logger().warning("Async task failed: "+t);});
    }
    public static void runSync(MMOPlugin plugin,Runnable task){
        var server=MythicLibFabricMod.server(); if(server==null || server.isOnThread()) task.run(); else server.execute(task);
    }
    public static <T> Consumer<T> sync(MMOPlugin plugin,Consumer<T> consumer){return value->runSync(plugin,()->consumer.accept(value));}
    public static Runnable sync(MMOPlugin plugin,Runnable runnable){return ()->runSync(plugin,runnable);}
}
