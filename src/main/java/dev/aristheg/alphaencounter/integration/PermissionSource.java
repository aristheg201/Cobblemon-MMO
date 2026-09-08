package dev.aristheg.alphaencounter.integration;

import net.minecraft.server.network.ServerPlayerEntity;

public interface PermissionSource {
    boolean available();
    boolean hasPermission(ServerPlayerEntity player, String permission);
    String status();

    static PermissionSource unavailable(String reason) {
        return new PermissionSource() {
            @Override public boolean available() { return false; }
            @Override public boolean hasPermission(ServerPlayerEntity player, String permission) { return false; }
            @Override public String status() { return reason; }
        };
    }
}
