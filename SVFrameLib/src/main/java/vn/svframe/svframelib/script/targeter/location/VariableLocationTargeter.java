package vn.svframe.svframelib.script.targeter.location;

import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.script.targeter.LocationTargeter;
import vn.svframe.svframelib.script.variable.Variable;
import vn.svframe.svframelib.script.variable.def.PositionVariable;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.List;

@Orientable
public class VariableLocationTargeter extends LocationTargeter {
    private final String name;
    public VariableLocationTargeter(ConfigObject config) { super(config); config.validateKeys("name"); name = config.getString("name"); }
    @Override public List<Vec3d> findTargets(SkillMetadata meta) {
        Variable<?> var = meta.getVariable(name);
        if (!(var instanceof PositionVariable pos) || pos.getStored() == null) throw new IllegalArgumentException("Variable '" + name + "' is not a vector");
        return List.of(pos.getStored().toVector());
    }
}
