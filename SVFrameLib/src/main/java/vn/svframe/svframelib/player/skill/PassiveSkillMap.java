package vn.svframe.svframelib.player.skill;

import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.modifier.ModifierMap;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.trigger.TriggerType;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;

/** Fabric-native passive skill map with SVFrameLib 1.7.1 timer/login semantics. */
public class PassiveSkillMap extends ModifierMap<PassiveSkill> {
    private final Map<String, Long> nextFireAt = new HashMap<>();

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

            String handlerId = passive.getTriggeredSkill().getHandler().getId();
            long now = System.currentTimeMillis();
            Long deadline = nextFireAt.get(handlerId);
            if (deadline != null && now < deadline) continue;

            // Revalidate runtime conditions at the exact point the passive is due.
            // Leaving the deadline unchanged while blocked preserves the old behavior:
            // the skill fires on the first eligible tick instead of skipping an interval.
            if (getPlayerData().getPlayer().interactionManager.getGameMode() == GameMode.SPECTATOR) continue;

            passive.getTriggeredSkill().cast(metadata.get());
            nextFireAt.put(handlerId, now + passive.getTimerPeriod());
        }
    }
}
