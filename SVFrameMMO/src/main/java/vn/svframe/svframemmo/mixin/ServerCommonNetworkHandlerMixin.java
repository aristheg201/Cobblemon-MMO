package vn.svframe.svframemmo.mixin;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vn.svframe.svframemmo.runtime.PersistentHudRuntime;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the vanilla heart renderer at the configured visual size without ever
 * modifying authoritative health. Only packets sent to the owning player are
 * rewritten; server MAX_HEALTH/current health and packets seen by other clients
 * retain their real values.
 *
 * ClientPlayerEntity interprets a lower HealthUpdateS2CPacket value as damage.
 * MAX_HEALTH/stat refreshes can change the proportional visual value even when
 * authoritative health stayed equal or increased, which used to create a false
 * hurt response while regenerating. Keep the visual value monotonic whenever the
 * authoritative health did not decrease; real damage still produces a decrease.
 */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin {
    @Unique private float svframe$lastActualHealth = Float.NaN;
    @Unique private float svframe$lastVisibleHealth = Float.NaN;

    @ModifyVariable(method = "send", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Packet<?> svframe$maskLocalHealth(Packet<?> original) {
        if (!((Object) this instanceof ServerPlayNetworkHandler play)) return original;
        ServerPlayerEntity player = play.getPlayer();
        if (player == null) return original;

        double cap = PersistentHudRuntime.visualHealthCap();
        if (!Double.isFinite(cap) || cap <= 0d) return original;
        double actualMax = player.getMaxHealth();
        if (!Double.isFinite(actualMax) || actualMax <= 0d) return original;

        if (original instanceof HealthUpdateS2CPacket health) {
            float actual = health.getHealth();

            // Below the visual cap no masking is required. Still remember the
            // authoritative/visible pair so crossing the cap cannot be mistaken
            // for damage by the client on the next packet.
            if (actualMax <= cap + 1.0e-4d) {
                svframe$lastActualHealth = actual;
                svframe$lastVisibleHealth = actual;
                return original;
            }

            float visible = actual <= 0f
                    ? actual
                    : (float) Math.max(0d, Math.min(cap, cap * actual / actualMax));

            boolean authoritativeDidNotDecrease = Float.isFinite(svframe$lastActualHealth)
                    && actual + 1.0e-4f >= svframe$lastActualHealth;
            if (authoritativeDidNotDecrease
                    && Float.isFinite(svframe$lastVisibleHealth)
                    && visible < svframe$lastVisibleHealth) {
                visible = (float) Math.min(cap, svframe$lastVisibleHealth);
            }

            svframe$lastActualHealth = actual;
            svframe$lastVisibleHealth = visible;
            return new HealthUpdateS2CPacket(visible, health.getFood(), health.getSaturation());
        }

        if (actualMax <= cap + 1.0e-4d) return original;
        if (original instanceof EntityAttributesS2CPacket attributes && attributes.getEntityId() == player.getId()) {
            List<EntityAttributesS2CPacket.Entry> entries = new ArrayList<>(attributes.getEntries());
            boolean changed = false;
            for (int i = 0; i < entries.size(); i++) {
                EntityAttributesS2CPacket.Entry entry = entries.get(i);
                if (!entry.attribute().equals(EntityAttributes.GENERIC_MAX_HEALTH)) continue;
                entries.set(i, new EntityAttributesS2CPacket.Entry(entry.attribute(), cap, List.of()));
                changed = true;
            }
            if (changed) return EntityAttributesS2CPacketInvoker.svframe$create(attributes.getEntityId(), entries);
        }
        return original;
    }
}
