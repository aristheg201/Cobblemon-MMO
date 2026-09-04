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
 * The owning client receives a proportional health presentation capped to the configured vanilla-heart budget.
 * With the default cap of 40 HP, the client renders at most 20 hearts / two rows while server-side current/max health
 * remain authoritative and uncapped. Exact real health/max-health remain available through SVFrameMMO's numeric HUD.
 */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin {
    @ModifyVariable(method = "send", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Packet<?> svframe$maskLocalHealth(Packet<?> original) {
        // send() is a very hot path. Ignore every unrelated packet before resolving player/attributes.
        if (!(original instanceof HealthUpdateS2CPacket)
                && !(original instanceof EntityAttributesS2CPacket)) return original;
        if (!((Object) this instanceof ServerPlayNetworkHandler play)) return original;
        ServerPlayerEntity player = play.getPlayer();
        if (player == null) return original;

        double cap = PersistentHudRuntime.visualHealthCap();
        if (!Double.isFinite(cap) || cap <= 0d) return original;

        double actualMax = player.getMaxHealth();
        if (!Double.isFinite(actualMax) || actualMax <= cap + 1.0e-4d) return original;

        if (original instanceof HealthUpdateS2CPacket health) {
            float actual = health.getHealth();
            if (!Float.isFinite(actual)) return original;
            float visible = (float) Math.max(0.0d, Math.min(cap, actual / actualMax * cap));
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
