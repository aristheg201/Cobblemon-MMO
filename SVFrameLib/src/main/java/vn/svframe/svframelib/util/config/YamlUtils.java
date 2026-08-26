package vn.svframe.svframelib.util.config;
import java.util.*;
import java.util.function.Function;

public final class YamlUtils {
    private YamlUtils(){}
    public static Object get(Map<String,?> section,String... keys){ if(section==null)return null; for(String k:keys) if(section.containsKey(k)) return section.get(k); return null; }
    public static String getString(Map<String,?> s,String...k){ Object v=get(s,k); return v==null?null:String.valueOf(v); }
    public static int getInt(Map<String,?> s,String...k){ Integer v=getInteger(s,k); return v==null?0:v; }
    public static Integer getInteger(Map<String,?> s,String...k){ Object v=get(s,k); if(v instanceof Number n)return n.intValue(); try{return v==null?null:Integer.valueOf(String.valueOf(v));}catch(Exception e){return null;} }
    public static boolean getBoolean(Map<String,?> s,String...k){ Boolean v=getBooleanObj(s,k); return v!=null&&v; }
    public static Boolean getBooleanObj(Map<String,?> s,String...k){ Object v=get(s,k); return v instanceof Boolean b?b:v==null?null:Boolean.valueOf(String.valueOf(v)); }
    public static boolean getBool(Map<String,?> s,String...k){ return getBoolean(s,k); }
    public static float getFloat(Map<String,?> s,String...k){ Float v=getFloatObj(s,k);return v==null?0:v; }
    public static Float getFloatObj(Map<String,?> s,String...k){ Object v=get(s,k);if(v instanceof Number n)return n.floatValue();try{return v==null?null:Float.valueOf(String.valueOf(v));}catch(Exception e){return null;} }
    public static double getDouble(Map<String,?> s,String...k){ Double v=getDoubleObj(s,k);return v==null?0:v; }
    public static Double getDoubleObj(Map<String,?> s,String...k){ Object v=get(s,k);if(v instanceof Number n)return n.doubleValue();try{return v==null?null:Double.valueOf(String.valueOf(v));}catch(Exception e){return null;} }
    public static List<String> getStringList(Map<String,?> s,String...k){ Object v=get(s,k); if(v instanceof Collection<?> c)return c.stream().map(String::valueOf).toList(); return v==null?List.of():List.of(String.valueOf(v)); }
    public static <T> boolean containsOneKey(Map<String,?> s,Iterable<T> keys){ for(T k:keys)if(s.containsKey(String.valueOf(k)))return true;return false; }
    public static <T extends Enum<?>> boolean containsOneKey(Map<String,?> s,T[] keys,Function<T,String> mapper){for(T k:keys)if(s.containsKey(mapper.apply(k)))return true;return false;}
}
