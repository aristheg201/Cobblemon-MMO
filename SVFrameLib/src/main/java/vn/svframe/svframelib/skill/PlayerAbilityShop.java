package vn.svframe.svframelib.skill;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;

/** Optional shop bridge: catalog is presentation data; purchases remain owned by the integration. */
public interface PlayerAbilityShop {
    List<Map<String,Object>> catalog(ServerPlayerEntity player);
    void purchase(ServerPlayerEntity player,String id);
    static void install(PlayerAbilityShop shop){Holder.shop=Objects.requireNonNull(shop);}
    static PlayerAbilityShop get(){return Holder.shop;}
    final class Holder { private Holder(){} private static volatile PlayerAbilityShop shop; }
}
