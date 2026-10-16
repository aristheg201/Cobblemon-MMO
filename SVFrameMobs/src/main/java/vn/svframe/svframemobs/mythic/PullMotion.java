package vn.svframe.svframemobs.mythic;

import static vn.svframe.svframemobs.mythic.MythicEngine.Point;

/** Native equivalent of the supplied Mythic 5.6.2 pull velocity, including its vertical falloff. */
final class PullMotion {
    record Motion(double x,double y,double z) {}
    private PullMotion() {}
    static Motion towards(Point target,Point destination,double velocity) {
        if(!target.world().equals(destination.world())||!Double.isFinite(velocity))
            throw new IllegalArgumentException("Pull requires a finite velocity and the same world");
        double dx=destination.x()-target.x(),dy=destination.y()-target.y(),dz=destination.z()-target.z();
        // Mythic's configured units are tenths of a block; the distance-weighted
        // horizontal and vertical factors differ. A constant normalized shove
        // loses the source behavior near the centre of a black hole.
        double speed=velocity/10,squared=speed*speed;
        double x=dx*.5*squared,y=dy*.34*squared*(dy==0?1:Math.abs(dy)*.5),z=dz*.5*squared;
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))
            throw new IllegalArgumentException("Pull geometry exceeds finite movement bounds");
        double length=Math.hypot(Math.hypot(x,y),z),scale=length>4?4/length:1;
        return new Motion(x*scale,y*scale,z*scale);
    }
}
