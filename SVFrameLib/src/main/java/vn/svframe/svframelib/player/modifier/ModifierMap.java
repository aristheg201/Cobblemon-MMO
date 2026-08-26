package vn.svframe.svframelib.player.modifier;

import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.PlayerDataMap;
import vn.svframe.svframelib.util.Closeable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Player-bound modifier container with the same replacement/removal ownership
 * semantics as MythicLib 1.7.1. Modifier maps are session-aware data maps;
 * subclasses can therefore activate/deactivate their runtime state from the
 * PlayerDataMap lifecycle.
 */
public abstract class ModifierMap<T extends PlayerModifier> extends PlayerDataMap {
    protected final MMOPlayerData playerData;
    protected final Map<UUID, T> modifiers = new HashMap<>();

    public ModifierMap(MMOPlayerData playerData) {
        this.playerData = playerData;
    }

    public MMOPlayerData getPlayerData() {
        return playerData;
    }

    public Collection<T> getModifiers() {
        return modifiers.values();
    }

    public List<T> isolateModifiers(EquipmentSlot slot) {
        List<T> isolated = new ArrayList<>();
        for (T modifier : getModifiers()) {
            if (slot.isCompatible(modifier)) isolated.add(modifier);
        }
        return isolated;
    }

    public T addModifier(T modifier) {
        T replaced = modifiers.put(modifier.getUniqueId(), modifier);
        closeIfNeeded(replaced);
        return replaced;
    }

    public T removeModifier(UUID uniqueId) {
        T removed = modifiers.remove(uniqueId);
        closeIfNeeded(removed);
        return removed;
    }

    public void removeModifiersIf(Predicate<String> predicate) {
        Iterator<T> iterator = modifiers.values().iterator();
        while (iterator.hasNext()) {
            T modifier = iterator.next();
            if (!predicate.test(modifier.getKey())) continue;
            iterator.remove();
            closeIfNeeded(modifier);
        }
    }

    public void removeModifiers(String key) {
        Iterator<T> iterator = modifiers.values().iterator();
        while (iterator.hasNext()) {
            T modifier = iterator.next();
            if (!modifier.getKey().equals(key)) continue;
            iterator.remove();
            closeIfNeeded(modifier);
        }
    }

    private static void closeIfNeeded(PlayerModifier modifier) {
        if (modifier instanceof Closeable closeable) closeable.close();
    }
}
