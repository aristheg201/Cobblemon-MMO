package io.lumine.mythic.lib.util;
import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.util.config.YamlFile;
import vn.svframe.compat.YamlLite;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

public final class FileUtils {
    private FileUtils(){}
    public static <T> void iterateConfigSectionList(Map<String,?> section,List<T> output,Function<Map<String,Object>,T> mapper,Function<Integer,T> integerMapper,BiConsumer<String,RuntimeException> error){
        if(section==null)return; for(var e:section.entrySet()) try{
            Object v=e.getValue(); if(v instanceof Map<?,?> m)output.add(mapper.apply(copy(m))); else if(v instanceof Number n)output.add(integerMapper.apply(n.intValue()));
        }catch(RuntimeException ex){if(error!=null)error.accept(e.getKey(),ex);else throw ex;}
    }
    public static void loadSingleObjectsFromFolder(MMOPlugin plugin,String folder,BiConsumer<String,Map<String,Object>> consumer,String extension){
        loadFiles(plugin,folder,extension,file->{try{Object x=YamlLite.parse(file);if(x instanceof Map<?,?>m)consumer.accept(strip(file.getFileName().toString()),copy(m));}catch(IOException e){throw new UncheckedIOException(e);}});
    }
    public static void loadObjectsFromFolder(MMOPlugin plugin,String folder,BiConsumer<String,Map<String,Object>> consumer,String extension){loadObjectsFromFolder(plugin,folder,true,consumer,extension);}
    public static void loadObjectsFromFolder(MMOPlugin plugin,String folder,boolean recursive,BiConsumer<String,Map<String,Object>> consumer,String extension){
        loadFiles(plugin,folder,extension,file->{try{Object x=YamlLite.parse(file);if(x instanceof Map<?,?>m)for(var e:m.entrySet())if(e.getValue() instanceof Map<?,?> sub)consumer.accept(String.valueOf(e.getKey()),copy(sub));}catch(IOException e){throw new UncheckedIOException(e);}},recursive);
    }
    public static void loadRawObjectsFromFolder(MMOPlugin plugin,String folder,Consumer<File> consumer,String extension){loadFiles(plugin,folder,extension,p->consumer.accept(p.toFile()));}
    public static void exploreFolderRecursively(File folder,Consumer<File> consumer){if(folder==null||!folder.exists())return;try(var s=Files.walk(folder.toPath())){s.filter(Files::isRegularFile).forEach(p->consumer.accept(p.toFile()));}catch(IOException e){throw new UncheckedIOException(e);}}
    public static File getFile(MMOPlugin plugin,String path){return root(plugin).resolve(path).toFile();}
    public static boolean moveIfExists(MMOPlugin plugin,String oldPath,String newPath){try{Path a=root(plugin).resolve(oldPath),b=root(plugin).resolve(newPath);if(!Files.exists(a))return false;Files.createDirectories(b.getParent());Files.move(a,b,StandardCopyOption.REPLACE_EXISTING);return true;}catch(IOException e){throw new UncheckedIOException(e);}}
    public static void copyDefaultFile(MMOPlugin plugin,String path){try{Path p=root(plugin).resolve(path);if(!Files.exists(p)){Files.createDirectories(p.getParent());Files.createFile(p);}}catch(IOException e){throw new UncheckedIOException(e);}}
    private static void loadFiles(MMOPlugin p,String f,String e,Consumer<Path> c){loadFiles(p,f,e,c,true);}
    private static void loadFiles(MMOPlugin p,String folder,String ext,Consumer<Path> consumer,boolean recursive){
        Path dir=root(p).resolve(folder);if(!Files.isDirectory(dir))return;try(var s=recursive?Files.walk(dir):Files.list(dir)){s.filter(Files::isRegularFile).filter(x->ext==null||ext.isBlank()||x.getFileName().toString().endsWith(ext)).sorted().forEach(consumer);}catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static Path root(MMOPlugin plugin){Path r=MythicLibFabricMod.configRoot();return plugin==null||"svframelib".equals(plugin.getNamespacedKey())?r:r.getParent().resolve(plugin.getNamespacedKey());}
    private static String strip(String n){int i=n.lastIndexOf('.');return i<0?n:n.substring(0,i);}
    private static Map<String,Object> copy(Map<?,?> m){Map<String,Object> r=new LinkedHashMap<>();m.forEach((k,v)->r.put(String.valueOf(k),v));return r;}
}
