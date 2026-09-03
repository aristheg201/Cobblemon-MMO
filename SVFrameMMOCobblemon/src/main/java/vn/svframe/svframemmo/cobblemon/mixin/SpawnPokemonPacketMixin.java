package vn.svframe.svframemmo.cobblemon.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnPokemonPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.fusion.FusionSession;

import java.util.Set;

/**
 * Cobblemon's SpawnPokemonPacket(PokemonEntity, ...) snapshots exposed aspects from the packet/template entity.
 * Fusion templates are built through PokemonProperties, whose aspects are selection/render properties rather than
 * forced Pokemon aspects. Preserve the exact authoritative party Pokemon appearance at the final packet boundary.
 */
@Mixin(SpawnPokemonPacket.class)
public abstract class SpawnPokemonPacketMixin {
    private static final int SELF_VIEW_ENTITY_BASE = 1_000_000_000;

    @Inject(
            method = "<init>(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/network/packet/s2c/play/EntitySpawnS2CPacket;)V",
            at = @At("RETURN")
    )
    private void svframe$preserveFusionAppearance(PokemonEntity entity, EntitySpawnS2CPacket vanillaSpawnPacket,
                                                   CallbackInfo ci) {
        int visualId = entity.getId();
        if (visualId < SELF_VIEW_ENTITY_BASE) return;
        if (!(entity.getWorld() instanceof ServerWorld world)) return;

        int playerEntityId = visualId - SELF_VIEW_ENTITY_BASE;
        Entity candidate = world.getEntityById(playerEntityId);
        if (!(candidate instanceof ServerPlayerEntity player)) return;

        FusionSession session = SVFrameMMOCobblemon.fusions().session(player.getUuid());
        if (session == null || !session.activated()) return;

        Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(session.pokemonUuid());
        if (pokemon == null) return;

        Set<String> exactAspects = Set.copyOf(pokemon.getAspects());

        // Keep the template coherent for any later packet construction in the same fusion lifecycle.
        entity.getPokemon().setForcedAspects(exactAspects);
        entity.getDataTracker().set(PokemonEntity.getASPECTS(), exactAspects);

        // More importantly, overwrite the payload fields that Cobblemon's client actually applies to its PokemonEntity.
        SpawnPokemonPacket packet = (SpawnPokemonPacket) (Object) this;
        packet.setSpeciesId(pokemon.getSpecies().getResourceIdentifier());
        packet.setFormName(pokemon.getForm().formOnlyShowdownId());
        packet.setAspects(exactAspects);
        packet.setGender(pokemon.getGender());
        packet.setShiny(pokemon.getShiny());
        packet.setScaleModifier(pokemon.getScaleModifier());
    }
}
