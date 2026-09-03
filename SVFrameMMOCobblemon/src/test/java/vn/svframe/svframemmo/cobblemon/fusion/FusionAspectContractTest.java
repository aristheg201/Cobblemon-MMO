package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonPropertyExtractor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnPokemonPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the exact Cobblemon 1.7.3 APIs Fusion relies on to preserve custom visual aspects. */
final class FusionAspectContractTest {
    @Test
    void cobblemonExposesForcedAspectAndSpawnAspectApis() throws Exception {
        Method setForcedAspects = Pokemon.class.getMethod("setForcedAspects", Set.class);
        Method getAspects = Pokemon.class.getMethod("getAspects");
        Method getEntityAspects = PokemonEntity.class.getMethod("getASPECTS");
        Method setPacketAspects = SpawnPokemonPacket.class.getMethod("setAspects", Set.class);

        assertNotNull(setForcedAspects);
        assertNotNull(getAspects);
        assertNotNull(getEntityAspects);
        assertNotNull(setPacketAspects);
        assertTrue(PokemonPropertyExtractor.TRANSFORM.contains(PokemonPropertyExtractor.ASPECTS));
    }
}
