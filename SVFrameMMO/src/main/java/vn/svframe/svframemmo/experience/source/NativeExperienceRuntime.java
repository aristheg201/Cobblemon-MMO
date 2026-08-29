package vn.svframe.svframemmo.experience.source;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.api.event.PlayerAttackEvent;
import vn.svframe.svframelib.damage.DamageType;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.experience.Profession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Native Fabric event bridge for MMOCore experience-source gameplay. */
public final class NativeExperienceRuntime {
    private final ExperienceSourceRuntime sources;
    private final Map<UUID, PositionState> positions = new HashMap<>();
    private final Map<UUID, UUID> lastAttackers = new HashMap<>();
    private final Set<PlacedBlock> playerPlaced = new HashSet<>();
    private boolean installed;

    public NativeExperienceRuntime(ExperienceSourceRuntime sources) {
        this.sources = sources;
    }

    public synchronized void install() {
        if (installed) return;
        installed = true;

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer)
                onBlockBroken(serverPlayer, serverWorld, pos, state);
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (damageTaken <= 0f || blocked) return;
            if (entity instanceof ServerPlayerEntity player && player.isAlive()) {
                ExperienceSignal.Builder signal = ExperienceSignal.builder("damagetaken")
                        .primary(source.getName())
                        .units(Math.min(damageTaken, player.getMaxHealth()))
                        .tag(source.getName());
                damageCauseAliases(source).forEach(signal::tag);
                sources.accept(SVFrameMMO.playerData().get(player), signal.build());
            }

            Entity direct = source.getSource();
            if (direct instanceof ProjectileEntity projectile && projectile.getOwner() instanceof ServerPlayerEntity shooter && shooter != entity) {
                lastAttackers.put(entity.getUuid(), shooter.getUuid());
                double distance = Math.max(0d, projectile.getPos().distanceTo(shooter.getPos()));
                if (distance > 0d) onProjectileHit(shooter, projectileType(projectile), damageTaken * distance);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            UUID attackerId = lastAttackers.remove(entity.getUuid());
            if (attackerId == null && damageSource.getAttacker() instanceof ServerPlayerEntity player) attackerId = player.getUuid();
            if (attackerId == null || isSpawnerSpawned(entity)) return;
            MinecraftServer server = entity.getServer();
            if (server == null) return;
            ServerPlayerEntity attacker = server.getPlayerManager().getPlayer(attackerId);
            if (attacker == null) return;
            String name = entity.hasCustomName() ? entity.getCustomName().getString() : "";
            sources.accept(SVFrameMMO.playerData().get(attacker), ExperienceSignal.builder("killmob")
                    .primary(id(Registries.ENTITY_TYPE.getId(entity.getType()).toString()))
                    .attribute("name", name)
                    .build());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    /** Called from SVFrameMMO's SVFrameLib-native attack pipeline. */
    public void onPlayerAttack(PlayerAttackEvent event) {
        if (event == null || event.isCancelled()) return;
        LivingEntity target = event.getAttack().getTarget();
        if (target == null) return;
        ServerPlayerEntity player = event.getPlayer();
        lastAttackers.put(target.getUuid(), player.getUuid());
        ExperienceSignal.Builder signal = ExperienceSignal.builder("damagedealt")
                .units(Math.min(event.getDamage().getDamage(), target.getMaxHealth()));
        for (DamageType type : event.getDamage().collectTypes()) signal.tag(type.name());
        sources.accept(SVFrameMMO.playerData().get(player), signal.build());
    }

    public void onResourceCommitted(PlayerData data, PlayerResource resource, double oldAmount, double newAmount) {
        if (data == null || resource == null || resource == PlayerResource.HEALTH || newAmount >= oldAmount) return;
        sources.accept(data, ExperienceSignal.builder("resource").primary(resource.name()).units(oldAmount - newAmount).build());
    }

    public void onBlockPlaced(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state) {
        if (player == null || world == null || pos == null || state == null) return;
        playerPlaced.add(PlacedBlock.of(world, pos));
        if (!player.isCreative() && !player.isSpectator())
            sources.accept(SVFrameMMO.playerData().get(player), ExperienceSignal.builder("placeblock").primary(blockId(state)).build());
    }

    /** Marks non-natural block formation without emitting the player place-block EXP source. */
    public synchronized void markPlayerPlaced(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) return;
        playerPlaced.add(PlacedBlock.of(world, pos));
    }

