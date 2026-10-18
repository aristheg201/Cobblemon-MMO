package vn.svframe.fantasyhub.core.rider;

import java.util.*;

/** Per-caster Targeted marks. Another Zolda cannot spend or increase this caster's damage bonus. */
public final class ZoldaState {
    public record Mark(int stacks,long until){}
    private final LinkedHashMap<UUID,Mark> marks=new LinkedHashMap<>();
    private final int capacity;
    private double shield;
    private long shieldUntil;
    public long guardUntil, strikeUntil, finalUntil;
    public boolean transformed;
    public ZoldaState(){this(64);}
    public ZoldaState(int capacity){if(capacity<1)throw new IllegalArgumentException("No mark capacity");this.capacity=capacity;}
    public int mark(UUID target,long tick,int duration){
        Mark old=marks.get(target);int count=old!=null&&tick<old.until?Math.min(3,old.stacks+1):1;
        marks.remove(target);marks.put(target,new Mark(count,Math.addExact(tick,duration)));
        while(marks.size()>capacity)marks.remove(marks.keySet().iterator().next());return count;
    }
    public int stacks(UUID target,long tick){Mark mark=marks.get(target);return mark!=null&&tick<mark.until?mark.stacks:0;}
    public void expire(long tick){marks.values().removeIf(m->tick>=m.until);if(tick>=shieldUntil)shield=0;}
    public void shield(double maximumHealth,double fraction,long tick,int duration){
        if(!Double.isFinite(maximumHealth)||maximumHealth<0||!Double.isFinite(fraction)||fraction<0||fraction>1||duration<1)throw new IllegalArgumentException("Invalid barrier");
        shield=maximumHealth*fraction;shieldUntil=Math.addExact(tick,duration);strikeUntil=shieldUntil;
    }
    public double shield(long tick){return tick<shieldUntil?shield:0;}
    /** Remaining incoming damage, after this ability's own barrier has absorbed its share. */
    public double absorb(double damage,long tick){double spent=Math.min(Math.max(0,damage),shield(tick));shield-=spent;return damage-spent;}
    public void breakBarrier(){shield=0;}
    public boolean release(long tick){if(tick>=strikeUntil||strikeUntil==0)return false;strikeUntil=0;shield=0;return true;}
    public void clear(){marks.clear();shield=0;strikeUntil=guardUntil=finalUntil=0;transformed=false;}
    public static boolean inCone(double forwardX,double forwardZ,double deltaX,double deltaZ,double range,double degrees){
        double length=Math.hypot(deltaX,deltaZ),forward=Math.hypot(forwardX,forwardZ);
        return length<=range&&forward>0&&(length<1e-6||(forwardX*deltaX+forwardZ*deltaZ)/(length*forward)>=Math.cos(Math.toRadians(degrees/2))-1e-9);
    }
}
