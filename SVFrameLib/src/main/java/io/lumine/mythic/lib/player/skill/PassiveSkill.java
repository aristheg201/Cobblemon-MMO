package io.lumine.mythic.lib.player.skill;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.modifier.ModifierMap;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.PlayerModifier;
import io.lumine.mythic.lib.skill.SimpleSkill;
import io.lumine.mythic.lib.skill.Skill;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import io.lumine.mythic.lib.util.configobject.ConfigObject;

import java.util.Objects;

public class PassiveSkill extends PlayerModifier {
    private final Skill triggered;
    private final TriggerType trigger;

    public PassiveSkill(String key, TriggerType trigger, Skill triggered) {
        this(key, trigger, triggered, EquipmentSlot.OTHER, ModifierSource.OTHER);
    }

    public PassiveSkill(String key, TriggerType trigger, Skill triggered, EquipmentSlot slot, ModifierSource source) {
        super(key, slot, source);
        this.trigger = trigger;
        this.triggered = triggered;
        Objects.requireNonNull(triggered.getHandler(), "Null skill handler");
    }

    public PassiveSkill(ConfigObject config) {
        super(config.getString("key"), EquipmentSlot.OTHER, ModifierSource.OTHER);
        this.triggered = new SimpleSkill(MythicLib.plugin.getSkills().getHandlerOrThrow(config.getString("skill")));
        this.trigger = TriggerType.valueOf(config.getString("trigger"));
        Objects.requireNonNull(triggered.getHandler(), "Null skill handler");
    }

    public PassiveSkill(String key, Skill skill, EquipmentSlot slot, ModifierSource source) {
        super(key, slot, source);
        TriggerType type = skill.getTrigger();
        if (!type.isPassive()) {
            throw new IllegalArgumentException("Skill is active");
        }
        this.triggered = Objects.requireNonNull(skill, "Skill cannot be null");
        this.trigger = type;
        Objects.requireNonNull(triggered.getHandler(), "Null skill handler");
    }

    public Skill getTriggeredSkill() {
        return triggered;
    }

    public long getTimerPeriod() {
        return Math.max(1L, (long) triggered.getParameter("timer")) * 50L;
    }

    public TriggerType getType() {
        return getTrigger();
    }

    public TriggerType getTrigger() {
        return trigger;
    }

    @Override
    public void register(MMOPlayerData playerData) {
        playerData.getPassiveSkillMap().addModifier(this);
    }

    @Override
    public void unregister(MMOPlayerData playerData) {
        playerData.getPassiveSkillMap().removeModifier(getUniqueId());
    }

    @Override
    public ModifierMap<?> getMap(MMOPlayerData playerData) {
        return playerData.getPassiveSkillMap();
    }

    public static PassiveSkill fromConfig(ConfigObject config) {
        return new PassiveSkill(config);
    }
}
