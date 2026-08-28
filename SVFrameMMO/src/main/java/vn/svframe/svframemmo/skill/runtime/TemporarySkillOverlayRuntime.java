package vn.svframe.svframemmo.skill.runtime;

import vn.svframe.svframemmo.skill.ClassSkill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime-only skill bindings owned by integration systems such as Pokemon fusion.
 * Persistent class bindings are never mutated.
 */
public final class TemporarySkillOverlayRuntime {
    private final Map<UUID, Overlay> overlays = new ConcurrentHashMap<>();

    public Handle push(UUID player, String owner, List<ClassSkill> skills) {
        Objects.requireNonNull(skills, "skills");
        LinkedHashMap<Integer, ClassSkill> slots = new LinkedHashMap<>();
        int slot = 1;
        for (ClassSkill skill : skills) {
            if (skill == null || skill.getTrigger().isPassive()) continue;
            slots.put(slot++, skill);
        }
        return push(player, owner, slots);
    }

    public Handle push(UUID player, String owner, Map<Integer, ClassSkill> bindings) {
        Objects.requireNonNull(player, "player");
        String normalizedOwner = Objects.requireNonNull(owner, "owner").trim();
        if (normalizedOwner.isEmpty()) throw new IllegalArgumentException("owner must not be blank");
        Objects.requireNonNull(bindings, "bindings");

        ArrayList<Slot> slots = new ArrayList<>();
        bindings.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            int index = entry.getKey() == null ? 0 : entry.getKey();
            ClassSkill skill = entry.getValue();
            if (index <= 0 || index > 8) throw new IllegalArgumentException("Temporary skill slot must be between 1 and 8: " + index);
            if (skill == null) throw new IllegalArgumentException("Temporary skill cannot be null for slot " + index);
            if (skill.getTrigger().isPassive()) throw new IllegalArgumentException("Temporary manual overlay cannot contain passive skill " + skill.getSkill().getId());
            slots.add(new Slot(index, skill));
        });
        if (slots.isEmpty()) throw new IllegalArgumentException("Temporary skill overlay must contain at least one active skill");

        UUID token = UUID.randomUUID();
        Overlay overlay = new Overlay(normalizedOwner, token, List.copyOf(slots));
        overlays.put(player, overlay);
        return new Handle(this, player, token);
    }

    public Overlay get(UUID player) { return player == null ? null : overlays.get(player); }
    public List<Slot> slots(UUID player) { Overlay overlay = get(player); return overlay == null ? List.of() : overlay.slots(); }
    public boolean has(UUID player) { return player != null && overlays.containsKey(player); }

    public boolean clear(UUID player, UUID token) {
        if (player == null || token == null) return false;
        final boolean[] removed = {false};
        overlays.computeIfPresent(player, (id, current) -> {
            if (!current.token().equals(token)) return current;
            removed[0] = true;
            return null;
        });
        return removed[0];
    }

    public void clear(UUID player) { if (player != null) overlays.remove(player); }
    public void clear() { overlays.clear(); }
    public int size() { return overlays.size(); }

    public record Slot(int slot, ClassSkill skill) { }
    public record Overlay(String owner, UUID token, List<Slot> slots) { }

    public static final class Handle implements AutoCloseable {
        private final TemporarySkillOverlayRuntime runtime;
        private final UUID player;
        private final UUID token;
        private volatile boolean closed;

        private Handle(TemporarySkillOverlayRuntime runtime, UUID player, UUID token) {
            this.runtime = runtime;
            this.player = player;
            this.token = token;
        }

        public UUID token() { return token; }

        @Override public void close() {
            if (closed) return;
            closed = true;
            runtime.clear(player, token);
        }
    }
}
