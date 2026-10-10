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
    private final Map<String, TimerState> timers = new HashMap<>();

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
            long period = passive.getTimerPeriod();
            long now = System.currentTimeMillis();
            TimerState timer = timers.get(handlerId);
            if (timer != null) {
                // The legacy runtime evaluated timer every tick. If a live parameter changes,
                // recompute from the last actual fire timestamp so the original phase is preserved.
                if (timer.period != period) timer.reschedule(period);
                if (now < timer.nextFireAt) continue;
            }

            // Runtime eligibility is deliberately revalidated only when the passive is due.
            // A blocked fire leaves the deadline due, matching the old "fire first eligible tick" behavior.
            if (getPlayerData().getPlayer().interactionManager.getGameMode() == GameMode.SPECTATOR) continue;

            // Legacy code recorded lastCast immediately before cast. Keep the same phase anchor.
            long firedAt = System.currentTimeMillis();
            if (timer == null) {
                timer = new TimerState(firedAt, period);
                timers.put(handlerId, timer);
            } else {
                timer.fired(firedAt, period);
            }
            passive.getTriggeredSkill().cast(metadata.get());
        }
    }

    private static final class TimerState {
        private long lastFireAt;
        private long period;
        private long nextFireAt;

        private TimerState(long lastFireAt, long period) {
            fired(lastFireAt, period);
        }

        private void fired(long lastFireAt, long period) {
            this.lastFireAt = lastFireAt;
            this.period = period;
            this.nextFireAt = lastFireAt + period;
        }

        private void reschedule(long period) {
            this.period = period;
            this.nextFireAt = lastFireAt + period;
        }
    }
}
