package vn.svframe.fantasyhub.client;

import java.util.*;
import net.minecraft.util.math.Vec3d;

/** Samples actual movement; stationary frames and teleports cannot create standing ghost stacks. */
final class HeroMotionHistory {
 static final int MAX_SAMPLES=6,MAX_AGE=9;
 record Sample(Vec3d at,float yaw,long tick){}
 private final ArrayDeque<Sample> samples=new ArrayDeque<>();
 private Vec3d last;
 private long lastTick=Long.MIN_VALUE;
 private double speed;
 void add(Vec3d at,float yaw,long tick){
  if(tick==lastTick)return;
  samples.removeIf(s->tick-s.tick()>MAX_AGE);
  if(last!=null){
   double distance=at.distanceTo(last);speed=distance;
   if(distance>8){samples.clear();speed=0;}
   else if(distance>=.12){samples.addFirst(new Sample(last,yaw,tick-1));while(samples.size()>MAX_SAMPLES)samples.removeLast();}
   else if(distance<.035){samples.clear();speed=0;}
  }
  last=at;lastTick=tick;
 }
 double speed(){return speed;}
 List<Sample> samples(){return List.copyOf(samples);}
}
