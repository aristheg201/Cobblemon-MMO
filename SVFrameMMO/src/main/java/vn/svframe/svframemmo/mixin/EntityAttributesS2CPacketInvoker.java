package vn.svframe.svframemmo.mixin;

import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(EntityAttributesS2CPacket.class)
public interface EntityAttributesS2CPacketInvoker {
    @Invoker("<init>")
    static EntityAttributesS2CPacket svframe$create(int entityId, List<EntityAttributesS2CPacket.Entry> entries) {
        throw new AssertionError("mixin");
    }
}
