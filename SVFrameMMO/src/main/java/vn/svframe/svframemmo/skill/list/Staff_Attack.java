package vn.svframe.svframemmo.skill.list;

import vn.svframe.svframelib.skill.handler.BuiltinSkillHandler;
import vn.svframe.svframelib.skill.handler.ScriptSkillHandler;
import vn.svframe.svframelib.util.configobject.ConfigObject;

/** MMOCore's Staff Attack backed by SVFrameLib's native Fabric script runtime. */
@BuiltinSkillHandler
public final class Staff_Attack extends ScriptSkillHandler {
    public Staff_Attack() { super("STAFF_ATTACK"); }
    public Staff_Attack(ConfigObject config) { super("STAFF_ATTACK", config); }
}
