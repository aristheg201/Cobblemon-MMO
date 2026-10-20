package vn.svframe.fantasyhub.client;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientHudPacksTest {
    @Test void detailedAnimationsContainsExecutablePlayerAndCapeAnimations() throws Exception {
        for(String model:java.util.List.of("player","player_slim","player_cape","elytra")) {
            String path="/resourcepacks/detailed_animations/assets/minecraft/emf/cem/"+model+".jem";
            try(var input=getClass().getResourceAsStream(path)) {
                assertNotNull(input,path);
                var json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
                assertTrue(json.getAsJsonArray("models").size()>0,path);
                assertTrue(json.toString().contains("animations"),path);
                assertFalse(java.util.regex.Pattern.compile("(?<![\\w.])is_\\w+\\s*[<*]\\s*1\\b").matcher(json.toString()).find(),path);
                assertFalse(json.toString().contains("/ (var.frame_time)"),path);
                assertFalse(json.toString().contains("/ var.HeightVelocity"),path);
                assertFalse(json.toString().contains("/ var.SprintingVactor"),path);
            }
        }
    }
    @Test void e19DefaultAndShinyPokemonResolveToRealSprites() throws Exception {
        String base="/resourcepacks/e19_minimap_icons/assets/xaerominimap/entity/icon/";
        try(var input=getClass().getResourceAsStream(base+"definition/cobblemon/pokemon.json")) {
            assertNotNull(input);
            var variants=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("variants");
            for(String name:java.util.List.of("bulbasaur","pikachu","rayquaza","noivern")) {
                for(String suffix:java.util.List.of("","_shiny")) {
                    String key="cobblemon:textures/pokemon/"+name+"/"+name+suffix+".png";
                    assertTrue(variants.has(key),key);
                    String sprite=variants.get(key).getAsString().replace("normal_sprite:","");
                    assertNotNull(getClass().getResource(base+"sprite/"+sprite),sprite);
                }
            }
        }
    }
    @Test void minimapPackDoesNotReplaceLiveCobblemonModels() {
        assertNull(getClass().getResource("/resourcepacks/e19_minimap_icons/assets/cobblemon/bedrock/pokemon/resolvers/0025_pikachu/0_pikachu_base.json"));
    }
}
