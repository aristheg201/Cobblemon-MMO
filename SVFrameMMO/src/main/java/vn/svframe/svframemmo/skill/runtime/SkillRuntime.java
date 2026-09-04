package vn.svframe.svframemmo.skill.runtime;

import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.api.MMOLineConfig;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.modifier.ModifierSource;
import vn.svframe.svframelib.player.modifier.ModifierType;
import vn.svframe.svframelib.player.modifier.PlayerModifier;
import vn.svframe.svframelib.player.skill.PassiveSkill;
import vn.svframe.svframelib.player.skillmod.SkillModifier;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.skill.ClassSkill;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Owns runtime-only passive registrations, class scripts and skill-slot parameter buffs. */
public final class SkillRuntime {
    private final Map<UUID, Map<String, Registration>> registrations = new LinkedHashMap<>();

    public synchronized void attach(PlayerData data) { refresh(data); }

    public synchronized void detach(PlayerData data) {
        Map<String, Registration> removed = registrations.remove(data.getUniqueId());
        if (removed != null) removed.values().forEach(Registration::close);
    }

    public synchronized void refresh(PlayerData data) {
        detach(data);
        if (!data.isOnline()) return;
        LinkedHashMap<String, Registration> next = new LinkedHashMap<>();

        for (ClassSkill skill : data.getProfess().getSkills()) {
            if (!skill.isPermanent() || !skill.getTrigger().isPassive() || !data.canUseSkill(skill)) continue;
            Registration registration = new Registration(data.getMMOPlayerData());
            PassiveSkill passive = new PassiveSkill("svframemmo-permanent-" + skill.getSkill().getLowerCaseId(),
                    skill.getTrigger(), new ClassCastableSkill(skill, data), EquipmentSlot.OTHER, ModifierSource.OTHER);
            passive.register(data.getMMOPlayerData());
            registration.modifiers.add(passive);
            next.put("permanent:" + skill.getSkill().getId(), registration);
        }

        int classScriptIndex = 0;
        for (PassiveSkill script : data.getProfess().getScripts()) {
            Registration registration = new Registration(data.getMMOPlayerData());
            script.register(data.getMMOPlayerData());
            registration.modifiers.add(script);
            next.put("class-script:" + classScriptIndex++, registration);
        }

        for (Map.Entry<Integer, String> entry : data.getSkillBindings().entrySet()) {
            int slot = entry.getKey();
            ClassSkill skill = data.getProfess().getSkill(entry.getValue());
            var slotDefinition = data.getProfess().getSkillSlot(slot);
            if (skill == null || slotDefinition == null || !data.canUseSkill(skill)) continue;
            Registration registration = new Registration(data.getMMOPlayerData());
            int buffIndex = 0;
            for (String line : slotDefinition.skillBuffs()) {
                SkillModifier modifier = parseSlotBuff(data, slot, skill, line, buffIndex++);
                modifier.register(data.getMMOPlayerData());
                registration.modifiers.add(modifier);
            }
            if (skill.getTrigger().isPassive() && !skill.isPermanent()) {
                PassiveSkill passive = new PassiveSkill("svframemmo-bound-slot-" + slot,
                        skill.getTrigger(), new ClassCastableSkill(skill, data), EquipmentSlot.OTHER, ModifierSource.OTHER);
                passive.register(data.getMMOPlayerData());
                registration.modifiers.add(passive);
            }
            next.put("slot:" + slot, registration);
        }

        for (Map.Entry<String, Integer> learned : SVFrameMMO.externalProgression().learned(data.getUniqueId()).entrySet()) {
            ClassSkill skill = SVFrameMMO.externalSkills().get(learned.getKey());
            if (skill == null || !skill.getTrigger().isPassive()) continue;
            Registration registration = new Registration(data.getMMOPlayerData());
            PassiveSkill passive = new PassiveSkill("svframemmo-external-" + skill.getSkill().getLowerCaseId(),
                    skill.getTrigger(), new ClassCastableSkill(skill, data, false), EquipmentSlot.OTHER, ModifierSource.OTHER);
            passive.register(data.getMMOPlayerData());
            registration.modifiers.add(passive);
            next.put("external:" + skill.getSkill().getId(), registration);
        }
        registrations.put(data.getUniqueId(), next);
    }

