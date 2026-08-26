package vn.svframe.svframelib.script.targeter.location;

import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.script.targeter.LocationTargeter;
import vn.svframe.svframelib.script.util.expression.numeric.NumericExpression;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.List;

@Orientable
public class CustomLocationTargeter extends LocationTargeter {
    private final NumericExpression x,y,z; private final boolean relative,source;
    public CustomLocationTargeter(ConfigObject config){super(config);config.validateKeys("x","y","z");x=NumericExpression.compile(config.getString("x"));y=NumericExpression.compile(config.getString("y"));z=NumericExpression.compile(config.getString("z"));relative=config.getBoolean("relative",true);source=config.getBoolean("source",false);}
    @Override public List<Vec3d> findTargets(SkillMetadata meta){Vec3d base=relative?meta.getSkillLocation(source):Vec3d.ZERO;return List.of(base.add(x.evaluate(meta),y.evaluate(meta),z.evaluate(meta)));}
}
