package vn.svframe.svframelib.script.targeter;

import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.script.targeter.location.Orientable;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.List;

/** SVFrameLib 1.7.1 location targeter base with orientation contract. */
public abstract class LocationTargeter {
    private final boolean oriented;

    protected LocationTargeter(ConfigObject config) {
        this(config.getBoolean("oriented", false));
    }

    protected LocationTargeter(boolean oriented) {
        this.oriented = oriented;
        if (oriented && !getClass().isAnnotationPresent(Orientable.class))
            throw new IllegalArgumentException("Tried creating an oriented location targeter with a non orientable type");
    }

    protected final boolean isOriented() { return oriented; }
    public abstract List<Vec3d> findTargets(SkillMetadata metadata);
}
