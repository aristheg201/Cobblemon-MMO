package io.lumine.mythic.lib.util.config;
import io.lumine.mythic.lib.module.MMOPlugin;
import java.util.*;
public final class ConfigVersioner {
    private static final Runnable NOP=()->{};
    private ConfigVersioner(){}
    public static List<Runnable> nops(int amount,Runnable... migrations){
        ArrayList<Runnable> out=new ArrayList<>(Math.max(amount,migrations.length));
        Collections.addAll(out,migrations); while(out.size()<amount)out.add(NOP); return out;
    }
    public static void applyConfigVersioner(MMOPlugin plugin,List<Runnable> migrations){
        if(migrations==null)return; for(Runnable migration:migrations) if(migration!=null) migration.run();
    }
}
