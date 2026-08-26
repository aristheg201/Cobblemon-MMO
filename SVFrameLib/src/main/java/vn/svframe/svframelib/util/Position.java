package vn.svframe.svframelib.util;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;

/** World-aware mutable 3D position used by the MythicLib variable runtime. */
public class Position implements Cloneable {
    private final ServerWorld world;
    private double x, y, z;

    public Position(ServerWorld world, double x, double y, double z) {
        this.world = Objects.requireNonNull(world, "World cannot be null");
        this.x=x; this.y=y; this.z=z;
    }
    public Position(ServerWorld world, Vec3d vector) { this(world, vector.x, vector.y, vector.z); }
    public ServerWorld getWorld(){return world;}
    public double getX(){return x;} public double getY(){return y;} public double getZ(){return z;}
    public int getBlockX(){return (int)Math.floor(x);} public int getBlockY(){return (int)Math.floor(y);} public int getBlockZ(){return (int)Math.floor(z);}
    public Position setX(double value){x=value;return this;} public Position setY(double value){y=value;return this;} public Position setZ(double value){z=value;return this;}
    public Position add(Position other){return add(other.x,other.y,other.z);} public Position add(double dx,double dy,double dz){x+=dx;y+=dy;z+=dz;return this;}
    public Position subtract(Position other){x-=other.x;y-=other.y;z-=other.z;return this;}
    public Position multiply(double factor){x*=factor;y*=factor;z*=factor;return this;}
    public double lengthSquared(){return x*x+y*y+z*z;} public double length(){return Math.sqrt(lengthSquared());}
    public double distanceSquared(Position other){double dx=x-other.x,dy=y-other.y,dz=z-other.z;return dx*dx+dy*dy+dz*dz;}
    public double distance(Position other){return Math.sqrt(distanceSquared(other));}
    public double dot(Position other){return x*other.x+y*other.y+z*other.z;}
    public float angle(Position other){double den=length()*other.length(); if(den<1e-12)return 0f; return (float)Math.acos(Math.max(-1d,Math.min(1d,dot(other)/den)));}
    public Position normalize(){double len=length(); if(len>1e-12)multiply(1d/len); return this;}
    public Vec3d toVector(){return new Vec3d(x,y,z);} public BlockPos toBlockPos(){return BlockPos.ofFloored(x,y,z);}
    @Override public Position clone(){return new Position(world,x,y,z);}
    @Override public boolean equals(Object o){if(!(o instanceof Position p))return false;return world==p.world&&Math.abs(x-p.x)<1e-6&&Math.abs(y-p.y)<1e-6&&Math.abs(z-p.z)<1e-6;}
    @Override public int hashCode(){return Objects.hash(System.identityHashCode(world),x,y,z);}
    @Override public String toString(){return world.getRegistryKey().getValue()+"["+x+","+y+","+z+"]";}
}
