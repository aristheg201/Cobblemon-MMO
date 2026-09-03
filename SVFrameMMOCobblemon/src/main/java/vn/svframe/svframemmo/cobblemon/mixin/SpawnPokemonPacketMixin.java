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
 * Preserves the authoritative party Pokemon appearance at the final Cobblemon spawn-packet boundary while applying
 * the Fusion-only Stand render scale. The selected Pokemon itself is never resized or mutated.
 */
@Mixin(SpawnPokemonPacket.class)
public abstract class SpawnPokemonPacketMixin {
    private static final int STAND_ENTITY_BASE = 1_000_000_000;
    private static final float STAND_SCALE_MULTIPLIER = 0.60F;
    private static final float MIN_STAND_SCALE = 0.10F;
    private static final double MAX_STAND_DIMENSION = 2.20D;

    @Inject(
            method = "<init>(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/network/packet/s2c/play/EntitySpawnS2CPacket;)V",
            at = @At("RETURN")
    )
    private void svframe$preserveFusionAppearance(PokemonEntity entity, EntitySpawnS2CPacket vanillaSpawnPacket,
                                                   CallbackInfo ci) {
        int visualId = entity.getId();
        if (visualId < STAND_ENTITY_BASE) return;
        if (!(entity.getWorld() instanceof ServerWorld world)) return;

        int playerEntityId = visualId - STAND_ENTITY_BASE;
        Entity candidate = world.getEntityById(playerEntityId);
        if (!(candidate instanceof ServerPlayerEntity player)) return;

        FusionSession session = SVFrameMMOCobblemon.fusions().session(player.getUuid());
        if (session == null || !session.activated()) return;

        Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(session.pokemonUuid());
        if (pokemon == null) return;

        Set<String> exactAspects = Set.copyOf(pokemon.getAspects());

        // Keep the packet-only template coherent for species/form/aspect rendering. Do not write the canonical party
        // scale back into the packet: Fusion Stand scale is deliberately smaller than the real Pokemon.
        entity.getPokemon().setForcedAspects(exactAspects);
        entity.getDataTracker().set(PokemonEntity.getASPECTS(), exactAspects);

        SpawnPokemonPacket packet = (SpawnPokemonPacket) (Object) this;
        packet.setSpeciesId(pokemon.getSpecies().getResourceIdentifier());
        packet.setFormName(pokemon.getForm().formOnlyShowdownId());
        packet.setAspects(exactAspects);
        packet.setGender(pokemon.getGender());
        packet.setShiny(pokemon.getShiny());
        packet.setScaleModifier(standScale(entity, pokemon));
    }

    private static float standScale(PokemonEntity entity, Pokemon pokemon) {
        float originalScale = pokemon.getScaleModifier();
        if (!Float.isFinite(originalScale) || originalScale <= 0F) originalScale = 1F;

        float factor = STAND_SCALE_MULTIPLIER;
        double largestDimension = Math.max(entity.getWidth(), entity.getHeight());
        if (Double.isFinite(largestDimension) && largestDimension > 1.0e-4D) {
            factor = Math.min(factor, (float) (MAX_STAND_DIMENSION / largestDimension));
        }
        return Math.max(MIN_STAND_SCALE, originalScale * Math.max(0.01F, factor));
    }
}
