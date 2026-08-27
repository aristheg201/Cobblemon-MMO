package vn.svframe.svframelib.player.skillmod;

import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.stat.api.InstanceModifier;
import vn.svframe.svframelib.player.modifier.ModifierMap;
import vn.svframe.svframelib.player.modifier.ModifierSource;
import vn.svframe.svframelib.player.modifier.ModifierType;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Skill parameter modifier preserving SVFrameLib 1.7.1 semantics. */
public class SkillModifier extends InstanceModifier {
    private final List<SkillHandler<?>> skills;
    private final String parameter;

    public SkillModifier(String key, String parameter, List<SkillHandler<?>> skills, double value) {
        this(key, parameter, skills, value, ModifierType.FLAT, EquipmentSlot.OTHER, ModifierSource.OTHER);
    }
    public SkillModifier(String key, String parameter, List<SkillHandler<?>> skills, double value, ModifierType type) {
        this(key, parameter, skills, value, type, EquipmentSlot.OTHER, ModifierSource.OTHER);
    }
    public SkillModifier(String key, String parameter, List<SkillHandler<?>> skills, double value, ModifierType type, EquipmentSlot slot, ModifierSource source) {
        this(UUID.randomUUID(), key, parameter, skills, value, type, slot, source);
    }
    public SkillModifier(UUID id, String key, String parameter, List<SkillHandler<?>> skills, double value, ModifierType type, EquipmentSlot slot, ModifierSource source) {
        super(id, key, slot, source, value, type);
        this.skills = List.copyOf(Objects.requireNonNull(skills, "skills"));
        this.parameter = Objects.requireNonNull(parameter, "parameter");
    }

    public SkillModifier add(double offset) {
        return new SkillModifier(getUniqueId(), getKey(), parameter, new ArrayList<>(skills), value + offset, type, getSlot(), getSource());
    }
    public List<SkillHandler<?>> getSkills() { return skills; }
    public String getParameter() { return parameter; }

    @Deprecated public void register(MMOPlayerData data, SkillHandler<?> handler) { register(data); }
    @Deprecated public void unregister(MMOPlayerData data, SkillHandler<?> handler) { unregister(data); }
    @Override public void register(MMOPlayerData data) { data.getSkillModifierMap().addModifier(this); }
    @Override public void unregister(MMOPlayerData data) { data.getSkillModifierMap().removeModifier(getUniqueId()); }
    @Override public ModifierMap<?> getMap(MMOPlayerData data) { return data.getSkillModifierMap(); }

    /** 1.7.1 intentionally leaves this config parser unimplemented. */
    public static SkillModifier fromConfig(ConfigObject config) { throw new RuntimeException("Not implemented"); }
}
