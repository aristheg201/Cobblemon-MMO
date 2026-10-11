package vn.svframe.skript.lang;

import java.util.*;

/** Skript scalar and structured-list variables. Local variables never enter the persistent store. */
public final class Variables {
    private final NavigableMap<String,Object> globals=new TreeMap<>();
    private Runnable onChange=()->{};
    private java.util.function.Consumer<Object> validator=value->{};
    public void validateWith(java.util.function.Consumer<Object> validator){this.validator=Objects.requireNonNull(validator);}
    public void onChange(Runnable listener){onChange=Objects.requireNonNull(listener);}
    public void restore(Map<String,Object> data){globals.clear();data.forEach((k,v)->globals.put(key(k),Values.copy(v)));}
    public Map<String,Object> snapshot(){Map<String,Object> out=new LinkedHashMap<>();globals.forEach((k,v)->out.put(k,Values.copy(v)));return out;}
    private static String key(String name){return name.toLowerCase(Locale.ROOT);}
    public Map<String,Object> capture(String name){String normalized=key(name);Map<String,Object> out=new LinkedHashMap<>();if(normalized.endsWith("::*"))range(normalized,Map.of()).forEach((k,v)->out.put(k,Values.copy(v)));else if(globals.containsKey(normalized))out.put(normalized,Values.copy(globals.get(normalized)));return out;}
    public void restore(String name,Map<String,Object> before){String normalized=key(name);if(normalized.endsWith("::*"))range(normalized,Map.of()).clear();else globals.remove(normalized);before.forEach((k,v)->globals.put(k,Values.copy(v)));onChange.run();}
    private Map<String,Object> scope(String name,Map<String,Object> local){return name.startsWith("_")?local:globals;}
    private Map<String,Object> range(String name,Map<String,Object> local){Map<String,Object> map=scope(name,local);if(map==globals){String prefix=key(name.substring(0,name.length()-1));return globals.subMap(prefix,true,prefix.substring(0,prefix.length()-1)+";",false);}return map;}
    public Object get(String name,Map<String,Object> local){
        name=key(name);Map<String,Object> map=scope(name,local);
        if(!name.endsWith("::*"))return Values.copy(map.get(name));
        return entries(name,local).values().stream().map(Values::copy).toList();
    }
    public Map<String,Object> entries(String name,Map<String,Object> local){
        String prefix=key(name.substring(0,name.length()-1));Map<String,Object> out=new TreeMap<>(Variables::indexOrder);
        range(name,local).forEach((k,v)->{if(k.startsWith(prefix)){String rest=k.substring(prefix.length());if(!rest.contains("::"))out.put(rest,v);}});
        return out;
    }
    private static Long indexNumber(String key){try{return Long.parseLong(key);}catch(NumberFormatException ignored){return null;}}
    private static int indexOrder(String a,String b){Long x=indexNumber(a),y=indexNumber(b);if(x==null&&y==null)return a.compareTo(b);if(x==null)return 1;if(y==null)return -1;int order=Long.compare(x,y);return order==0?a.compareTo(b):order;}
    public void set(String name,Object value,Map<String,Object> local){
        name=key(name);Map<String,Object> map=scope(name,local);
        if(!name.startsWith("_")&&value!=null){if(name.endsWith("::*"))Values.list(value).stream().filter(Objects::nonNull).forEach(validator);else validator.accept(value);}
        if(name.endsWith("::*")){
            String prefix=name.substring(0,name.length()-1);if(map==globals)range(name,local).clear();else map.keySet().removeIf(k->k.startsWith(prefix));
            int index=1;for(Object v:Values.list(value))if(v!=null)map.put(prefix+index++,Values.copy(v));
        }else if(value==null)map.remove(name);else map.put(name,Values.copy(value));
        if(!name.startsWith("_"))onChange.run();
    }
    public void add(String name,Object value,Map<String,Object> local){
        if(!name.endsWith("::*")){Object old=get(name,local);set(name,(old==null?0:Values.number(old))+Values.number(value),local);return;}
        long index=0;for(String key:entries(name,local).keySet())try{index=Math.max(index,Long.parseLong(key));}catch(NumberFormatException ignored){}
        String prefix=name.substring(0,name.length()-1);for(Object item:Values.list(value))if(item!=null)set(prefix+Math.addExact(index++,1),item,local);
    }
    public void remove(String name,Object value,Map<String,Object> local){
        if(!name.endsWith("::*")){Object old=get(name,local);set(name,(old==null?0:Values.number(old))-Values.number(value),local);return;}
        Map<String,Object> map=range(name,local);String prefix=key(name.substring(0,name.length()-1));
        // Removing list values keeps the surviving indices, as in Skript.
        map.entrySet().removeIf(e->e.getKey().startsWith(prefix)&&Values.list(value).stream().anyMatch(v->Values.equal(v,e.getValue())));
        if(!name.startsWith("_"))onChange.run();
    }
}
