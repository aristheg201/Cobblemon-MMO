package vn.svframe.svframelib.api.event.skill;

import vn.svframe.svframelib.skill.Skill;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.result.SkillResult;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Fabric-native post-cast event. */
public class SkillCastEvent extends PlayerSkillEvent {
    @FunctionalInterface
    public interface Listener {
        void onSkillCast(SkillCastEvent event);
    }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(
            Listener.class,
            listeners -> event -> {
                for (Listener listener : listeners) listener.onSkillCast(event);
            });

    public SkillCastEvent(Skill skill, SkillMetadata skillMeta, SkillResult result) {
        super(skill, skillMeta, result);
    }

    public SkillCastEvent call() {
        EVENT.invoker().onSkillCast(this);
        return this;
    }
}
