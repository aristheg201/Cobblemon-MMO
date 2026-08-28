package vn.svframe.svframemmo.cobblemon.move;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Timed replacement for turn-bound Pokemon stat stages. */
public final class RealtimeBattleState {
    private final Map<UUID, EnumMap<BattleStat, Stage>> states = new ConcurrentHashMap<>();
    private final Map<UUID, Long> protectedUntil = new ConcurrentHashMap<>();

    public int stage(UUID entity, BattleStat stat, long tick) {
        EnumMap<BattleStat, Stage> map = states.get(entity);
        if (map == null) return 0;
        Stage stage = map.get(stat);
        if (stage == null) return 0;
        if (tick >= stage.expiresAt()) {
            map.remove(stat);
            if (map.isEmpty()) states.remove(entity, map);
            return 0;
        }
        return stage.value();
    }

    public int add(UUID entity, BattleStat stat, int amount, long tick, long durationTicks) {
        if (amount == 0) return stage(entity, stat, tick);
        EnumMap<BattleStat, Stage> map = states.computeIfAbsent(entity, ignored -> new EnumMap<>(BattleStat.class));
        int current = stage(entity, stat, tick);
        int next = Math.max(-6, Math.min(6, current + amount));
        map.put(stat, new Stage(next, tick + Math.max(1, durationTicks)));
        return next;
    }

    public void protect(UUID entity, long tick, long durationTicks) {
        if (entity != null) protectedUntil.put(entity, tick + Math.max(1, durationTicks));
    }

    public boolean protectedNow(UUID entity, long tick) {
        Long expiry = protectedUntil.get(entity);
        if (expiry == null) return false;
        if (tick < expiry) return true;
        protectedUntil.remove(entity, expiry);
        return false;
    }

    public void clear(UUID entity) { if (entity != null) { states.remove(entity); protectedUntil.remove(entity); } }
    public void clear() { states.clear(); protectedUntil.clear(); }

    public void tick(long tick) {
        protectedUntil.entrySet().removeIf(entry -> tick >= entry.getValue());
        states.forEach((id, map) -> {
            map.entrySet().removeIf(entry -> tick >= entry.getValue().expiresAt());
            if (map.isEmpty()) states.remove(id, map);
        });
    }

    private record Stage(int value, long expiresAt) { }
}
