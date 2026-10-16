package vn.svframe.skript.lang;

import java.util.*;

public final class Values {
    private Values(){}
    public interface Copyable {Object copy();}
    public record Span(double milliseconds) {
        public long ticks(){return Math.max(1,(long)Math.ceil(milliseconds/50));}
        @Override public String toString(){return Syntax.formatNumber(milliseconds/1000)+" seconds";}
    }
    public record Date(long milliseconds){}
    public record GameTime(int ticks){
        public GameTime{ticks=Math.floorMod(ticks,24000);}
        public static GameTime parse(String text){
            String[] parts=text.split(":");int hour=Integer.parseInt(parts[0]),minute=Integer.parseInt(parts[1]);
            if(hour<0||hour>23||minute<0||minute>59)throw new IllegalArgumentException("Invalid Minecraft clock time: "+text);
            return new GameTime((int)Math.round(hour*1000d-6000+minute*1000d/60));
        }
        @Override public String toString(){int clock=(ticks+6000)%24000,hour=clock/1000,minute=(int)Math.round(clock%1000*60d/1000);if(minute==60){hour=(hour+1)%24;minute=0;}return String.format(Locale.ROOT,"%d:%02d",hour,minute);}
    }
    /** Stable references resolve the current entity after login, respawn and dimension changes. */
    public record EntityRef(UUID id,boolean player){}
    public record WorldRef(String id){}
    public record Location(WorldRef world,double x,double y,double z,float yaw,float pitch){}
    public static double number(Object value) {
        if(value instanceof Number n)return n.doubleValue();
        if(value instanceof Span s)return s.milliseconds;
        throw new IllegalArgumentException("Expected number, got "+text(value));
    }
    public static boolean truth(Object v){return Boolean.TRUE.equals(v);}
    public static List<Object> list(Object value){if(value==null)return List.of();if(value instanceof Collection<?> c)return new ArrayList<>(c);return List.of(value);}
    public static String text(Object v){
        if(v==null)return "<none>";
        if(v instanceof Double d)return Syntax.formatNumber(d);
        if(v instanceof Float f)return Syntax.formatNumber(f);
        if(v instanceof List<?> l)return String.join(", ",l.stream().map(Values::text).toList());
        return String.valueOf(v);
    }
    public static boolean equal(Object a,Object b){
        if(a==null||b==null)return false;
        if(a instanceof Number&&b instanceof Number)return Double.compare(number(a),number(b))==0;
        if(a instanceof String x&&b instanceof String y)return x.equalsIgnoreCase(y);
        return Objects.equals(a,b);
    }
    public static int compare(Object a,Object b){
        if(a instanceof GameTime x&&b instanceof GameTime y)return Integer.compare(x.ticks(),y.ticks());
        if(a instanceof Date x&&b instanceof Date y)return Long.compare(x.milliseconds,y.milliseconds);
        if((a instanceof Number||a instanceof Span)&&(b instanceof Number||b instanceof Span))return Double.compare(number(a),number(b));
        if(a instanceof String x&&b instanceof String y)return x.compareToIgnoreCase(y);
        throw new IllegalArgumentException("Values cannot be ordered: "+text(a)+", "+text(b));
    }
    public static Object copy(Object v){
        if(v instanceof Copyable c)return c.copy();
        if(v instanceof List<?> list){List<Object> out=new ArrayList<>();for(Object item:list)out.add(copy(item));return out;}
        if(v instanceof Map<?,?> m){Map<String,Object> out=new LinkedHashMap<>();m.forEach((k,x)->out.put(k.toString(),copy(x)));return out;}
        return v;
    }
}
