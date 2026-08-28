package vn.svframe.svframemmo.skill;

import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified player-facing skill catalog. Class progression remains class-scoped internally while integration-contributed
 * progression remains global, but callers no longer need two separate RPG skill systems.
 */
public final class PlayerSkillCatalog {
    private PlayerSkillCatalog() { }

    public enum Origin { CLASS, EXTERNAL }

    public record Entry(ClassSkill skill, Origin origin, boolean learned, int level) {
        public String id() { return skill.getSkill().getId(); }
        public boolean bindable() { return !skill.isPermanent() && !skill.getTrigger().isPassive(); }
    }

    /** Current-class definitions plus learned external definitions, de-duplicated by canonical skill ID. */
    public static List<Entry> entries(PlayerData data) {
        LinkedHashMap<String, Entry> result = new LinkedHashMap<>();
        for (ClassSkill skill : data.getProfess().getSkills()) {
            String id = skill.getSkill().getId();
            result.put(id, new Entry(skill, Origin.CLASS, data.canUseSkill(skill), data.getSkillLevel(id)));
        }
        for (Map.Entry<String, Integer> learned : SVFrameMMO.externalProgression().learned(data.getUniqueId()).entrySet()) {
            ClassSkill skill = SVFrameMMO.externalSkills().get(learned.getKey());
            if (skill == null) continue;
            result.putIfAbsent(skill.getSkill().getId(), new Entry(skill, Origin.EXTERNAL, true, Math.max(1, learned.getValue())));
        }
        ArrayList<Entry> out = new ArrayList<>(result.values());
        out.sort(Comparator.comparing((Entry entry) -> entry.origin() == Origin.CLASS ? 0 : 1)
                .thenComparing(entry -> entry.skill().getSkill().getName(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(out);
    }

    /** Resolves a skill the player can currently use. */
    public static Entry owned(PlayerData data, String skillId) {
        ClassSkill classSkill = data.getProfess().getSkill(skillId);
        if (classSkill != null && data.canUseSkill(classSkill))
            return new Entry(classSkill, Origin.CLASS, true, data.getSkillLevel(classSkill.getSkill().getId()));
        ClassSkill external = SVFrameMMO.externalSkills().get(skillId);
        if (external != null && SVFrameMMO.externalProgression().isLearned(data.getUniqueId(), external.getSkill().getId()))
            return new Entry(external, Origin.EXTERNAL, true,
                    Math.max(1, SVFrameMMO.externalProgression().level(data.getUniqueId(), external.getSkill().getId())));
        return null;
    }

    /** Resolves either a current-class definition or any contributed external definition, regardless of ownership. */
    public static Entry definition(PlayerData data, String skillId) {
        ClassSkill classSkill = data.getProfess().getSkill(skillId);
        if (classSkill != null)
            return new Entry(classSkill, Origin.CLASS, data.canUseSkill(classSkill), data.getSkillLevel(classSkill.getSkill().getId()));
        ClassSkill external = SVFrameMMO.externalSkills().get(skillId);
        if (external == null) return null;
        boolean learned = SVFrameMMO.externalProgression().isLearned(data.getUniqueId(), external.getSkill().getId());
        return new Entry(external, Origin.EXTERNAL, learned,
                learned ? Math.max(1, SVFrameMMO.externalProgression().level(data.getUniqueId(), external.getSkill().getId())) : 0);
    }

    /** Effective persistent loadout. External and class skills can coexist; a slot can contain only one of them. */
    public static Map<Integer, Entry> bindings(PlayerData data) {
        LinkedHashMap<Integer, Entry> result = new LinkedHashMap<>();
        data.getSkillBindings().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ClassSkill skill = data.getProfess().getSkill(entry.getValue());
            if (skill != null && data.canUseSkill(skill))
                result.put(entry.getKey(), new Entry(skill, Origin.CLASS, true, data.getSkillLevel(skill.getSkill().getId())));
        });
        SVFrameMMO.externalProgression().bindings(data.getUniqueId()).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ClassSkill skill = SVFrameMMO.externalSkills().get(entry.getValue());
            if (skill != null && SVFrameMMO.externalProgression().isLearned(data.getUniqueId(), skill.getSkill().getId()))
                result.put(entry.getKey(), new Entry(skill, Origin.EXTERNAL, true,
                        Math.max(1, SVFrameMMO.externalProgression().level(data.getUniqueId(), skill.getSkill().getId()))));
        });
        return Map.copyOf(result);
    }

    /** Slots visible in the unified RPG loadout UI. */
    public static List<Integer> slots(PlayerData data) {
        Set<Integer> slots = new LinkedHashSet<>();
        data.getProfess().getSlots().forEach(slot -> slots.add(slot.slot()));
        for (int slot = 1; slot <= ExternalSkillProgression.LOADOUT_SIZE; slot++) slots.add(slot);
        return slots.stream().filter(slot -> slot > 0).sorted().toList();
    }

    public static void bind(PlayerData data, int slot, String skillId) {
        Entry entry = owned(data, skillId);
        if (entry == null) throw new IllegalStateException("Skill is locked or not learned: " + skillId);
        if (!entry.bindable()) throw new IllegalArgumentException("Passive/permanent skill cannot be bound: " + entry.id());
        if (entry.origin() == Origin.EXTERNAL) {
            SVFrameMMO.externalProgression().bind(data.getUniqueId(), slot, entry.id());
            data.unbindSkill(slot);
            SVFrameMMO.externalProgression().save();
        } else {
            data.bindSkill(slot, entry.id());
            SVFrameMMO.externalProgression().unbind(data.getUniqueId(), slot);
            SVFrameMMO.externalProgression().save();
        }
    }

    public static String unbind(PlayerData data, int slot) {
        String external = SVFrameMMO.externalProgression().unbind(data.getUniqueId(), slot);
        String classSkill = data.unbindSkill(slot);
        if (external != null) SVFrameMMO.externalProgression().save();
        return external != null ? external : classSkill;
    }

    public static int level(PlayerData data, ClassSkill skill) {
        ClassSkill external = SVFrameMMO.externalSkills().get(skill.getSkill().getId());
        if (external != null && SVFrameMMO.externalProgression().isLearned(data.getUniqueId(), external.getSkill().getId()))
            return Math.max(1, SVFrameMMO.externalProgression().level(data.getUniqueId(), external.getSkill().getId()));
        return data.getSkillLevel(skill.getSkill().getId());
    }
}
