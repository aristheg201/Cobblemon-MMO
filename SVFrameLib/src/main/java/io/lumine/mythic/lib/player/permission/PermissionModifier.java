package io.lumine.mythic.lib.player.permission;

import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.modifier.ModifierMap;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.PlayerModifier;
import io.lumine.mythic.lib.util.configobject.ConfigObject;

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
