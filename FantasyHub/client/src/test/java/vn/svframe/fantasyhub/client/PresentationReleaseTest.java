package vn.svframe.fantasyhub.client;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.svframe.fantasyhub.protocol.Protocol;

class PresentationReleaseTest {
  @Test void packagedAssetRevisionMatchesConnectionHandshake() throws Exception {
    try(var input=getClass().getResourceAsStream("/assets/fantasyhub/asset-revision.txt")) {
      assertNotNull(input);
      assertEquals(Protocol.ASSET_REVISION,new String(input.readAllBytes(),StandardCharsets.UTF_8).strip());
    }
  }
}
