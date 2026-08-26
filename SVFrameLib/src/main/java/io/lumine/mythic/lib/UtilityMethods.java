package io.lumine.mythic.lib;

import io.lumine.mythic.lib.api.MMOLineConfig;
import io.lumine.mythic.lib.api.condition.RegionCondition;
import io.lumine.mythic.lib.api.condition.type.MMOCondition;
import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.comp.interaction.InteractionType;
import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.player.PlayerMetadata;
import io.lumine.mythic.lib.util.DelayFormat;
import io.lumine.mythic.lib.util.lang3.Validate;
import io.lumine.mythic.lib.util.configobject.ConfigObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Native Fabric utility surface preserving the platform-neutral 1.7.1 semantics. */
public final class UtilityMethods {
    private static final int NEGATIVE_SPACE_BASE_CHAR = 851968;
    private UtilityMethods() { }

    public static Vec3d readLocation(ConfigObject config) {
        Objects.requireNonNull(config, "config");
        return new Vec3d(config.getDouble("x", 0d), config.getDouble("y", 0d), config.getDouble("z", 0d));
    }

    public static <T> T getLast(List<T> list) { return list == null || list.isEmpty() ? null : list.get(list.size() - 1); }
    public static Runnable emptyRunnable() { return () -> { }; }

    public static <T> T prettyValueOf(Function<String, T> evaluator, String rawInput, String errorMessage) {
        try { return evaluator.apply(enumName(rawInput)); }
        catch (Throwable throwable) { throw new RuntimeException(String.format(errorMessage, rawInput), throwable); }
    }

    public static UUID uniqueIdFromString(String input) {
        return UUID.nameUUIDFromBytes(String.valueOf(input).getBytes(StandardCharsets.UTF_8));
    }

    public static Vec3d safeNormalize(Vec3d vector) { return safeNormalize(vector, Vec3d.ZERO); }
    public static Vec3d safeNormalize(Vec3d vector, Vec3d fallback) {
        if (vector == null || vector.lengthSquared() < 1.0E-12d) return fallback;
        return vector.normalize();
    }

    public static void forcePotionEffect(LivingEntity entity, RegistryEntry<StatusEffect> effect, double durationSeconds, int amplifier) {
        if (entity == null || effect == null) return;
        int ticks = Math.max(1, (int) Math.round(durationSeconds * 20d));
        entity.addStatusEffect(new StatusEffectInstance(effect, ticks, Math.max(0, amplifier)));
    }

    /** Only legacy built-in external condition; native region integrations register through FlagHandler. */
    public static MMOCondition getCondition(String line) {
        MMOLineConfig config = new MMOLineConfig(line);
        if ("region".equalsIgnoreCase(config.getKey())) return new RegionCondition(config);
        return null;
    }

    public static boolean isInvalidated(PlayerMetadata metadata) { return metadata == null || isInvalidated(metadata.getData()); }
    public static boolean isInvalidated(MMOPlayerData data) { return data == null || !data.isOnline() || data.getPlayer().isDead(); }
    public static boolean isInvalidated(ServerPlayerEntity player) { return player == null || player.isDead() || !player.networkHandler.isConnectionOpen(); }

