package vn.svframe.svframelib.api.event.skill;

import vn.svframe.svframelib.skill.Skill;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.result.SkillResult;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Fabric-native cancellable pre-cast event. */
public class PlayerCastSkillEvent extends PlayerSkillEvent {
    @FunctionalInterface
    public interface Listener {
        void onPlayerCastSkill(PlayerCastSkillEvent event);
    }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(
            Listener.class,
            listeners -> event -> {
                for (Listener listener : listeners) listener.onPlayerCastSkill(event);
            });

    private boolean cancelled;

    public PlayerCastSkillEvent(Skill skill, SkillMetadata skillMeta, SkillResult result) {
        super(skill, skillMeta, result);
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public PlayerCastSkillEvent call() {
        EVENT.invoker().onPlayerCastSkill(this);
        return this;
    }
}
