package vn.svframe.svframelib.player.permission;

import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.modifier.ModifierMap;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Native equivalent of Bukkit PermissionAttachment using LuckPerms transient session nodes. */
public class PermissionMap extends ModifierMap<PermissionModifier> {
    private final Map<String, Node> grantedNodes = new HashMap<>();

    public PermissionMap(MMOPlayerData playerData) { super(playerData); }

    @Override
    protected void onSessionOpen() { }

    @Override
    protected void onSessionClose() {
        User user = user();
        if (user != null) for (Node node : grantedNodes.values()) user.transientData().remove(node);
        grantedNodes.clear();
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
        User user = requireUser();
        Node existing = grantedNodes.remove(permission);
        if (existing != null) user.transientData().remove(existing);
        Node node = Node.builder(permission).value(true).build();
        user.transientData().add(node);
        grantedNodes.put(permission, node);
    }

    private void take(String permission) {
        Node node = grantedNodes.remove(permission);
        if (node == null) return;
        User user = user();
        if (user != null) user.transientData().remove(node);
    }

    private User requireUser() {
        User user = user();
        if (user == null) throw new IllegalStateException("LuckPerms user is not loaded for " + getPlayerData().getUniqueId());
        return user;
    }

    private User user() {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            return luckPerms.getUserManager().getUser(getPlayerData().getUniqueId());
        } catch (IllegalStateException unavailable) {
            return null;
        }
    }
}
