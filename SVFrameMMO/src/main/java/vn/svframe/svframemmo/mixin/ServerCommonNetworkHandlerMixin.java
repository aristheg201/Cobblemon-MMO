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
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.player.ResourceRegenRuntime;
import vn.svframe.svframemmo.runtime.PersistentHudRuntime;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the vanilla heart renderer at the configured visual size without ever modifying authoritative health.
 *
 * Minecraft 1.21.1 ClientPlayerEntity.updateHealth() treats any lower HealthUpdateS2CPacket value as damage and sets
 * hurtTime/maxHurtTime even when the server never damaged the player. SVFrameMMO therefore keeps the visual value
 * monotonic across positive regeneration and across the short sync window immediately following that regeneration.
 * A genuine server-side hit still has hurtTime set on ServerPlayerEntity and is allowed through normally.
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
        double actualMax = player.getMaxHealth();
        boolean scaleHealth = Double.isFinite(cap) && cap > 0d
                && Double.isFinite(actualMax) && actualMax > cap + 1.0e-4d;

        if (original instanceof HealthUpdateS2CPacket health) {
            float actual = health.getHealth();
            float visible = actual;
            if (scaleHealth && actual > 0f)
                visible = (float) Math.max(0d, Math.min(cap, cap * actual / actualMax));

            boolean actualDidNotDecrease = Float.isFinite(svframe$lastActualHealth)
                    && actual + 1.0e-4f >= svframe$lastActualHealth;
            boolean visualWouldDecrease = Float.isFinite(svframe$lastVisibleHealth)
                    && visible + 1.0e-4f < svframe$lastVisibleHealth;
            boolean regenSync = ResourceRegenRuntime.recentlyRegeneratedHealth(player.getUuid(), SVFrameMMO.currentTick());
            boolean genuineDamageActive = player.hurtTime > 0;

            // Never turn an increase/no-change into client-side hurt. Also suppress a transient lower packet belonging
            // to the immediate post-regeneration sync unless the server is actually processing a real hurt state.
            if (actual > 0f && visualWouldDecrease
                    && (actualDidNotDecrease || (regenSync && !genuineDamageActive))) {
                visible = svframe$lastVisibleHealth;
            }

            svframe$lastActualHealth = actual;
            svframe$lastVisibleHealth = visible;
            if (Float.compare(visible, actual) == 0) return original;
            return new HealthUpdateS2CPacket(visible, health.getFood(), health.getSaturation());
        }

        if (!scaleHealth) return original;
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
