package vn.svframe.svframelib.fabric.runtime;

import vn.svframe.svframelib.fabric.PassiveSkillRuntime;

/** Runtime attachment contract implemented on projectile entities by the snapshot mixin. */
public interface ProjectilePassiveSnapshotHolder {
    PassiveSkillRuntime.Snapshot svframelib$getPassiveSnapshot();
    void svframelib$setPassiveSnapshot(PassiveSkillRuntime.Snapshot snapshot);
}
