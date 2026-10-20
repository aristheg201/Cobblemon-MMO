package vn.svframe.fantasyhub.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AssetVerifierTest {
    @Test void actualMergedModResourcesPassTheirPackagedIndex() throws Exception {
        var index=getClass().getResource("/assets/fantasyhub/asset-index.json");
        assertNotNull(index);
        Path indexFile=Path.of(index.toURI());
        Path resources=indexFile.getParent().getParent().getParent();
        assertEquals("",AssetVerifier.diagnose(name->{Path path=resources.resolve(name);return Files.exists(path)?path:null;},vn.svframe.fantasyhub.protocol.Protocol.ASSET_REVISION));
    }

    @TempDir Path root;
    private Path find(String name){Path p=root.resolve(name);return Files.exists(p)?p:null;}
    private void write(String name,String value)throws Exception{Path p=root.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,value);}
    private void valid()throws Exception{
        write("assets/fantasyhub/asset-revision.txt","reference-dragonborn-v4\n");
        JsonObject index=new JsonObject();
        String[] required={"assets/fantasyhub/models/item/visual_bone.json","assets/svframe_imported/models/vfx_soul_blade/cube_0.json"};
        for(int i=0;i<180;i++){
            String name=i<2?required[i]:"assets/test/"+i+".json";write(name,"{}");
            index.addProperty(name,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(name)))));
        }
        write("assets/fantasyhub/asset-index.json",index.toString());
    }
    @Test void validContainerPasses()throws Exception{valid();assertEquals("",AssetVerifier.diagnose(this::find,"reference-dragonborn-v4"));}
    @Test void changedAssetReportsTheActualFile()throws Exception{
        valid();write("assets/test/42.json","{\"changed\":true}");
        assertEquals("asset checksum mismatch: assets/test/42.json",AssetVerifier.diagnose(this::find,"reference-dragonborn-v4"));
    }
    @Test void missingAssetReportsTheActualFile()throws Exception{
        valid();Files.delete(root.resolve("assets/test/42.json"));
        assertEquals("missing assets/test/42.json",AssetVerifier.diagnose(this::find,"reference-dragonborn-v4"));
    }
    @Test void staleRevisionDoesNotPassEvenWithValidIndex()throws Exception{
        valid();write("assets/fantasyhub/asset-revision.txt","reference-dragonborn-v3");
        assertTrue(AssetVerifier.diagnose(this::find,"reference-dragonborn-v4").startsWith("asset-revision.txt does not match"));
    }
    @Test void packagedPackTamperingIsVerifiedToo()throws Exception{
        valid();
        String name="resourcepacks/detailed_animations/pack.mcmeta";
        write(name,"{}");
        var index=JsonParser.parseString(Files.readString(root.resolve("assets/fantasyhub/asset-index.json"))).getAsJsonObject();
        index.addProperty(name,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        write("assets/fantasyhub/asset-index.json",index.toString());
        assertEquals("",AssetVerifier.diagnose(this::find,"reference-dragonborn-v4"));
        write(name,"{\"changed\":true}");
        assertEquals("asset checksum mismatch: "+name,AssetVerifier.diagnose(this::find,"reference-dragonborn-v4"));
    }
}
