package vn.svframe.svframemmo.skill;

import vn.svframe.svframelib.UtilityMethods;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registry of persistent SVFrameMMO skills contributed by integration mods. */
public final class ExternalSkillRegistry {
    private final Map<String, Map<String, ClassSkill>> byOwner = new LinkedHashMap<>();
    private final Map<String, ClassSkill> skills = new LinkedHashMap<>();

    public synchronized void replace(String owner, Collection<ClassSkill> definitions) {
        String ownerKey = normalizeOwner(owner);
        Objects.requireNonNull(definitions, "definitions");
        LinkedHashMap<String, ClassSkill> nextOwner = new LinkedHashMap<>();
        for (ClassSkill skill : definitions) {
            Objects.requireNonNull(skill, "External skill cannot be null");
            String id = normalize(skill.getSkill().getId());
            if (nextOwner.putIfAbsent(id, skill) != null)
                throw new IllegalArgumentException("Duplicate external skill ID from " + owner + ": " + skill.getSkill().getId());
        }
        for (Map.Entry<String, Map<String, ClassSkill>> entry : byOwner.entrySet()) {
            if (entry.getKey().equals(ownerKey)) continue;
            for (String id : nextOwner.keySet()) {
                if (entry.getValue().containsKey(id))
                    throw new IllegalArgumentException("External skill ID collision between '" + owner + "' and '" + entry.getKey() + "': " + id);
            }
        }
        byOwner.put(ownerKey, Map.copyOf(nextOwner));
        rebuild();
    }

    public synchronized void remove(String owner) {
        if (byOwner.remove(normalizeOwner(owner)) != null) rebuild();
    }

    public synchronized ClassSkill get(String id) {
        return id == null ? null : skills.get(normalize(id));
    }

    public synchronized Collection<ClassSkill> getAll() { return List.copyOf(skills.values()); }

    public synchronized Collection<ClassSkill> getByOwner(String owner) {
        Map<String, ClassSkill> found = byOwner.get(normalizeOwner(owner));
        return found == null ? List.of() : List.copyOf(found.values());
    }

    public synchronized int size() { return skills.size(); }

    private void rebuild() {
        skills.clear();
        for (Map<String, ClassSkill> ownerSkills : byOwner.values()) skills.putAll(ownerSkills);
    }

    private static String normalize(String id) {
        String normalized = UtilityMethods.enumName(Objects.requireNonNull(id, "skill id"));
        if (normalized.isBlank()) throw new IllegalArgumentException("Skill ID cannot be blank");
        return normalized;
    }

    private static String normalizeOwner(String owner) {
        String normalized = Objects.requireNonNull(owner, "owner").trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("External skill owner cannot be blank");
        return normalized;
    }
}
