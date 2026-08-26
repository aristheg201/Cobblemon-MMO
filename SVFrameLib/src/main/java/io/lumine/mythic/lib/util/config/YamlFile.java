package io.lumine.mythic.lib.util.config;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.module.MMOPlugin;
import vn.svframe.compat.YamlLite;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class YamlFile extends ConfigFile<Map<String,Object>> {
    public YamlFile(String name){ this(MythicLib.plugin, "", name, true); }
    public YamlFile(String path, String name){ this(MythicLib.plugin, path, name, true); }
    public YamlFile(MMOPlugin plugin, String name){ this(plugin, "", name, true); }
    public YamlFile(MMOPlugin plugin, String path, String name){ this(plugin, path, name, true); }
    public YamlFile(MMOPlugin plugin, String path, String name, boolean load){ super(plugin,path,name); if(load) reload(); }
    public YamlFile(MMOPlugin plugin, String path, String name, Map<String,Object> content){ super(plugin,path,name); setContent(content == null ? new LinkedHashMap<>() : new LinkedHashMap<>(content)); }
    public final void reload(){
        try {
            if(exists()) {
                Object parsed=YamlLite.parse(getPath());
                setContent(parsed instanceof Map<?,?> m ? cast(m) : new LinkedHashMap<>());
            } else setContent(new LinkedHashMap<>());
        } catch(IOException e){ throw new RuntimeException("Could not load "+getPath(),e); }
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> cast(Map<?,?> raw){
        Map<String,Object> out=new LinkedHashMap<>(); raw.forEach((k,v)->out.put(String.valueOf(k),v)); return out;
    }
    @Override public void save(){
        try { Files.createDirectories(getPath().getParent()); Files.writeString(getPath(), dump(getContent()==null?Map.of():getContent())); }
        catch(IOException e){ throw new RuntimeException("Could not save "+getPath(),e); }
    }
    public static YamlFile fromJarFile(MMOPlugin plugin,String path,String name){
        YamlFile file=new YamlFile(plugin,path,name,false);
        if(!file.exists()) file.save();
        else file.reload();
        return file;
    }
    public static String dump(Object value){ StringBuilder out=new StringBuilder(); emit(out,value,0); return out.toString(); }
    private static void emit(StringBuilder out,Object value,int indent){
        String pad=" ".repeat(indent);
        if(value instanceof Map<?,?> map){
            for(var e:map.entrySet()){
                out.append(pad).append(e.getKey()).append(':');
                Object v=e.getValue();
                if(v instanceof Map<?,?> || v instanceof Collection<?>){ out.append('\n'); emit(out,v,indent+2); }
                else out.append(' ').append(scalar(v)).append('\n');
            }
        } else if(value instanceof Collection<?> list){
            for(Object v:list){
                out.append(pad).append("- ");
                if(v instanceof Map<?,?> || v instanceof Collection<?>){ out.append('\n'); emit(out,v,indent+2); }
                else out.append(scalar(v)).append('\n');
            }
        }
    }
    private static String scalar(Object v){
        if(v==null)return "null";
        if(v instanceof Number || v instanceof Boolean)return String.valueOf(v);
        String s=String.valueOf(v);
        if(s.isEmpty() || s.matches(".*[:#\\[\\]{},&*!|>'\"%@`\\s].*")) return "'"+s.replace("'","''")+"'";
        return s;
    }
}