    public static ItemStack getHandItem(LivingEntity entity, EquipmentSlot slot) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(slot, "slot");
        return switch (slot) {
            case MAIN_HAND -> entity.getMainHandStack();
            case OFF_HAND -> entity.getOffHandStack();
            default -> throw new IllegalArgumentException("Must provide a hand slot");
        };
    }

    public static boolean isAir(ItemStack item) { return item == null || item.isEmpty(); }

    public static boolean canTarget(ServerPlayerEntity player, Entity target) {
        return canTarget(player, target, InteractionType.OFFENSE_ACTION);
    }
    public static boolean canTarget(ServerPlayerEntity player, Entity target, InteractionType type) {
        return MythicLib.inst().getEntities().canTarget(player, target, type);
    }

    public static <T> T resolveField(Function<String, T> evaluator, String... fields) {
        return resolveField(evaluator, null, fields);
    }
    public static <T> T resolveField(Function<String, T> evaluator, Supplier<T> fallback, String... fields) {
        Objects.requireNonNull(evaluator, "evaluator");
        if (fields != null) {
            for (String field : fields) {
                try {
                    T value = Objects.requireNonNull(evaluator.apply(field), "Null supplied value");
                    return value;
                } catch (RuntimeException ignored) { }
            }
        }
        if (fallback != null) return Objects.requireNonNull(fallback.get(), "Null supplied default value");
        throw new IllegalArgumentException("Could not resolve any field " + java.util.Arrays.asList(fields));
    }

    public static boolean isRealPlayer(Entity entity) {
        return entity instanceof ServerPlayerEntity player && player.networkHandler.isConnectionOpen();
    }

    public static void setHealth(LivingEntity entity, double health) {
        if (entity == null) return;
        entity.setHealth((float) clamp(health, 0d, entity.getMaxHealth()));
    }

    public static String caseOnWords(String input) {
        if (input == null || input.isBlank()) return "";
        String[] split = input.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : split) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    public static void dropItemNaturally(ServerWorld world, Vec3d location, ItemStack item) {
        if (world == null || location == null || isAir(item)) return;
        ItemEntity dropped = new ItemEntity(world, location.x, location.y, location.z, item.copy());
        dropped.setToDefaultPickupDelay();
        world.spawnEntity(dropped);
    }

    public static String enumName(String input) {
        return input == null ? "" : input.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }
    public static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    public static <T extends Enum<?>> String kebabCase(T value) { return value.name().toLowerCase(Locale.ROOT).replace('_', '-'); }
    public static String kebabCase(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-'); }
    public static String ymlName(String value) { return kebabCase(value); }

    public static String getSpaceFont(int size) {
        Validate.isTrue(size >= -8192 && size <= 8192, "Size must be between -8192 and 8192");
        return size == 0 ? "" : new String(Character.toChars(NEGATIVE_SPACE_BASE_CHAR + size));
    }
    public static String getFontSpace(int size) { return getSpaceFont(size); }
    public static int getPageNumber(int entries, int entriesPerPage) { return Math.ceilDiv(Math.max(1, entries), entriesPerPage); }

    public static String substringBetween(String input, String open, String close) {
        if (input == null || open == null || close == null) return null;
        int start = input.indexOf(open);
        if (start < 0) return null;
        start += open.length();
        int end = input.indexOf(close, start);
        return end < 0 ? null : input.substring(start, end);
    }

    public static <T> Consumer<T> dummyConsume() { return ignored -> { }; }

    public static Pattern internalPlaceholderPattern(char open, char close) {
        return Pattern.compile(Pattern.quote(String.valueOf(open)) + "([^" + Pattern.quote(String.valueOf(open)) + Pattern.quote(String.valueOf(close)) + "]+)" + Pattern.quote(String.valueOf(close)));
    }

    public static String format(String format, Object... args) { return args == null || args.length == 0 ? format : String.format(format, args); }

    /** Native replacement retaining the historical method name for source ports. */
    public static Object bukkitBootstrap(MMOPlugin plugin, String modId) {
        if (modId == null || modId.isBlank()) return null;
        return FabricLoader.getInstance().getModContainer(modId).orElse(null);
    }

    public static int getPageNumber(long entries, int entriesPerPage) {
        return (int) Math.max(1L, Math.ceil((double) entries / Math.max(1, entriesPerPage)));
    }

    public static String formatDelay(long delay) { return new DelayFormat("mhdMy").format(delay); }
    public static String formatDelay(long delay, boolean seconds) { return new DelayFormat(seconds ? "smhdMy" : "mhdMy").format(delay); }

    public static <T> T safeValueOf(Function<String, T> evaluator, String input, String message, Object... args) {
        try { return evaluator.apply(input); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(String.format(message, args), exception); }
    }
}
