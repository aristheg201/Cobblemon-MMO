package vn.svframe.svframelib.player.permission;

import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.modifier.ModifierMap;
import vn.svframe.svframelib.player.modifier.ModifierSource;
import vn.svframe.svframelib.player.modifier.PlayerModifier;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.UUID;

public class PermissionModifier extends PlayerModifier {
    private final String permission;

    public PermissionModifier(String key, String permission, EquipmentSlot slot, ModifierSource source) {
        super(key, slot, source);
        this.permission = permission;
    }

    public PermissionModifier(UUID uniqueId, String key, String permission, EquipmentSlot slot, ModifierSource source) {
        super(uniqueId, key, slot, source);
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }

    @Override
    public void register(MMOPlayerData playerData) {
        playerData.getPermissionMap().addModifier(this);
    }

    @Override
    public void unregister(MMOPlayerData playerData) {
        playerData.getPermissionMap().removeModifier(getUniqueId());
    }

    @Override
    public ModifierMap<?> getMap(MMOPlayerData playerData) {
        return playerData.getPermissionMap();
    }

    public static PermissionModifier fromConfig(ConfigObject config) {
        throw new RuntimeException("Not implemented");
    }
}
