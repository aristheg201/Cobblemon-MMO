package vn.svframe.svframemmo.cobblemon.integration;

import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

/** Optional LuckPerms permission bridge. User-facing fusion permissions default to allowed when unset. */
public final class LuckPermsIntegration {
    public static final String DANCE = "svframemmo.cobblemon.fusion.dance";
    public static final String POTARA = "svframemmo.cobblemon.fusion.potara";
    public static final String POTARA_BASIC = POTARA + ".basic";
    public static final String POTARA_LEVEL2 = POTARA + ".level2";
    public static final String POTARA_ADVANCEMENT = POTARA + ".advancement";
    public static final String POTARA_GOD = POTARA + ".god";
    public static final String UNFUSE = "svframemmo.cobblemon.fusion.unfuse";

    private static volatile Backend backend = Backend.DEFAULT;
    private LuckPermsIntegration() { }

    public static void initialize() {
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
            SVFrameMMOCobblemon.LOG.info("LuckPerms not present; fusion permissions use default-allowed policy");
            return;
        }
        try {
            backend = new LuckPermsBackend();
            SVFrameMMOCobblemon.LOG.info("LuckPerms fusion permission bridge enabled");
        } catch (Throwable error) {
            backend = Backend.DEFAULT;
            SVFrameMMOCobblemon.LOG.warn("LuckPerms was detected but its API was not ready; using default-allowed fusion permissions", error);
        }
    }

    public static boolean has(ServerPlayerEntity player, String node) { return backend.has(player, node); }

    private interface Backend {
        Backend DEFAULT = (player, node) -> true;
        boolean has(ServerPlayerEntity player, String node);
    }

    private static final class LuckPermsBackend implements Backend {
        private final LuckPerms api = LuckPermsProvider.get();
        @Override public boolean has(ServerPlayerEntity player, String node) {
            User user = api.getUserManager().getUser(player.getUuid());
            if (user == null) return true;
            Tristate value = user.getCachedData().getPermissionData().checkPermission(node);
            return value == Tristate.UNDEFINED || value.asBoolean();
        }
    }
}
