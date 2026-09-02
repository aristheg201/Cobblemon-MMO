package vn.svframe.svframemmo.cobblemon.fusion.render;

import net.minecraft.network.packet.Packet;

/** Direct network send used only after Fusion packet rewriting has decided the replacement packet set. */
public interface FusionRawPacketSender {
    void svframe$sendRaw(Packet<?> packet);
}
