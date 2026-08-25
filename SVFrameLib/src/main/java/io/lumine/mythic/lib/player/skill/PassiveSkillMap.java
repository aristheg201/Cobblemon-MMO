package io.lumine.mythic.lib.player.skill;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.modifier.ModifierMap;
import io.lumine.mythic.lib.skill.SkillMetadata;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;

/** Fabric-native passive skill map with MythicLib 1.7.1 timer/login semantics. */
public class PassiveSkillMap extends ModifierMap<PassiveSkill> {
    private final Map<String, Long> lastCast = new HashMap<>();

    public PassiveSkillMap(MMOPlayerData playerData) {
        super(playerData);
    }

    @Override
    protected void onSessionOpen() {
        getPlayerData().triggerSkills(TriggerType.LOGIN);
    }

    public PassiveSkill getSkill(SkillHandler handler) {
        for (PassiveSkill passive : getModifiers()) {
            if (handler.equals(passive.getTriggeredSkill().getHandler())) return passive;
        }
        return null;
    }

    public void tickTimerSkills() {
        if (!sessionOpen) throw new IllegalArgumentException("Session not open");

        var metadata = SkillMetadata.lazyOf(getPlayerData());
        for (PassiveSkill passive : getModifiers()) {
            if (!TriggerType.TIMER.equals(passive.getTrigger())) continue;
            if (getPlayerData().getPlayer().interactionManager.getGameMode() == GameMode.SPECTATOR) continue;

            String handlerId = passive.getTriggeredSkill().getHandler().getId();
            Long last = lastCast.get(handlerId);
            long lastTimestamp = last == null ? 0L : last;
            if (lastTimestamp + passive.getTimerPeriod() > System.currentTimeMillis()) continue;

            lastCast.put(handlerId, System.currentTimeMillis());
            passive.getTriggeredSkill().cast(metadata.get());
        }
    }
}
