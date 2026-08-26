package vn.svframe.svframelib.script.targeter.location;

import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.script.targeter.LocationTargeter;
import vn.svframe.svframelib.script.targeter.TargeterMath;
import vn.svframe.svframelib.script.util.expression.numeric.NumericExpression;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.ArrayList;
import java.util.List;

@Orientable
public class CircleLocationTargeter extends LocationTargeter {
    private final boolean source; private final NumericExpression radius,amount;
    public CircleLocationTargeter(ConfigObject config){super(config);config.validateKeys("radius","amount");source=config.getBoolean("source",false);radius=NumericExpression.compile(config.getString("radius"));amount=NumericExpression.compile(config.getString("amount"));}
    @Override public List<Vec3d> findTargets(SkillMetadata meta){Vec3d origin=meta.getSkillLocation(source);int count=(int)amount.evaluate(meta);if(count<0)throw new IllegalArgumentException("Amount cannot be negative");double r=radius.evaluate(meta),step=Math.PI*2d/(double)count;List<Vec3d> out=new ArrayList<>(count);
        Vec3d axis=isOriented()?meta.getTargetLocation().subtract(meta.getSourceLocation()):new Vec3d(0,1,0);for(int i=0;i<count;i++){Vec3d v=new Vec3d(r*Math.cos(i*step),0,r*Math.sin(i*step));if(isOriented())v=TargeterMath.rotate(v,axis);out.add(origin.add(v));}return out;}
}
