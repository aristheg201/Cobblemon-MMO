package vn.svframe.fantasyhub.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.google.gson.JsonParser;

/** Verify packaged presentation inputs before accepting the server's presentation contract. */
final class AssetIntegrity {
    private AssetIntegrity(){}
    static boolean verified(){
        try(var stream=AssetIntegrity.class.getResourceAsStream("/assets/fantasyhub/asset-index.json")){
            if(stream==null)return false;byte[] bytes=stream.readNBytes(262145);if(bytes.length>262144)return false;
            var index=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            if(index.size()<180||index.size()>4096||!index.has("assets/fantasyhub/models/item/visual_bone.json")||!index.has("assets/svframe_imported/models/vfx_soul_blade/cube_0.json"))return false;
            var digest=MessageDigest.getInstance("SHA-256");
            for(var entry:index.entrySet()){
                String name=entry.getKey();if(!name.startsWith("assets/")||name.contains(".."))return false;
                try(var asset=AssetIntegrity.class.getResourceAsStream("/"+name)){
                    if(asset==null)return false;byte[] content=asset.readNBytes(8_000_001);if(content.length>8_000_000)return false;
                    if(!entry.getValue().getAsString().equals(HexFormat.of().formatHex(digest.digest(content))))return false;
                }
            }return true;
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException|RuntimeException failure){return false;}
    }
}
