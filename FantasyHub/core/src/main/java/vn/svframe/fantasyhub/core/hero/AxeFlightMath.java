package vn.svframe.fantasyhub.core.hero;
import java.util.*;
/** Geometry and independent hit passes for a swept weapon flight. */
public final class AxeFlightMath {
 private AxeFlightMath(){}
 public static double distance(double requested){return Math.clamp(requested,0,20);}
 public static boolean touches(double ax,double ay,double az,double bx,double by,double bz,double px,double py,double pz,double radius){
  double x=bx-ax,y=by-ay,z=bz-az,l=x*x+y*y+z*z;
  double t=l<1e-12?0:Math.clamp(((px-ax)*x+(py-ay)*y+(pz-az)*z)/l,0,1);
  double dx=px-ax-t*x,dy=py-ay-t*y,dz=pz-az-t*z;return dx*dx+dy*dy+dz*dz<=radius*radius;
 }
 public static final class Hits {
  private final Set<String> outward=new HashSet<>(),returning=new HashSet<>();
  public boolean accept(String id,boolean recall){return (recall?returning:outward).add(id);}
 }
}
