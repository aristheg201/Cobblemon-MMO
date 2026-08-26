package vn.svframe.svframelib.util.configobject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MapConfigObject implements ConfigObject {
    protected final String key;
    protected final Map<String,Object> values;
    public MapConfigObject(Map<String,?> values){this(null,values);}
    public MapConfigObject(String key,Map<String,?> values){this.key=key;this.values=new LinkedHashMap<>();if(values!=null)values.forEach((k,v)->this.values.put(k,v));}
    protected Object value(String key){return values.get(key);}
    @Override public String getString(String key){Object v=value(key);if(v==null)throw new MissingArgumentException(key);return String.valueOf(v);}
    @Override public String getString(String key,String fallback){Object v=value(key);return v==null?fallback:String.valueOf(v);}
    @Override public double getDouble(String key){Object v=value(key);if(v==null)throw new MissingArgumentException(key);return asDouble(v);}
    @Override public double getDouble(String key,double fallback){Object v=value(key);return v==null?fallback:asDouble(v);}
    @Override public int getInt(String key){Object v=value(key);if(v==null)throw new MissingArgumentException(key);return asInt(v);}
    @Override public int getInt(String key,int fallback){Object v=value(key);return v==null?fallback:asInt(v);}
    @Override public float getFloat(String key){Object v=value(key);if(v==null)throw new MissingArgumentException(key);return (float)asDouble(v);}
    @Override public float getFloat(String key,float fallback){Object v=value(key);return v==null?fallback:(float)asDouble(v);}
    @Override public boolean getBoolean(String key){Object v=value(key);if(v==null)throw new MissingArgumentException(key);return asBoolean(v);}
    @Override public boolean getBoolean(String key,boolean fallback){Object v=value(key);return v==null?fallback:asBoolean(v);}
    @Override public String string(String... keys){for(String k:keys)if(contains(k))return getString(k);throw new MissingArgumentException(keys);}
    @Override public String stringFb(String fallback,String... keys){for(String k:keys)if(contains(k))return getString(k);return Objects.requireNonNull(fallback);}
    @Override public double dble(String...keys){for(String k:keys)if(contains(k))return getDouble(k);throw new MissingArgumentException(keys);}
    @Override public double dble(double fallback,String...keys){for(String k:keys)if(contains(k))return getDouble(k);return fallback;}
    @Override public float flpt(String...keys){for(String k:keys)if(contains(k))return getFloat(k);throw new MissingArgumentException(keys);}
    @Override public float flpt(float fallback,String...keys){for(String k:keys)if(contains(k))return getFloat(k);return fallback;}
    @Override public int integer(String...keys){for(String k:keys)if(contains(k))return getInt(k);throw new MissingArgumentException(keys);}
    @Override public int integer(int fallback,String...keys){for(String k:keys)if(contains(k))return getInt(k);return fallback;}
    @Override public boolean bool(String...keys){for(String k:keys)if(contains(k))return getBoolean(k);throw new MissingArgumentException(keys);}
    @Override public boolean bool(boolean fallback,String...keys){for(String k:keys)if(contains(k))return getBoolean(k);return fallback;}
    @Override public ConfigObject getObject(String key){Object v=value(key);if(v==null)throw new MissingArgumentException(key);if(v instanceof ConfigObject c)return c;if(v instanceof Map<?,?> m)return fromMap(key,m);throw new IllegalArgumentException("Not an object: "+key);}
    @Override public ConfigObject adaptObject(String key){Object v=value(key);if(v==null)throw new MissingArgumentException(key);if(v instanceof ConfigObject c)return c;if(v instanceof Map<?,?> m)return fromMap(key,m);return new MapConfigObject(key,Map.of("type",String.valueOf(v)));}
    @Override public boolean contains(String key){return values.containsKey(key);}
    @Override public Set<String> getKeys(){return Set.copyOf(values.keySet());}
    @Override public String getKey(){return key;}
    public Map<String,Object> asMap(){return Map.copyOf(values);}
    private static MapConfigObject fromMap(String key,Map<?,?> raw){Map<String,Object> map=new LinkedHashMap<>();raw.forEach((k,v)->map.put(String.valueOf(k),v));return new MapConfigObject(key,map);}
    private static double asDouble(Object v){return v instanceof Number n?n.doubleValue():Double.parseDouble(String.valueOf(v).trim());}
    private static int asInt(Object v){return v instanceof Number n?n.intValue():Integer.parseInt(String.valueOf(v).trim());}
    private static boolean asBoolean(Object v){if(v instanceof Boolean b)return b;String s=String.valueOf(v).trim().toLowerCase(Locale.ROOT);return switch(s){case"true","yes","on","1"->true;case"false","no","off","0"->false;default->throw new IllegalArgumentException("Not a boolean: "+v);};}
}
