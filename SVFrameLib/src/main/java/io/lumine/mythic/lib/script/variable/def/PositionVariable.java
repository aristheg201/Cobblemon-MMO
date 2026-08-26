package io.lumine.mythic.lib.script.variable.def;

import io.lumine.mythic.lib.script.variable.*;
import io.lumine.mythic.lib.util.Position;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class PositionVariable extends Variable<Position> {
    public static final SimpleVariableRegistry<Position> VARIABLE_REGISTRY = new SimpleVariableRegistry<>();
    static {
        VARIABLE_REGISTRY.registerVariable("x", p -> new DoubleVariable("temp",p.getX()));
        VARIABLE_REGISTRY.registerVariable("y", p -> new DoubleVariable("temp",p.getY()));
        VARIABLE_REGISTRY.registerVariable("z", p -> new DoubleVariable("temp",p.getZ()));
        VARIABLE_REGISTRY.registerVariable("yaw", p -> new DoubleVariable("temp",yawPitch(p.toVector())[0]));
        VARIABLE_REGISTRY.registerVariable("pitch", p -> new DoubleVariable("temp",yawPitch(p.toVector())[1]));
        VARIABLE_REGISTRY.registerVariable("length", p -> new DoubleVariable("temp",p.length()),"norm","len");
        VARIABLE_REGISTRY.registerVariable("world", p -> new WorldVariable("temp",p.getWorld()));
        VARIABLE_REGISTRY.registerVariable("biome", p -> new StringVariable("temp",p.getWorld().getBiome(p.toBlockPos()).getKey().map(k->k.getValue().toString()).orElse("unknown")));
        VARIABLE_REGISTRY.registerVariable("altitude", p -> new DoubleVariable("temp",altitude(p)));
    }
    public PositionVariable(String name, Position value){super(name,value);}
    public PositionVariable(String name, ServerWorld world, Vec3d value){this(name,new Position(world,value));}
    @Override public VariableRegistry<Variable<Position>> getVariableRegistry(){return VARIABLE_REGISTRY;}
    private static double[] yawPitch(Vec3d v){
        double x=v.x,z=v.z;
        if(Math.abs(x)<1e-12&&Math.abs(z)<1e-12)return new double[]{0d,v.y>0?-90d:90d};
        double yaw=Math.toDegrees((Math.atan2(-x,z)+Math.PI*2d)%(Math.PI*2d));
        double xz=Math.sqrt(x*x+z*z); double pitch=Math.toDegrees(Math.atan(-v.y/xz));
        return new double[]{yaw,pitch};
    }
    private static double altitude(Position p){
        BlockPos.Mutable pos=p.toBlockPos().mutableCopy(); int min=p.getWorld().getBottomY();
        while(pos.getY()>min&&!p.getWorld().getBlockState(pos).isSolidBlock(p.getWorld(),pos))pos.move(0,-1,0);
        return p.getY()-pos.getY()-1d;
    }
}
