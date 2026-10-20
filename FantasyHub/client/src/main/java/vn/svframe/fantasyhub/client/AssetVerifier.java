package vn.svframe.fantasyhub.client;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Function;

/** All paths must come from the client mod container, never the shared classloader. */
final class AssetVerifier {
    private AssetVerifier() {}
    static String diagnose(Function<String, Path> find, String expectedRevision) {
        try {
            Path revision=find.apply("assets/fantasyhub/asset-revision.txt");
            if(revision==null)return "missing asset-revision.txt";
            try(var stream=Files.newInputStream(revision)) {
                byte[] bytes=stream.readNBytes(257);
                if(bytes.length>256||!expectedRevision.equals(new String(bytes,StandardCharsets.UTF_8).trim()))return "asset-revision.txt does not match "+expectedRevision;
            }
            Path indexPath=find.apply("assets/fantasyhub/asset-index.json");
            if(indexPath==null)return "missing asset-index.json";
            byte[] bytes;
            try(var stream=Files.newInputStream(indexPath)) {bytes=stream.readNBytes(4_194_305);}
            if(bytes.length>4_194_304)return "asset-index.json exceeds size limit";
            var index=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            if(index.size()<180||index.size()>20000||!index.has("assets/fantasyhub/models/item/visual_bone.json")||!index.has("assets/svframe_imported/models/vfx_soul_blade/cube_0.json"))return "asset-index.json is incomplete";
            var digest=MessageDigest.getInstance("SHA-256");
            for(var entry:index.entrySet()) {
                String name=entry.getKey();
                if(!(name.startsWith("assets/")||name.startsWith("resourcepacks/detailed_animations/")||name.startsWith("resourcepacks/e19_minimap_icons/"))||name.contains("..")||name.contains("\\"))return "invalid asset path";
                Path path=find.apply(name);
                if(path==null)return "missing "+name;
                try(var stream=Files.newInputStream(path)) {
                    byte[] content=stream.readNBytes(8_000_001);
                    if(content.length>8_000_000)return "asset exceeds size limit: "+name;
                    if(!entry.getValue().getAsString().equals(HexFormat.of().formatHex(digest.digest(content))))return "asset checksum mismatch: "+name;
                }
            }
            return "";
        } catch(Exception failure) {return "asset verification failed: "+failure.getClass().getSimpleName();}
    }
}
