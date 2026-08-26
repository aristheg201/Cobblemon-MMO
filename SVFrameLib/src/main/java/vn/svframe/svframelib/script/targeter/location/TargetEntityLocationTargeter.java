package vn.svframe.svframelib.script.targeter.location;

import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.script.targeter.LocationTargeter;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.EntityLocationType;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.List;
import java.util.Locale;

public class TargetEntityLocationTargeter extends LocationTargeter {
    private final EntityLocationType locationType;
    public TargetEntityLocationTargeter(ConfigObject config) { super(false); locationType = config.contains("position") ? EntityLocationType.valueOf(config.getString("position").toUpperCase(Locale.ROOT)) : EntityLocationType.BODY; }
    @Override public List<Vec3d> findTargets(SkillMetadata meta) { return List.of(locationType.getLocation(meta.getTargetEntity())); }
}
