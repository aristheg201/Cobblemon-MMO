package vn.svframe.svframemmo.mixin;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vn.svframe.svframemmo.runtime.PersistentHudRuntime;

import java.util.ArrayList;
import java.util.List;

/**
 * Limits only the owning client's vanilla heart count while leaving authoritative health untouched.
 *
 * Do not proportionally remap current health against MAX_HEALTH here. Minecraft 1.21.1 treats any lower
 * HealthUpdateS2CPacket value as damage, so a MAX_HEALTH increase could otherwise make a healing player appear hurt.
 * Instead the presentation is a direct clamp: current visual health is min(real health, visual cap), and visual
 * MAX_HEALTH is capped to the same value. Exact real health/max-health remain available through SVFrameMMO's HUD.
 */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin {
    @ModifyVariable(method = "send", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Packet<?> svframe$maskLocalHealth(Packet<?> original) {
        if (!((Object) this instanceof ServerPlayNetworkHandler play)) return original;
        ServerPlayerEntity player = play.getPlayer();
        if (player == null) return original;

        double cap = PersistentHudRuntime.visualHealthCap();
        if (!Double.isFinite(cap) || cap <= 0d) return original;

        double actualMax = player.getMaxHealth();
        if (!Double.isFinite(actualMax) || actualMax <= cap + 1.0e-4d) return original;

        if (original instanceof HealthUpdateS2CPacket health) {
            float actual = health.getHealth();
            if (actual <= 0f) return original;
            float visible = (float) Math.min(cap, actual);
            if (Float.compare(visible, actual) == 0) return original;
            return new HealthUpdateS2CPacket(visible, health.getFood(), health.getSaturation());
        }

        if (original instanceof EntityAttributesS2CPacket attributes && attributes.getEntityId() == player.getId()) {
            List<EntityAttributesS2CPacket.Entry> originalEntries = attributes.getEntries();
            int maxHealthIndex = -1;
            for (int i = 0; i < originalEntries.size(); i++) {
                if (originalEntries.get(i).attribute().equals(EntityAttributes.GENERIC_MAX_HEALTH)) {
                    maxHealthIndex = i;
                    break;
                }
            }
            if (maxHealthIndex < 0) return original;

            // Copy only packets that actually contain MAX_HEALTH. Packet order, client rounding,
            // and all non-health attribute entries remain byte-for-byte equivalent in meaning.
            List<EntityAttributesS2CPacket.Entry> entries = new ArrayList<>(originalEntries);
            EntityAttributesS2CPacket.Entry entry = entries.get(maxHealthIndex);
            entries.set(maxHealthIndex, new EntityAttributesS2CPacket.Entry(entry.attribute(), cap, List.of()));
            return EntityAttributesS2CPacketInvoker.svframe$create(attributes.getEntityId(), entries);
        }
        return original;
    }
}
