package io.lumine.mythic.lib.player.permission;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.modifier.ModifierMap;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fabric permission lifecycle backed by LuckPerms transient user data.
 * Bukkit PermissionAttachment semantics are transient: modifiers are granted for
 * the open player session and are removed when the session closes.
 */
public class PermissionMap extends ModifierMap<PermissionModifier> {
    private final Map<String, Node> grantedNodes = new HashMap<>();

    public PermissionMap(MMOPlayerData playerData) {
        super(playerData);
    }

    @Override
    protected void onSessionOpen() {
        // MythicLib 1.7.1 intentionally performs no eager permission attachment work here.
    }

    @Override
    protected void onSessionClose() {
        User user = user();
        if (user != null) {
            for (Node node : grantedNodes.values()) {
                user.getTransientData().remove(node);
            }
        }
        grantedNodes.clear();
    }

    @Override
    public PermissionModifier addModifier(PermissionModifier modifier) {
        PermissionModifier previous = super.addModifier(modifier);
        if (previous != null) {
            take(previous.getPermission());
        }
        give(modifier.getPermission());
        return previous;
    }

    @Override
    public PermissionModifier removeModifier(UUID uniqueId) {
        PermissionModifier previous = super.removeModifier(uniqueId);
        if (previous != null) {
            take(previous.getPermission());
        }
        return previous;
    }

    private void give(String permission) {
        User user = requireUser();
        Node existing = grantedNodes.remove(permission);
        if (existing != null) {
            user.getTransientData().remove(existing);
        }

        Node node = Node.builder(permission).value(true).build();
        user.getTransientData().add(node);
        grantedNodes.put(permission, node);
    }

    private void take(String permission) {
        Node node = grantedNodes.remove(permission);
        if (node == null) {
            return;
        }
        User user = user();
        if (user != null) {
            user.getTransientData().remove(node);
        }
    }

    private User requireUser() {
        User user = user();
        if (user == null) {
            throw new IllegalStateException("LuckPerms user is not loaded for " + getPlayerData().getUniqueId());
        }
        return user;
    }

    private User user() {
        LuckPerms luckPerms = LuckPermsProvider.get();
        return luckPerms.getUserManager().getUser(getPlayerData().getUniqueId());
    }
}