    public SkillResult castBound(PlayerData data, int slot) {
        ClassSkill external = SVFrameMMO.externalProgression().boundSkill(data.getUniqueId(), slot);
        if (external != null) return cast(data, external);
        ClassSkill skill = data.getBoundSkill(slot);
        if (skill == null) throw new IllegalArgumentException("No skill bound to slot " + slot);
        return cast(data, skill);
    }

    public SkillResult cast(PlayerData data, String skillId) {
        ClassSkill skill = resolve(data, skillId);
        if (skill == null) throw new IllegalArgumentException("Unknown or unavailable SVFrameMMO skill '" + skillId + "'");
        return cast(data, skill);
    }

    public SkillResult cast(PlayerData data, ClassSkill skill) {
        boolean external = SVFrameMMO.externalSkills().get(skill.getSkill().getId()) != null;
        if (external && !SVFrameMMO.externalProgression().isLearned(data.getUniqueId(), skill.getSkill().getId()))
            throw new IllegalStateException("External skill is not learned: " + skill.getSkill().getId());
        return cast(data, skill, !external);
    }

    /** Casts a runtime-owned temporary skill without requiring persistent ownership. */
    public SkillResult castTemporary(PlayerData data, ClassSkill skill) { return cast(data, skill, false); }

    private SkillResult cast(PlayerData data, ClassSkill skill, boolean requireClassProgression) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(skill, "skill");
        if (skill.getTrigger().isPassive()) throw new IllegalArgumentException("Passive skills cannot be manually cast: " + skill.getSkill().getId());
        if (!data.isOnline()) throw new IllegalStateException("Cannot cast a skill for an offline player");
        ClassCastableSkill cast = new ClassCastableSkill(skill, data, requireClassProgression);
        return cast.cast(new SkillMetadata(cast, data.getMMOPlayerData()));
    }

    public synchronized int registrationCount(UUID player) {
        Map<String, Registration> map = registrations.get(player);
        return map == null ? 0 : map.size();
    }

    private static ClassSkill resolve(PlayerData data, String skillId) {
        ClassSkill skill = data.getProfess().getSkill(skillId);
        if (skill != null) return skill;
        ClassSkill external = SVFrameMMO.externalSkills().get(skillId);
        return external != null && SVFrameMMO.externalProgression().isLearned(data.getUniqueId(), external.getSkill().getId()) ? external : null;
    }

    private static SkillModifier parseSlotBuff(PlayerData data, int slot, ClassSkill skill, String line, int index) {
        MMOLineConfig config = new MMOLineConfig(line);
        String key = config.getKey().trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!key.equals("skill_buff")) throw new IllegalArgumentException("Skill slot buff must use skill_buff: " + line);
        String parameter = config.getString("modifier", config.getString("parameter", null));
        if (parameter == null || parameter.isBlank()) throw new IllegalArgumentException("Missing modifier in skill slot buff: " + line);
        skill.getParameterFormula(parameter);
        double amount = config.getDouble("amount");
        ModifierType type = ModifierType.valueOf(UtilityMethods.enumName(config.getString("type", "FLAT")));
        UUID id = UUID.nameUUIDFromBytes((data.getUniqueId() + ":slot:" + slot + ":" + skill.getSkill().getId() + ":" + index)
                .getBytes(StandardCharsets.UTF_8));
        return new SkillModifier(id, "svframemmo_skill_slot", parameter, List.of(skill.getSkill()), amount, type,
                EquipmentSlot.OTHER, ModifierSource.OTHER);
    }

    private static final class Registration {
        private final MMOPlayerData data;
        private final List<PlayerModifier> modifiers = new ArrayList<>();
        private Registration(MMOPlayerData data) { this.data = Objects.requireNonNull(data, "data"); }
        private void close() {
            for (int i = modifiers.size() - 1; i >= 0; i--) modifiers.get(i).unregister(data);
            modifiers.clear();
        }
    }
}
