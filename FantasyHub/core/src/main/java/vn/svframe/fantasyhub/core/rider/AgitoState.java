package vn.svframe.fantasyhub.core.rider;

/** Timed forms and six-point Infinite Force. No client-provided counters or wall-clock timers. */
public final class AgitoState {
    public enum Form { GROUND, STORM, FLAME, TRINITY, SHINING }
    private Form stance=Form.GROUND, elevated;
    private long elevatedUntil;
    private int force;
    private boolean transformed;
    public boolean transformed(){return transformed;}
    public boolean henshin(){transformed=!transformed;if(!transformed){elevated=null;force=0;}return transformed;}
    public void clear(){transformed=false;elevated=null;force=0;}
    public void stance(Form form){if(form!=Form.GROUND&&form!=Form.STORM&&form!=Form.FLAME)throw new IllegalArgumentException("Not a base stance");stance=form;}
    public Form form(long tick){return elevated!=null&&tick<elevatedUntil?elevated:stance;}
    public void elevate(Form form,long tick,int duration){
        if(form!=Form.TRINITY&&form!=Form.SHINING)throw new IllegalArgumentException("Not an elevated form");
        if(duration<=0)throw new IllegalArgumentException("Non-positive form duration");
        elevated=form;elevatedUntil=Math.addExact(tick,duration);
    }
    public int remaining(long tick){return elevated==null?0:(int)Math.max(0,elevatedUntil-tick);}
    public int force(){return force;}
    /** Returns true only on the edge at which the horns expand. */
    public boolean gain(){if(!transformed||force==6)return false;return ++force==6;}
    public boolean consume(){if(force!=6)return false;force=0;return true;}
}
