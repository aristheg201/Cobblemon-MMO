package vn.svframe.svquest.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import vn.svframe.svquest.SVQuest;

/** Sent only on join/manual sync/reload. Normal quest progress never resends the full catalog. */
public record CatalogPayload(String catalog) implements CustomPayload {
    public static final Id<CatalogPayload> ID = new Id<>(Identifier.of(SVQuest.MOD_ID, "catalog"));
    public static final PacketCodec<RegistryByteBuf, CatalogPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, CatalogPayload::catalog, CatalogPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
