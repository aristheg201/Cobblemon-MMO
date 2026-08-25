package io.lumine.mythic.lib.player.cooldown;

import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.player.PlayerDataMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CooldownMap extends PlayerDataMap {
    private static final long FLUSH_INTERVAL = 60_000L;
    private final Map<String, CooldownInfo> map = new HashMap<>();
    private long nextFlush = System.currentTimeMillis() + FLUSH_INTERVAL;

    public CooldownInfo applyCooldown(CooldownObject object, double duration) {
        return applyCooldown(object.getCooldownPath(), duration);
    }

    public CooldownInfo applyCooldown(String path, double duration) {
        tryFlush();
        String key = UtilityMethods.enumName(path);
        return map.compute(key, (ignored, current) ->
                current == null || current.getRemaining() < duration * 1000d ? new CooldownInfo(duration) : current);
    }

    public CooldownInfo getInfo(CooldownObject object) { return getInfo(object.getCooldownPath()); }
    public CooldownInfo getInfo(String path) { return map.get(UtilityMethods.enumName(path)); }
    public double getCooldown(CooldownObject object) { return getCooldown(object.getCooldownPath()); }

    public double getCooldown(String path) {
        CooldownInfo info = map.get(UtilityMethods.enumName(path));
        return info == null ? 0d : info.getRemaining() / 1000d;
    }

    public boolean isOnCooldown(CooldownObject object) { return isOnCooldown(object.getCooldownPath()); }
    public boolean isOnCooldown(String path) {
        CooldownInfo info = map.get(UtilityMethods.enumName(path));
        return info != null && !info.hasEnded();
    }

    public void resetCooldown(CooldownObject object) { resetCooldown(object.getCooldownPath()); }
    public void resetCooldown(String path) { map.remove(UtilityMethods.enumName(path)); }
    public Set<String> getCooldownKeys() { return map.keySet(); }
    public void clearAllCooldowns() { map.clear(); }

    private void tryFlush() {
        if (System.currentTimeMillis() < nextFlush) return;
        nextFlush = System.currentTimeMillis() + FLUSH_INTERVAL;
        map.values().removeIf(CooldownInfo::hasEnded);
    }
}
