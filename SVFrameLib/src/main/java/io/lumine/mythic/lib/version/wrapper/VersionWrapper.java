package io.lumine.mythic.lib.version.wrapper;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.version.OreDrops;
import io.lumine.mythic.lib.version.api.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

/** Native Fabric compatibility wrapper replacing the Bukkit version bridge. */
public interface VersionWrapper {
    String PLAYER_PROFILE_NAME = "SVFrameLibProfile";

    static VersionWrapper get() { return Native.INSTANCE; }
    GameProfile newProfile(UUID uniqueId, String textureValue);
    default GameProfile newProfile(String textureValue) { return newProfile(UUID.randomUUID(), textureValue); }
    OreDrops getOreDrops(Item item);
    int getFoodRestored(ItemStack item);
    float getAttackCooldown(ServerPlayerEntity player);
    boolean isGeneratorOutput(Item item);
    float getSaturationRestored(ItemStack item);
    NBTItem getNBTItem(ItemStack item);
    default void sendActionBar(ServerPlayerEntity player, String message) { sendActionBarRaw(player, message); }
    void sendActionBarRaw(ServerPlayerEntity player, String message);
    void sendJson(ServerPlayerEntity player, String json);
    void playArmAnimation(ServerPlayerEntity player);
    boolean damage(LivingEntity target, double damage, Entity source);

    final class Native implements VersionWrapper {
        private static final Native INSTANCE = new Native();
        private Native() { }
        @Override public GameProfile newProfile(UUID id, String texture) { return GameProfile.of(id, texture); }
        @Override public OreDrops getOreDrops(Item item) { return new OreDrops(item); }
        @Override public int getFoodRestored(ItemStack item) { var component = item.get(net.minecraft.component.DataComponentTypes.FOOD); return component == null ? 0 : component.nutrition(); }
        @Override public float getAttackCooldown(ServerPlayerEntity player) { return player.getAttackCooldownProgress(0.5f); }
        @Override public boolean isGeneratorOutput(Item item) { return item != null; }
        @Override public float getSaturationRestored(ItemStack item) { var component = item.get(net.minecraft.component.DataComponentTypes.FOOD); return component == null ? 0f : component.saturation(); }
        @Override public NBTItem getNBTItem(ItemStack item) { return NBTItem.get(item); }
        @Override public void sendActionBarRaw(ServerPlayerEntity player, String message) { if (player != null) player.sendMessage(Text.literal(message == null ? "" : message), true); }
        @Override public void sendJson(ServerPlayerEntity player, String json) { if (player != null) player.sendMessage(Text.literal(json == null ? "" : json), false); }
        @Override public void playArmAnimation(ServerPlayerEntity player) { if (player != null) player.swingHand(player.getActiveHand(), true); }
        @Override public boolean damage(LivingEntity target, double damage, Entity source) { if (target == null || damage <= 0) return false; return target.damage(source instanceof ServerPlayerEntity p ? target.getDamageSources().playerAttack(p) : target.getDamageSources().generic(), (float) damage); }
    }
}
