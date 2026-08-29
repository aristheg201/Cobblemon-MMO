package vn.svframe.svframemmo.profession.mining;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import vn.svframe.svframelib.api.MMOLineConfig;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.config.DefaultFiles;
import vn.svframe.svframemmo.experience.Profession;
import vn.svframe.svframemmo.trigger.NativeTriggerRegistry;
import vn.svframe.svframemmo.trigger.Trigger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Native custom-mining runtime with drop control, triggers, anti-duplication and tick-based regeneration. */
public final class CustomMiningRuntime {
    private static final Logger LOG = Logger.getLogger("SVFrameMMO-CustomMining");
    private static final CustomMiningRuntime INSTANCE = new CustomMiningRuntime();
    private final Map<BlockKey, RegenEntry> regenerating = new HashMap<>();
    private final Set<BlockKey> playerPlaced = new HashSet<>();
    private final Map<PendingKey, PendingBreak> pendingVanilla = new HashMap<>();
    private Profession loadedProfession;
    private Settings settings = Settings.disabled();
    private List<Definition> definitions = List.of();

    private CustomMiningRuntime() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::restoreAll);
    }

    public static CustomMiningRuntime instance() { return INSTANCE; }

    public synchronized void markPlaced(ServerWorld world, BlockPos pos) {
        playerPlaced.add(BlockKey.of(world, pos));
    }

    public synchronized BreakDecision beforeBreak(ServerPlayerEntity player, BlockPos pos) {
        if (player == null || pos == null || player.isCreative() || player.isSpectator()) return BreakDecision.PASS;
        reloadIfNeeded();
        if (!settings.enabled || !settings.matches(player)) return BreakDecision.PASS;
        ServerWorld world = player.getServerWorld();
        BlockKey key = BlockKey.of(world, pos);
        if (regenerating.containsKey(key)) return BreakDecision.DENY;
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return BreakDecision.PASS;
        Definition definition = findDefinition(state);
        if (definition == null) return settings.protectVanillaBlocks ? BreakDecision.DENY : BreakDecision.PASS;
        if (!definition.matchesConditions(player, world, pos, state)) return BreakDecision.DENY;
        if (settings.enableToolRestrictions && state.isToolRequired() && !player.getMainHandStack().isSuitableFor(state)) return BreakDecision.DENY;
        boolean placed = playerPlaced.remove(key);
        if (definition.vanillaDrops) {
            pendingVanilla.put(new PendingKey(player.getUuid(), key), new PendingBreak(definition, state, placed));
            return BreakDecision.PASS;
        }
        completeCustomBreak(player, world, pos, state, definition, placed);
        return BreakDecision.HANDLED;
    }

    public synchronized void afterVanillaBreak(ServerPlayerEntity player, BlockPos pos, boolean success) {
        if (player == null || pos == null) return;
        BlockKey key = BlockKey.of(player.getServerWorld(), pos);
        PendingBreak pending = pendingVanilla.remove(new PendingKey(player.getUuid(), key));
        if (pending == null) return;
        if (!success) {
            if (pending.playerPlaced) playerPlaced.add(key);
            return;
        }
        applyRewards(player, player.getServerWorld(), pos, pending.definition, pending.playerPlaced);
        scheduleRegen(player.getServerWorld(), pos, pending.originalState, pending.definition);
    }

    private void completeCustomBreak(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state, Definition definition, boolean placed) {
        List<ItemStack> drops = definition.rollDrops();
        world.breakBlock(pos, false, player);
        ItemStack tool = player.getMainHandStack();
        if (!tool.isEmpty() && tool.isDamageable())
            tool.damage(1, world, player, item -> player.sendEquipmentBreakStatus(item, net.minecraft.entity.EquipmentSlot.MAINHAND));
        for (ItemStack stack : drops) if (!stack.isEmpty()) Block.dropStack(world, pos, stack);
        applyTriggers(player, definition, placed);
        SVFrameMMO.nativeExperience().onCustomBlockBroken(player, world, pos, state, placed);
        scheduleRegen(world, pos, state, definition);
    }

    private void applyRewards(ServerPlayerEntity player, ServerWorld world, BlockPos pos, Definition definition, boolean placed) {
        for (ItemStack stack : definition.rollDrops()) if (!stack.isEmpty()) Block.dropStack(world, pos, stack);
        applyTriggers(player, definition, placed);
    }

    private void applyTriggers(ServerPlayerEntity player, Definition definition, boolean placed) {
        if (placed) return;
        var data = SVFrameMMO.playerData().get(player);
        for (Trigger trigger : definition.triggers) trigger.schedule(data);
    }

    private void scheduleRegen(ServerWorld world, BlockPos pos, BlockState original, Definition definition) {
        if (definition.regenTicks <= 0) return;
        BlockKey key = BlockKey.of(world, pos);
        if (regenerating.containsKey(key)) return;
        if (definition.temporaryBlock != null) world.setBlockState(pos, definition.temporaryBlock, Block.NOTIFY_ALL);
        regenerating.put(key, new RegenEntry(key, original, SVFrameMMO.currentTick() + definition.regenTicks));
    }

    private synchronized void tick(MinecraftServer server) {
        if (regenerating.isEmpty()) return;
        long now = SVFrameMMO.currentTick();
        var iterator = regenerating.entrySet().iterator();
        while (iterator.hasNext()) {
            RegenEntry entry = iterator.next().getValue();
            if (now < entry.restoreTick) continue;
            ServerWorld world = server.getWorld(entry.key.dimension);
            if (world != null) world.setBlockState(BlockPos.fromLong(entry.key.pos), entry.originalState, Block.NOTIFY_ALL);
            iterator.remove();
        }
    }

    private synchronized void restoreAll(MinecraftServer server) {
        for (RegenEntry entry : regenerating.values()) {
            ServerWorld world = server.getWorld(entry.key.dimension);
            if (world != null) world.setBlockState(BlockPos.fromLong(entry.key.pos), entry.originalState, Block.NOTIFY_ALL);
        }
        regenerating.clear();
        pendingVanilla.clear();
        playerPlaced.clear();
    }

    private Definition findDefinition(BlockState state) {
        String id = Registries.BLOCK.getId(state.getBlock()).toString();
        for (Definition definition : definitions) if (definition.matchesBlock(id)) return definition;
        return null;
    }

    private void reloadIfNeeded() {
        Profession profession = SVFrameMMO.professions().get("mining");
        if (profession == loadedProfession) return;
        loadedProfession = profession;
        if (profession == null) { definitions = List.of(); settings = Settings.disabled(); return; }
        try {
            Map<String, Object> root = map(YamlLite.parse(DefaultFiles.ROOT.resolve("config.yml")));
            settings = Settings.parse(map(root.get("custom-mining")));
            definitions = parseDefinitions(map(profession.getRawConfig().get("on-mine")));
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "Could not reload native custom mining", exception);
            settings = Settings.disabled();
            definitions = List.of();
        }
    }

    private static List<Definition> parseDefinitions(Map<String, Object> section) {
        ArrayList<Definition> result = new ArrayList<>();
        section.forEach((id, raw) -> {
            try { result.add(Definition.parse(id, map(raw))); }
            catch (RuntimeException exception) { LOG.log(Level.WARNING, "Could not load custom mining block '" + id + "': " + exception.getMessage()); }
        });
        return List.copyOf(result);
    }

    private static Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static List<String> strings(Object raw) {
        if (raw instanceof Collection<?> collection) {
            ArrayList<String> result = new ArrayList<>();
            for (Object value : collection) if (value != null) result.add(String.valueOf(value));
            return result;
        }
        return raw == null ? List.of() : List.of(String.valueOf(raw));
    }

    private static boolean bool(Object raw, boolean fallback) { return raw == null ? fallback : raw instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(raw)); }
    private static long integer(Object raw, long fallback) {
        try { return raw instanceof Number n ? n.longValue() : raw == null ? fallback : Long.parseLong(String.valueOf(raw)); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static String norm(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-'); }

    private record Settings(boolean enabled, boolean protectVanillaBlocks, boolean enableToolRestrictions, List<Condition> conditions) {
        static Settings disabled() { return new Settings(false, false, false, List.of()); }
        static Settings parse(Map<String, Object> raw) {
            List<Condition> conditions = strings(raw.get("conditions")).stream().map(Condition::parse).toList();
            return new Settings(bool(raw.get("enable"), true), bool(raw.get("protect-vanilla-blocks"), false), bool(raw.get("enable-tool-restrictions"), false), conditions);
        }
        boolean matches(ServerPlayerEntity player) {
            for (Condition condition : conditions) if (!condition.matches(player)) return false;
            return true;
        }
    }

    private interface Condition {
        boolean matches(ServerPlayerEntity player);
        static Condition parse(String line) {
            MMOLineConfig config = new MMOLineConfig(line);
            return switch (norm(config.getKey())) {
                case "world" -> {
                    Set<String> worlds = splitCsv(config.getString("name", ""));
                    yield player -> worlds.isEmpty() || worlds.contains(player.getServerWorld().getRegistryKey().getValue().toString().toLowerCase(Locale.ROOT)) || worlds.contains(player.getServerWorld().getRegistryKey().getValue().getPath().toLowerCase(Locale.ROOT));
                }
                case "height", "y" -> {
                    double min = config.getDouble("min", -Double.MAX_VALUE), max = config.getDouble("max", Double.MAX_VALUE);
                    yield player -> player.getY() >= min && player.getY() <= max;
                }
                default -> throw new IllegalArgumentException("Unsupported native custom-mining condition '" + config.getKey() + "'");
            };
        }
    }

    private record Definition(String id, String blockId, boolean vanillaDrops, List<Drop> drops, List<Trigger> triggers,
                              List<BlockCondition> conditions, long regenTicks, BlockState temporaryBlock) {
        static Definition parse(String id, Map<String, Object> raw) {
            String material = String.valueOf(raw.getOrDefault("material", ""));
            String blockId = parseVanillaType(material);
            if (blockId.isBlank()) throw new IllegalArgumentException("missing/unsupported material '" + material + "'");
            Map<String, Object> options = map(raw.get("options"));
            Map<String, Object> dropTable = map(raw.get("drop-table"));
            ArrayList<Drop> drops = new ArrayList<>();
            for (String line : strings(dropTable.get("items"))) drops.add(Drop.parse(line));
            ArrayList<Trigger> triggers = new ArrayList<>();
            for (String line : strings(raw.get("triggers"))) triggers.add(NativeTriggerRegistry.parse(line));
            ArrayList<BlockCondition> conditions = new ArrayList<>();
            for (String line : strings(raw.get("conditions"))) conditions.add(BlockCondition.parse(line));
            Map<String, Object> regen = map(raw.get("regen"));
            long regenTicks = Math.max(0L, integer(regen.get("time"), 0L));
            BlockState temp = parseBlockState(regen.get("temp-block"));
            return new Definition(id, blockId, bool(options.get("vanilla-drops"), true), List.copyOf(drops), List.copyOf(triggers), List.copyOf(conditions), regenTicks, temp);
        }
        boolean matchesBlock(String actual) { return actual.equalsIgnoreCase(blockId); }
        boolean matchesConditions(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state) {
            for (BlockCondition condition : conditions) if (!condition.matches(player, world, pos, state)) return false;
            return true;
        }
        List<ItemStack> rollDrops() {
            ArrayList<ItemStack> result = new ArrayList<>();
            for (Drop drop : drops) { ItemStack stack = drop.roll(); if (!stack.isEmpty()) result.add(stack); }
            return result;
        }
    }

    private interface BlockCondition {
        boolean matches(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state);
        static BlockCondition parse(String line) {
            MMOLineConfig config = new MMOLineConfig(line);
            return switch (norm(config.getKey())) {
                case "world" -> {
                    Set<String> worlds = splitCsv(config.getString("name", ""));
                    yield (player, world, pos, state) -> worlds.isEmpty() || worlds.contains(world.getRegistryKey().getValue().toString().toLowerCase(Locale.ROOT)) || worlds.contains(world.getRegistryKey().getValue().getPath().toLowerCase(Locale.ROOT));
                }
                case "height", "y" -> {
                    int min = config.getInt("min", Integer.MIN_VALUE), max = config.getInt("max", Integer.MAX_VALUE);
                    yield (player, world, pos, state) -> pos.getY() >= min && pos.getY() <= max;
                }
                default -> throw new IllegalArgumentException("Unsupported block condition '" + config.getKey() + "'");
            };
        }
    }

    private record Drop(Item item, double chance, int min, int max) {
        static Drop parse(String line) {
            MMOLineConfig config = new MMOLineConfig(line);
            if (!norm(config.getKey()).equals("vanilla")) throw new IllegalArgumentException("Only native vanilla{} custom-mining drops are supported: " + line);
            String rawType = config.getString("type", "");
            Identifier id = Identifier.of(rawType.contains(":") ? rawType.toLowerCase(Locale.ROOT) : "minecraft:" + rawType.toLowerCase(Locale.ROOT));
            Item item = Registries.ITEM.get(id);
            if (item == null || item == net.minecraft.item.Items.AIR && !id.toString().equals("minecraft:air")) throw new IllegalArgumentException("Unknown item '" + id + "'");
            String[] args = config.args();
            double chance = args.length > 0 ? parseDouble(args[0], 1d) : 1d;
            int[] amount = args.length > 1 ? range(args[1]) : new int[]{1, 1};
            return new Drop(item, Math.max(0d, Math.min(1d, chance)), Math.max(0, amount[0]), Math.max(0, amount[1]));
        }
        ItemStack roll() {
            if (chance <= 0d || ThreadLocalRandom.current().nextDouble() > chance) return ItemStack.EMPTY;
            int count = min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
            return count <= 0 ? ItemStack.EMPTY : new ItemStack(item, count);
        }
    }

    private static BlockState parseBlockState(Object raw) {
        if (raw == null) return null;
        String id = parseVanillaType(String.valueOf(raw));
        if (id.isBlank()) return null;
        Block block = Registries.BLOCK.get(Identifier.of(id));
        return block == null || block == Blocks.AIR ? null : block.getDefaultState();
    }

    private static String parseVanillaType(String line) {
        if (line == null || line.isBlank()) return "";
        MMOLineConfig config = new MMOLineConfig(line);
        String type;
        if (norm(config.getKey()).equals("vanilla")) type = config.getString("type", "");
        else if (!line.contains("{") && !line.contains("}")) type = line.trim();
        else return "";
        if (type.isBlank()) return "";
        String id = type.contains(":") ? type.toLowerCase(Locale.ROOT) : "minecraft:" + type.toLowerCase(Locale.ROOT);
        Identifier identifier = Identifier.tryParse(id);
        return identifier != null && Registries.BLOCK.containsId(identifier) ? id : "";
    }

    private static Set<String> splitCsv(String input) {
        HashSet<String> values = new HashSet<>();
        if (input != null) for (String part : input.split(",")) if (!part.isBlank()) values.add(part.trim().toLowerCase(Locale.ROOT));
        return values;
    }
    private static int[] range(String raw) {
        if (raw == null || raw.isBlank()) return new int[]{1, 1};
        String value = raw.trim(); int dash = value.indexOf('-');
        try {
            if (dash < 1) { int amount = Integer.parseInt(value); return new int[]{amount, amount}; }
            int a = Integer.parseInt(value.substring(0, dash)), b = Integer.parseInt(value.substring(dash + 1));
            return new int[]{Math.min(a, b), Math.max(a, b)};
        } catch (RuntimeException ignored) { return new int[]{1, 1}; }
    }
    private static double parseDouble(String raw, double fallback) { try { return Double.parseDouble(raw); } catch (RuntimeException ignored) { return fallback; } }

    public enum BreakDecision { PASS, DENY, HANDLED }
    private record PendingBreak(Definition definition, BlockState originalState, boolean playerPlaced) { }
    private record PendingKey(java.util.UUID player, BlockKey block) { }
    private record RegenEntry(BlockKey key, BlockState originalState, long restoreTick) { }
    private record BlockKey(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension, long pos) {
        static BlockKey of(ServerWorld world, BlockPos pos) { return new BlockKey(world.getRegistryKey(), pos.asLong()); }
    }
}
