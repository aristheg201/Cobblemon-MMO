package vn.svframe.svframelib.script.mechanic.type;

import vn.svframe.svframelib.MythicLib;
import vn.svframe.svframelib.script.mechanic.Mechanic;
import vn.svframe.svframelib.script.targeter.EntityTargeter;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import net.minecraft.entity.Entity;

/** Entity-targeting mechanic with the same target-selection contract as 1.7.1. */
public abstract class TargetMechanic extends Mechanic {
    private final EntityTargeter targeter;

    public TargetMechanic(ConfigObject config) {
        this.targeter = config != null && config.contains("target")
                ? MythicLib.plugin.getSkills().loadEntityTargeter(config.adaptObject("target"))
                : null; // null is the native equivalent of DefaultEntityTargeter: metadata target.
    }

    public EntityTargeter getTargeter() { return targeter; }

    @Override
    public void cast(SkillMetadata metadata) {
        if (targeter == null) {
            Entity target = metadata.getTargetEntityOrNull();
            if (target != null) cast(metadata, target);
            return;
        }
        var server = vn.svframe.mythiclibfabric.MythicLibFabricMod.server();
        if (server == null) return;
        for (java.util.UUID id : targeter.findTargets(metadata)) {
            for (var world : server.getWorlds()) {
                Entity entity = world.getEntity(id);
                if (entity != null) { cast(metadata, entity); break; }
            }
        }
    }

    public abstract void cast(SkillMetadata metadata, Entity target);
}
