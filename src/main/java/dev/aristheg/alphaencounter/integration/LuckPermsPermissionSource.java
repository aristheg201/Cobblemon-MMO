package dev.aristheg.alphaencounter.integration;

import dev.aristheg.alphaencounter.AlphaEncounterMod;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.server.network.ServerPlayerEntity;

public final class LuckPermsPermissionSource implements PermissionSource {
    private final LuckPerms api;
    private boolean warnedPermissionFailure;

    public LuckPermsPermissionSource() {
        this.api = LuckPermsProvider.get();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean hasPermission(ServerPlayerEntity player, String permission) {
        if (player == null || permission == null || permission.isBlank()) return false;
        try {
            User user = api.getUserManager().getUser(player.getUuid());
            return user != null && user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        } catch (Throwable t) {
            if (!warnedPermissionFailure) {
                warnedPermissionFailure = true;
                AlphaEncounterMod.LOGGER.warn("LuckPerms cached permission lookup failed; unmatched players will use the configured fallback spawn profile.", t);
            }
            return false;
        }
    }

    @Override
    public String status() {
        return "luckperms";
    }
}
