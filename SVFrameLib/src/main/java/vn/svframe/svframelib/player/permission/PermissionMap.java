package vn.svframe.svframelib.player.permission;

import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.fabric.SVFrameLibPermissionBridge;
import vn.svframe.svframelib.player.modifier.ModifierMap;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Native equivalent of a transient permission attachment.
 *
 * SVFrame owns the transient grant lifecycle. LuckPerms synchronization is
 * optional and handled reflectively by SVFrameLibPermissionBridge so the core
 * RPG runtime also works in integrated singleplayer without LuckPerms classes.
 */
public class PermissionMap extends ModifierMap<PermissionModifier> {
    private final Set<String> grantedPermissions = new HashSet<>();

    public PermissionMap(MMOPlayerData playerData) { super(playerData); }

    @Override
    protected void onSessionOpen() { }

    @Override
    protected void onSessionClose() {
        SVFrameLibPermissionBridge.clearTransient(getPlayerData().getUniqueId());
        grantedPermissions.clear();
    }

    @Override
    public PermissionModifier addModifier(PermissionModifier modifier) {
        PermissionModifier previous = super.addModifier(modifier);
        if (previous != null) take(previous.getPermission());
        give(modifier.getPermission());
        return previous;
    }

    @Override
    public PermissionModifier removeModifier(UUID uniqueId) {
        PermissionModifier previous = super.removeModifier(uniqueId);
        if (previous != null) take(previous.getPermission());
        return previous;
    }

    private void give(String permission) {
        if (permission == null || permission.isBlank()) return;
        grantedPermissions.add(permission);
        SVFrameLibPermissionBridge.grantTransient(getPlayerData().getUniqueId(), permission);
    }

    private void take(String permission) {
        if (permission == null || permission.isBlank()) return;
        grantedPermissions.remove(permission);
        SVFrameLibPermissionBridge.revokeTransient(getPlayerData().getUniqueId(), permission);
    }
}