    public void onCrafted(ServerPlayerEntity player, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) emit(player, "craftitem", itemId(stack), Math.max(1, stack.getCount()));
    }

    public void onSmelted(ServerPlayerEntity player, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) emit(player, "smeltitem", itemId(stack), Math.max(1, stack.getCount()));
    }

    public void onEaten(ServerPlayerEntity player, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) emit(player, "eat", itemId(stack), 1d);
    }

    public void onTamed(ServerPlayerEntity player, LivingEntity entity) {
        if (entity != null) emit(player, "tame", id(Registries.ENTITY_TYPE.getId(entity.getType()).toString()), 1d);
    }

    public void onFishCaught(ServerPlayerEntity player, ItemStack stack) {
        emit(player, "fishitem", stack == null || stack.isEmpty() ? "" : itemId(stack), Math.max(1, stack == null ? 1 : stack.getCount()));
    }

    /** Uses each profession's own base-enchant-exp map rather than a global approximation. */
    public void onEnchanted(ServerPlayerEntity player, ItemStack result) {
        if (player == null || result == null || result.isEmpty()) return;
        PlayerData data = SVFrameMMO.playerData().get(player);
        LinkedHashSet<String> enchantments = new LinkedHashSet<>();
        Map<String, Integer> levels = new HashMap<>();
        var component = result.getEnchantments();
        for (RegistryEntry<Enchantment> enchantment : component.getEnchantments()) {
            String key = enchantment.getKey().map(value -> value.getValue().getPath()).orElse("");
            if (key.isBlank()) continue;
            enchantments.add(key);
            levels.put(key, component.getLevel(enchantment));
        }
        if (enchantments.isEmpty()) return;
        ExperienceSignal baseSignal = tagged("enchantitem", enchantments, 1d);
        sources.acceptClass(data, baseSignal);
        for (Profession profession : SVFrameMMO.professions().getAll()) {
            double dynamic = 0d;
            Object raw = profession.getRawConfig().get("base-enchant-exp");
            for (String enchantment : enchantments)
                dynamic += mapNumber(raw, enchantment, 0d) * levels.getOrDefault(enchantment, 0);
            if (dynamic > 0d) sources.acceptProfession(data, profession, tagged("enchantitem", enchantments, dynamic));
        }
    }

    /** Calculates repaired durability against each profession's repair-exp table. */
    public void onRepaired(ServerPlayerEntity player, ItemStack before, ItemStack result) {
        if (player == null || before == null || result == null || !before.isDamageable() || !result.isDamageable()) return;
        int repaired = Math.max(0, before.getDamage() - result.getDamage());
        if (repaired <= 0) return;
        PlayerData data = SVFrameMMO.playerData().get(player);
        ExperienceSignal classSignal = ExperienceSignal.builder("repairitem").primary(itemId(result)).units(repaired / 100d).build();
        sources.acceptClass(data, classSignal);
        for (Profession profession : SVFrameMMO.professions().getAll()) {
            double perHundred = mapNumber(profession.getRawConfig().get("repair-exp"), itemId(result), 0d);
            if (perHundred <= 0d) continue;
            sources.acceptProfession(data, profession, ExperienceSignal.builder("repairitem")
                    .primary(itemId(result)).units(perHundred * repaired / 100d).build());
        }
    }

    /** Calculates potion base EXP and MMOCore splash/lingering/extend/upgrade modifiers. */
    public void onBrewed(ServerPlayerEntity player, ItemStack before, ItemStack potion) {
        if (player == null || potion == null || potion.isEmpty()) return;
        PotionContentsComponent contents = potion.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return;
        LinkedHashSet<String> effects = potionEffects(contents);
        String potionKey = potionKey(contents);
        if (effects.isEmpty() && !potionKey.isBlank()) effects.add(normalizePotionEffect(potionKey));
        if (effects.isEmpty()) return;

        PotionContentsComponent oldContents = before == null ? null : before.get(DataComponentTypes.POTION_CONTENTS);
        String oldPotionKey = potionKey(oldContents);
        PlayerData data = SVFrameMMO.playerData().get(player);
        for (String effect : effects) sources.acceptClass(data, ExperienceSignal.builder("brewpotion").primary(effect).build());

        for (Profession profession : SVFrameMMO.professions().getAll()) {
            Object alchemy = profession.getRawConfig().get("alchemy-experience");
            Object effectMap = nestedMap(alchemy, "effects");
            Object special = nestedMap(alchemy, "special");
            double modifier = 1d;
            if (before != null && before.isOf(Items.POTION) && potion.isOf(Items.SPLASH_POTION)) modifier *= mapNumber(special, "splash", 100d) / 100d;
            if (before != null && !before.isOf(Items.LINGERING_POTION) && potion.isOf(Items.LINGERING_POTION)) modifier *= mapNumber(special, "lingering", 100d) / 100d;
            if (!oldPotionKey.startsWith("long_") && potionKey.startsWith("long_")) modifier *= mapNumber(special, "extend", 100d) / 100d;
            if (!oldPotionKey.startsWith("strong_") && potionKey.startsWith("strong_")) modifier *= mapNumber(special, "upgrade", 100d) / 100d;
            for (String effect : effects) {
                double dynamic = mapNumber(effectMap, effect, mapNumber(effectMap, normalizePotionEffect(potionKey), 0d)) * modifier;
                if (dynamic > 0d) sources.acceptProfession(data, profession,
                        ExperienceSignal.builder("brewpotion").primary(effect).units(dynamic).build());
            }
        }
    }

    /** Projectile multiplier is effective damage times travelled distance, matching MMOCore. */
    public void onProjectileHit(ServerPlayerEntity player, String projectileType, double damageTimesDistance) {
        emit(player, "projectile", projectileType, damageTimesDistance);
    }

    public void tick(MinecraftServer server, long tick) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) tickPlayer(player, tick);
    }

    public synchronized void clear() {
        positions.clear();
        lastAttackers.clear();
        playerPlaced.clear();
    }

    public int trackedPlayerPlacedBlocks() { return playerPlaced.size(); }
    public int trackedAttackers() { return lastAttackers.size(); }

    private void onBlockBroken(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state) {
        onCustomBlockBroken(player, world, pos, state, false);
    }

    /** Emits the same mining signal for custom-mining breaks which intentionally bypass vanilla tryBreakBlock. */
    public void onCustomBlockBroken(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state, boolean knownPlayerPlaced) {
        boolean placed = knownPlayerPlaced | playerPlaced.remove(PlacedBlock.of(world, pos));
        if (player.isCreative() || player.isSpectator()) return;
        sources.accept(SVFrameMMO.playerData().get(player), ExperienceSignal.builder("mineblock")
                .primary(blockId(state))
                .attribute("crop-mature", isMatureCrop(state))
                .attribute("player-placed", placed)
                .attribute("silk-touch", hasSilkTouch(player, world))
                .build());
    }

    private void tickPlayer(ServerPlayerEntity player, long tick) {
        Vec3d current = player.getPos();
        String dimension = player.getServerWorld().getRegistryKey().getValue().toString();
        PositionState previous = positions.put(player.getUuid(), PositionState.capture(player, dimension));
        if (previous != null && previous.dimension.equals(dimension)) {
            int bx = player.getBlockX(), by = player.getBlockY(), bz = player.getBlockZ();
            int dx = bx - previous.blockX, dy = by - previous.blockY, dz = bz - previous.blockZ;
            if (dx != 0 || dy != 0 || dz != 0) {
                PlayerData data = SVFrameMMO.playerData().get(player);
                double blockDistance = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
                if (!player.hasVehicle()) sources.accept(data, ExperienceSignal.builder("move").primary(movingType(player)).units(blockDistance).build());
                BlockState feet = player.getServerWorld().getBlockState(player.getBlockPos());
                if (dy > 0 && isClimbable(feet)) sources.accept(data, ExperienceSignal.builder("climb").primary(blockId(feet)).units(dy).build());
            }
            if (player.hasVehicle() && player.getVehicle() != null) {
                double rideDistance = current.distanceTo(previous.position);
                if (rideDistance > 0d) sources.accept(SVFrameMMO.playerData().get(player), ExperienceSignal.builder("ride")
                        .primary(id(Registries.ENTITY_TYPE.getId(player.getVehicle().getType()).toString())).units(rideDistance).build());
            }
        }
        if (tick % 20L == 0L) {
            PlayerData data = SVFrameMMO.playerData().get(player);
            sources.accept(data, ExperienceSignal.builder("play")
                    .attribute("world", dimension).attribute("x", current.x).attribute("z", current.z)
                    .attribute("in-combat", data.isInCombat()).build());
        }
    }

    private void emit(ServerPlayerEntity player, String type, String primary, double units) {
        if (player == null || units <= 0d) return;
        sources.accept(SVFrameMMO.playerData().get(player), ExperienceSignal.builder(type)
                .primary(primary == null ? "" : primary).units(units).build());
    }

    private static ExperienceSignal tagged(String type, Set<String> tags, double units) {
        ExperienceSignal.Builder builder = ExperienceSignal.builder(type).units(units);
        tags.forEach(builder::tag);
        return builder.build();
    }

    private static String movingType(ServerPlayerEntity player) {
        if (player.isSneaking()) return "SNEAK";
        if (player.getAbilities().flying || player.getPose() == net.minecraft.entity.EntityPose.FALL_FLYING) return "FLY";
        if (player.isTouchingWater()) return "SWIM";
        if (player.isSprinting()) return "SPRINT";
        return "WALK";
    }

    private static boolean isClimbable(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.LADDER || block == Blocks.VINE || block == Blocks.WEEPING_VINES
                || block == Blocks.WEEPING_VINES_PLANT || block == Blocks.TWISTING_VINES || block == Blocks.TWISTING_VINES_PLANT;
    }

    private static boolean isMatureCrop(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) return crop.isMature(state);
        for (Property<?> property : state.getProperties()) {
            if (!property.getName().equalsIgnoreCase("age")) continue;
            if (isPropertyAtMaximum(state, property)) return true;
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isPropertyAtMaximum(BlockState state, Property property) {
        Comparable current = state.get(property);
        if (!(current instanceof Number currentNumber)) return false;
        double max = Double.NEGATIVE_INFINITY;
        for (Object allowed : property.getValues()) if (allowed instanceof Number number) max = Math.max(max, number.doubleValue());
        return max != Double.NEGATIVE_INFINITY && currentNumber.doubleValue() >= max;
    }

    private static boolean hasSilkTouch(ServerPlayerEntity player, ServerWorld world) {
        try {
            RegistryEntry<Enchantment> silk = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.SILK_TOUCH);
            return EnchantmentHelper.getLevel(silk, player.getMainHandStack()) > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static ArrayList<String> damageCauseAliases(DamageSource source) {
        ArrayList<String> aliases = new ArrayList<>();
        switch (ExperienceSignal.normalize(source.getName())) {
            case "infire", "onfire" -> aliases.add("FIRE");
            case "mob", "player", "sting" -> aliases.add("ENTITY_ATTACK");
            case "arrow", "trident", "thrown" -> aliases.add("PROJECTILE");
            case "drown" -> aliases.add("DROWNING");
            case "hotfloor" -> aliases.add("HOT_FLOOR");
            case "flyintowall" -> aliases.add("FLY_INTO_WALL");
            case "starve" -> aliases.add("STARVATION");
            case "fallingblock" -> aliases.add("FALLING_BLOCK");
            case "indirectmagic", "magic" -> aliases.add("MAGIC");
            case "dragonbreath" -> aliases.add("DRAGON_BREATH");
            default -> { }
        }
        aliases.add(source.getName());
        return aliases;
    }

    private static boolean isSpawnerSpawned(LivingEntity entity) {
        return entity instanceof SpawnerTracked tracked && tracked.svframemmo$fromSpawner();
    }

    private static String projectileType(ProjectileEntity projectile) {
        String path = Registries.ENTITY_TYPE.getId(projectile.getType()).getPath().toUpperCase(Locale.ROOT);
        return path.contains("TRIDENT") ? "TRIDENT" : path.contains("ARROW") ? "ARROW" : path;
    }

    private static String blockId(BlockState state) { return id(Registries.BLOCK.getId(state.getBlock()).toString()); }
    private static String itemId(ItemStack stack) { return id(Registries.ITEM.getId(stack.getItem()).toString()); }
    private static String id(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()).toUpperCase(Locale.ROOT) : value.toUpperCase(Locale.ROOT);
    }

    private static LinkedHashSet<String> potionEffects(PotionContentsComponent contents) {
        LinkedHashSet<String> effects = new LinkedHashSet<>();
        if (contents == null) return effects;
        contents.getEffects().forEach(effect -> effects.add(Registries.STATUS_EFFECT.getId(effect.getEffectType().value()).getPath().toUpperCase(Locale.ROOT)));
        return effects;
    }

    private static String potionKey(PotionContentsComponent contents) {
        if (contents == null) return "";
        return contents.potion().flatMap(RegistryEntry::getKey).map(key -> key.getValue().getPath()).orElse("").toLowerCase(Locale.ROOT);
    }

    private static String normalizePotionEffect(String potionKey) {
        if (potionKey == null || potionKey.isBlank()) return "";
        String key = potionKey.toLowerCase(Locale.ROOT);
        if (key.startsWith("long_")) key = key.substring(5);
        if (key.startsWith("strong_")) key = key.substring(7);
        return switch (key) {
            case "swiftness" -> "SPEED";
            case "leaping" -> "JUMP";
            case "healing" -> "INSTANT_HEAL";
            case "harming" -> "INSTANT_DAMAGE";
            case "regeneration" -> "REGEN";
            default -> key.toUpperCase(Locale.ROOT);
        };
    }

    private static double mapNumber(Object raw, String key, double fallback) {
        if (!(raw instanceof Map<?, ?> map)) return fallback;
        Object value = map.get(key);
        if (value == null) value = map.get(key.toUpperCase(Locale.ROOT));
        if (value == null) value = map.get(key.toLowerCase(Locale.ROOT));
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return fallback;
        try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException ignored) { return fallback; }
    }

    private static Object nestedMap(Object raw, String key) {
        if (!(raw instanceof Map<?, ?> map)) return null;
        return map.get(key);
    }

    private record PositionState(String dimension, Vec3d position, int blockX, int blockY, int blockZ) {
        private static PositionState capture(ServerPlayerEntity player, String dimension) {
            return new PositionState(dimension, player.getPos(), player.getBlockX(), player.getBlockY(), player.getBlockZ());
        }
    }
    private record PlacedBlock(String dimension, long pos) {
        private static PlacedBlock of(ServerWorld world, BlockPos pos) { return new PlacedBlock(world.getRegistryKey().getValue().toString(), pos.asLong()); }
    }

    public interface SpawnerTracked {
        boolean svframemmo$fromSpawner();
        void svframemmo$setFromSpawner(boolean value);
    }
}
