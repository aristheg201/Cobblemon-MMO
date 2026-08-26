package vn.svframe.svframelib.script.targeter;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Geometry helpers copied from the semantics used by MythicLib targeters. */
public final class TargeterMath {
    private TargeterMath() {}

    public static double angle(Vec3d a, Vec3d b) {
        double den = a.length() * b.length();
        if (den < 1.0E-12) return 0d;
        return Math.acos(Math.max(-1d, Math.min(1d, a.dotProduct(b) / den)));
    }

    public static Vec3d rotate(Vec3d vector, Vec3d axis) {
        double[] yp = yawPitch(axis);
        return rotate(vector, Math.toRadians(yp[0]), Math.toRadians(yp[1]));
    }

    public static Vec3d rotate(Vec3d vector, double yaw, double pitch) {
        double cy = Math.cos(pitch), sy = Math.sin(pitch);
        double y = vector.y * cy - vector.z * sy;
        double z = vector.y * sy + vector.z * cy;
        Vec3d xRot = new Vec3d(vector.x, y, z);
        double angle = -yaw, c = Math.cos(angle), s = Math.sin(angle);
        double x = xRot.x * c + xRot.z * s;
        double rz = xRot.x * -s + xRot.z * c;
        return new Vec3d(x, xRot.y, rz);
    }

    public static double[] yawPitch(Vec3d axis) {
        double x = axis.x, z = axis.z;
        if (x == 0d && z == 0d) return new double[]{0d, axis.y > 0d ? -90d : 90d};
        double theta = Math.atan2(-x, z);
        double yaw = Math.toDegrees((theta + Math.PI * 2d) % (Math.PI * 2d));
        double xz = Math.sqrt(x * x + z * z);
        double pitch = Math.toDegrees(Math.atan(-axis.y / xz));
        return new double[]{yaw, pitch};
    }

    /** Returns entry t in [0,1] for a segment/box hit, or POSITIVE_INFINITY. */
    public static double segmentBoxHit(Vec3d start, Vec3d end, Box box) {
        double[] s = {start.x,start.y,start.z}; double[] d = {end.x-start.x,end.y-start.y,end.z-start.z};
        double[] mn = {box.minX,box.minY,box.minZ}; double[] mx = {box.maxX,box.maxY,box.maxZ};
        double t0=0d,t1=1d;
        for(int i=0;i<3;i++){
            if(Math.abs(d[i])<1e-12){ if(s[i]<mn[i]||s[i]>mx[i]) return Double.POSITIVE_INFINITY; continue; }
            double a=(mn[i]-s[i])/d[i], b=(mx[i]-s[i])/d[i]; if(a>b){double tmp=a;a=b;b=tmp;}
            t0=Math.max(t0,a); t1=Math.min(t1,b); if(t0>t1)return Double.POSITIVE_INFINITY;
        }
        return t0;
    }
}
