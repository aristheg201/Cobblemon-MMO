package vn.svframe.svframelib.audit;
import vn.svframe.svframelib.config.YamlLite;
import java.nio.file.*;import java.util.*;
public final class YamlCorpusSmoke{public static void main(String[]a)throws Exception{Path root=Path.of(a[0]);int ok=0,fail=0;List<String> errors=new ArrayList<>();try(var s=Files.walk(root)){for(Path p:s.filter(x->x.toString().endsWith(".yml")).toList()){try{YamlLite.parse(p);ok++;}catch(Exception e){fail++;if(errors.size()<40)errors.add(root.relativize(p)+" :: "+e.getMessage());}}}System.out.println("YAML_CORPUS_OK="+ok);System.out.println("YAML_CORPUS_FAIL="+fail);errors.forEach(System.out::println);if(fail>0)System.exit(2);}}
