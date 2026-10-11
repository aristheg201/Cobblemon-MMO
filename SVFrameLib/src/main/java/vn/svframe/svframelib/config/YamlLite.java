package vn.svframe.svframelib.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Safe full YAML configuration reader; scalar mapping keys retain the native API's string semantics. */
public final class YamlLite {
    private YamlLite(){}
    public static Object parse(String text) {
        LoaderOptions options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setMaxAliasesForCollections(50);
        options.setCodePointLimit(16_000_000);options.setNestingDepthLimit(64);
        Object loaded=new Yaml(new SafeConstructor(options)).load(text.replace("\uFEFF",""));
        return loaded==null?new LinkedHashMap<String,Object>():normalize(loaded);
    }
    private static Object normalize(Object input) {
        if(input instanceof Map<?,?> map){Map<String,Object> result=new LinkedHashMap<>();for(var entry:map.entrySet()){
            if(entry.getKey() instanceof Map<?,?>||entry.getKey() instanceof Collection<?>)throw new IllegalArgumentException("Configuration keys must be scalar");
            String key=String.valueOf(entry.getKey());if(result.containsKey(key))throw new IllegalArgumentException("Duplicate normalized configuration key: "+key);result.put(key,normalize(entry.getValue()));
        }return result;}
        if(input instanceof List<?> list)return list.stream().map(YamlLite::normalize).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return input;
    }
    public static Object parse(Path path) throws IOException{return parse(Files.readString(path));}
    @SuppressWarnings("unchecked") public static Map<String,Object> map(Object value){return value==null?Map.of():(Map<String,Object>)value;}
    @SuppressWarnings("unchecked") public static List<Object> list(Object value){return value==null?List.of():(List<Object>)value;}
}
