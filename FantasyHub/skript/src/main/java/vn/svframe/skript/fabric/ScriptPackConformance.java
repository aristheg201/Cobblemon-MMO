package vn.svframe.skript.fabric;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import vn.svframe.skript.lang.*;

/** Compile each supplied source and the combined pack without executing load events or commands. */
final class ScriptPackConformance {
    static Map<String,Object> inspect(Path scripts, Path report) throws IOException {
        var sources=new LinkedHashMap<String,String>();
        try(var paths=Files.walk(scripts)) {
            for(Path path:paths.filter(Files::isRegularFile).sorted().toList()) {
                if(!path.getFileName().toString().endsWith(".sk"))continue;
                if(Files.size(path)>2_000_000)throw new IOException("Script exceeds size limit: "+scripts.relativize(path));
                sources.put(scripts.relativize(path).toString(),Files.readString(path));
            }
        }
        Program combined=new Parser(SVFrameSkript.PLATFORM).compile(sources);
        var files=new ArrayList<Map<String,Object>>();
        for(var entry:sources.entrySet()) {
            Program.File individual=new Parser(SVFrameSkript.PLATFORM).compile(Map.of(entry.getKey(),entry.getValue())).files.get(entry.getKey());
            Program.File pack=combined.files.get(entry.getKey());
            var row=new LinkedHashMap<String,Object>();
            row.put("file",entry.getKey());row.put("sha256",sha(entry.getValue()));
            row.put("individuallyCompiled",individual.valid());row.put("packCompiled",pack.valid());
            row.put("individualDiagnostics",diagnostics(individual));row.put("packDiagnostics",diagnostics(pack));
            row.put("runtimeStatus","NOT_RUN");files.add(row);
        }
        var out=new LinkedHashMap<String,Object>();out.put("format",1);out.put("files",files);
        out.put("scope","All installed .sk sources including disabled files; compilation never certifies runtime behavior.");
        out.put("individuallyCompiled",files.stream().filter(f->Boolean.TRUE.equals(f.get("individuallyCompiled"))).count());
        out.put("packCompiled",files.stream().filter(f->Boolean.TRUE.equals(f.get("packCompiled"))).count());
        Files.createDirectories(report.getParent());Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(out));
        return out;
    }
    private static List<Map<String,Object>> diagnostics(Program.File file) {
        return file.diagnostics().stream().map(d->Map.<String,Object>of("line",d.source().line(),"syntax",d.source().text(),"error",d.message())).toList();
    }
    private static String sha(String text) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
