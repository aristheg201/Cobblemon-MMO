package vn.svframe.fantasyhub.core.hero;
/** Rope constraint keeps tangential velocity; vanilla remains responsible for gravity/collision. */
public final class TetherPhysics {
 private TetherPhysics(){}
 public static boolean attached(boolean jump,boolean sneak,boolean valid){return valid&&!jump&&!sneak;}
 public static double reel(double length){return Math.max(2,length-.18);}
 public static double[] velocity(double px,double py,double pz,double ax,double ay,double az,double length,double vx,double vy,double vz,boolean reel){
  double x=ax-px,y=ay-py,z=az-pz,d=Math.sqrt(x*x+y*y+z*z);if(d<.001)return new double[]{vx,vy,vz};
  if(d>length){x/=d;y/=d;z/=d;double outward=Math.min(0,vx*x+vy*y+vz*z);double pull=Math.min(.45,(d-length)*.22)-outward;
   vx+=x*pull;vy+=y*pull;vz+=z*pull;
  }
  double speed=Math.sqrt(vx*vx+vy*vy+vz*vz);if(speed>2.5){vx*=2.5/speed;vy*=2.5/speed;vz*=2.5/speed;}
  return new double[]{vx,vy,vz};
 }
}
