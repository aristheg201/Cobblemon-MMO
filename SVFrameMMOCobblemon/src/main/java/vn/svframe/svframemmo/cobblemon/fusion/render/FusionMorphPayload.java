package vn.svframe.svframemmo.cobblemon.fusion.render;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Server-authoritative visual state for replacing a player's renderer with a Cobblemon Pokemon model. */
public record FusionMorphPayload(UUID playerUuid, boolean active, String properties) implements CustomPayload {
    public static final Id<FusionMorphPayload> ID = new Id<>(Identifier.of("svframemmo_cobblemon", "fusion_morph"));
    public static final PacketCodec<RegistryByteBuf, FusionMorphPayload> CODEC = PacketCodec.of(FusionMorphPayload::write, FusionMorphPayload::new);

    public FusionMorphPayload(RegistryByteBuf buf) {
        this(buf.readUuid(), buf.readBoolean(), buf.readString(2048));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeUuid(playerUuid);
        buf.writeBoolean(active);
        buf.writeString(properties == null ? "" : properties, 2048);
    }

    public static FusionMorphPayload clear(UUID playerUuid) {
        return new FusionMorphPayload(playerUuid, false, "");
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
