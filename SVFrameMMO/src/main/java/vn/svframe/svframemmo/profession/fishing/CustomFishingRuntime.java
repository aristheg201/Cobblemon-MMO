package vn.svframe.svframemmo.profession.fishing;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.api.MMOLineConfig;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.CustomPlayerFishEvent;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.experience.EXPSource;
import vn.svframe.svframemmo.experience.Profession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Native Fabric implementation of the tug-based custom fishing profession gameplay. */
public final class CustomFishingRuntime {
    /** Returned by {@link #onUse} when vanilla fishing must continue untouched. */
    public static final int PASS = Integer.MIN_VALUE;

    private static final Logger LOG = Logger.getLogger("SVFrameMMO-CustomFishing");
    private static final CustomFishingRuntime INSTANCE = new CustomFishingRuntime();
    private static final long TIMEOUT_TICKS = 20L;
    private static final double CHANCE_FACTOR = 7d / 100d;
    private static final double CHANCE_POWER = 1d / 3d;

    private final Map<UUID, Session> sessions = new HashMap<>();
    private Profession loadedProfession;
    private List<DropTable> tables = List.of();

    private CustomFishingRuntime() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    public static CustomFishingRuntime instance() {
        return INSTANCE;
    }

    /**
     * Handles a rod use for an existing fishing hook.
     * @return {@link #PASS} to keep vanilla behavior, otherwise the rod durability cost returned by FishingBobberEntity.use.
     */
    public synchronized int onUse(FishingBobberEntity hook, ServerPlayerEntity player, ItemStack rod, boolean biteReady) {
        if (hook == null || player == null) return PASS;
        reloadIfNeeded();
        if (tables.isEmpty()) return PASS;

        Session active = sessions.get(hook.getUuid());
        if (active != null) {
            if (!active.playerId.equals(player.getUuid())) return 0;
            if (active.expired(SVFrameMMO.currentTick())) {
                close(active, true);
                return 0;
            }
            return pull(active);
        }

        if (!biteReady) return PASS;
        DropTable table = firstMatchingTable(player, hook);
        if (table == null) return PASS;
        FishingDrop drop = table.roll(playerData(player));
        if (drop == null) return PASS;

        PlayerData data = playerData(player);
        double strengthStat = data.getMMOPlayerData().getStatMap().getStat("FISHING_STRENGTH");
        int strength = Math.max(0, (int) Math.floor(drop.tugs.roll() * (1d - strengthStat / 100d)));
        Session session = new Session(hook, player.getUuid(), drop, strength, drop.experience.roll(), drop.vanillaExp.roll(), SVFrameMMO.currentTick());
        sessions.put(hook.getUuid(), session);

        if (strength == 0) {
            loot(session);
            return 1;
        }
        return 0;
    }

    public synchronized void clear() {
        for (Session session : List.copyOf(sessions.values())) close(session, false);
        sessions.clear();
    }

    public synchronized int activeSessions() {
        return sessions.size();
    }

    private int pull(Session session) {
        ServerPlayerEntity player = findPlayer(session.playerId);
        if (player == null || session.hook.isRemoved()) {
            close(session, false);
            return 0;
        }

        PlayerData data = playerData(player);
        if (session.pulls == 0 && ThreadLocalRandom.current().nextDouble() < pct(data, "CRITICAL_FISHING_CHANCE")) {
            session.critical = true;
            session.pulls = session.requiredPulls + 2;
        }
        session.lastPullTick = SVFrameMMO.currentTick();
        session.pulls++;
        if (session.pulls >= session.requiredPulls) {
            loot(session);
            return 1;
        }
        return 0;
    }

    private void loot(Session session) {
        ServerPlayerEntity player = findPlayer(session.playerId);
        if (player == null) {
            close(session, false);
            return;
        }

        FishingBobberEntity hook = session.hook;
        PlayerData data = playerData(player);
        sessions.remove(hook.getUuid());

        if (!session.critical && ThreadLocalRandom.current().nextDouble() < pct(data, "CRITICAL_FISHING_FAILURE_CHANCE")) {
            Vec3d delta = hook.getPos().subtract(player.getPos());
            Vec3d horizontal = new Vec3d(delta.x, 0, delta.z);
            if (horizontal.lengthSquared() > 1.0E-6) {
                Vec3d launch = horizontal.normalize().multiply(3d).add(0d, .5d, 0d);
                player.setVelocity(launch);
                player.velocityModified = true;
            }
            hook.discard();
            return;
        }

        ItemStack caught = session.drop.rollStack(data);
        if (!caught.isEmpty()) {
            ServerWorld world = player.getServerWorld();
            ItemEntity entity = new ItemEntity(world, hook.getX(), hook.getY(), hook.getZ(), caught.copy());
            world.spawnEntity(entity);

            CustomPlayerFishEvent event = new CustomPlayerFishEvent(data, entity).call();
            if (event.isCancelled()) {
                entity.discard();
                hook.discard();
                return;
            }

            ItemStack finalCaught = event.getCaught().copy();
            Vec3d delta = player.getPos().subtract(hook.getPos());
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            entity.setVelocity(delta.x * .08d, delta.y * .031d + horizontal * .05d, delta.z * .08d);
            SVFrameMMO.nativeExperience().onFishCaught(player, finalCaught);
        }

        if (session.vanillaExperience > 0) player.addExperience(session.vanillaExperience);
        Profession fishing = SVFrameMMO.professions().get("fishing");
        if (fishing != null && session.professionExperience > 0)
            data.getProfessions().giveExperience(fishing, session.professionExperience, EXPSource.SOURCE);

        hook.discard();
    }

    private void tick(MinecraftServer server) {
        if (sessions.isEmpty()) return;
        long now = SVFrameMMO.currentTick();
        synchronized (this) {
            for (Session session : List.copyOf(sessions.values())) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.playerId);
                if (player == null || session.hook.isRemoved()) {
                    close(session, false);
                } else if (session.expired(now)) {
                    close(session, true);
                }
            }
        }
    }

    private void close(Session session, boolean discardHook) {
        sessions.remove(session.hook.getUuid());
        if (discardHook && !session.hook.isRemoved()) session.hook.discard();
    }

    private DropTable firstMatchingTable(ServerPlayerEntity player, FishingBobberEntity hook) {
        for (DropTable table : tables) if (table.matches(player, hook)) return table;
        return null;
    }

    private ServerPlayerEntity findPlayer(UUID id) {
        MinecraftServer server = sessions.values().stream().findFirst().map(s -> s.hook.getServer()).orElse(null);
        return server == null ? null : server.getPlayerManager().getPlayer(id);
    }

    private static PlayerData playerData(ServerPlayerEntity player) {
        return SVFrameMMO.playerData().get(player);
    }

    private static double pct(PlayerData data, String stat) {
        return Math.max(0d, Math.min(1d, data.getMMOPlayerData().getStatMap().getStat(stat) / 100d));
    }

    private synchronized void reloadIfNeeded() {
        Profession profession = SVFrameMMO.professions().get("fishing");
        if (profession == loadedProfession) return;
        loadedProfession = profession;
        if (profession == null) {
            tables = List.of();
            return;
        }
        try {
            tables = parseTables(map(profession.getRawConfig().get("on-fish")));
        } catch (RuntimeException exception) {
            LOG.log(Level.WARNING, "Could not reload native custom fishing", exception);
            tables = List.of();
        }
    }

    private static List<DropTable> parseTables(Map<String, Object> section) {
        ArrayList<DropTable> result = new ArrayList<>();
        section.forEach((id, raw) -> {
            try {
                Map<String, Object> table = map(raw);
                ArrayList<FishingCondition> conditions = new ArrayList<>();
                for (String line : strings(table.get("conditions"))) conditions.add(FishingCondition.parse(line));
                ArrayList<FishingDrop> items = new ArrayList<>();
                for (String line : strings(table.get("items"))) items.add(FishingDrop.parse(line));
                if (!items.isEmpty()) result.add(new DropTable(id, List.copyOf(conditions), List.copyOf(items)));
            } catch (RuntimeException exception) {
                LOG.log(Level.WARNING, "Could not load fishing table '" + id + "': " + exception.getMessage());
            }
        });
        return List.copyOf(result);
    }

    private record DropTable(String id, List<FishingCondition> conditions, List<FishingDrop> items) {
        boolean matches(ServerPlayerEntity player, FishingBobberEntity hook) {
            for (FishingCondition condition : conditions) if (!condition.matches(player, hook)) return false;
            return true;
        }

        FishingDrop roll(PlayerData player) {
            if (items.isEmpty()) return null;
            double luck = CHANCE_FACTOR * player.getMMOPlayerData().getStatMap().getStat("CHANCE");
            double exponent = Math.pow(1d + Math.max(-.99d, luck), -CHANCE_POWER);
            double total = 0d;
            for (FishingDrop item : items) total += Math.pow(Math.max(1d, item.weight), exponent);
            if (total <= 0d) return items.getFirst();
            double roll = ThreadLocalRandom.current().nextDouble(total);
            double cursor = 0d;
            for (FishingDrop item : items) {
                cursor += Math.pow(Math.max(1d, item.weight), exponent);
                if (roll <= cursor) return item;
            }
            return items.getLast();
        }
    }

    @FunctionalInterface
    private interface FishingCondition {
        boolean matches(ServerPlayerEntity player, FishingBobberEntity hook);

        static FishingCondition parse(String line) {
            MMOLineConfig config = new MMOLineConfig(line);
            String key = norm(config.getKey());
            return switch (key) {
                case "world" -> {
                    Set<String> values = csv(config.getString("name", ""));
                    yield (player, hook) -> {
                        String full = player.getServerWorld().getRegistryKey().getValue().toString().toLowerCase(Locale.ROOT);
                        String path = player.getServerWorld().getRegistryKey().getValue().getPath().toLowerCase(Locale.ROOT);
                        return values.isEmpty() || values.contains(full) || values.contains(path);
                    };
                }
                case "biome" -> {
                    Set<String> values = csv(config.getString("name", ""));
                    yield (player, hook) -> {
                        BlockPos pos = hook.getBlockPos();
                        String biome = player.getServerWorld().getBiome(pos).getKey().map(k -> k.getValue().getPath()).orElse("").toLowerCase(Locale.ROOT);
                        return values.isEmpty() || values.contains(biome);
                    };
                }
                case "height", "y" -> {
                    int min = config.getInt("min", Integer.MIN_VALUE), max = config.getInt("max", Integer.MAX_VALUE);
                    yield (player, hook) -> hook.getY() >= min && hook.getY() <= max;
                }
                default -> throw new IllegalArgumentException("Unsupported native fishing condition '" + config.getKey() + "'");
            };
        }
    }

    private record FishingDrop(Item item, Range tugs, Range experience, Range vanillaExp, double chance, Range amount, double weight) {
        static FishingDrop parse(String line) {
            MMOLineConfig config = new MMOLineConfig(line);
            if (!norm(config.getKey()).equals("vanilla"))
                throw new IllegalArgumentException("Only vanilla{} fishing items are available without an item-provider integration: " + line);
            String rawType = config.getString("type", "");
            Identifier id = Identifier.tryParse(rawType.contains(":") ? rawType.toLowerCase(Locale.ROOT) : "minecraft:" + rawType.toLowerCase(Locale.ROOT));
            if (id == null || !Registries.ITEM.containsId(id)) throw new IllegalArgumentException("Unknown fishing item '" + rawType + "'");
            String[] args = config.args();
            double chance = args.length > 0 ? decimal(args[0], 1d) : 1d;
            Range amount = args.length > 1 ? Range.parse(args[1], 1) : new Range(1, 1);
            double weight = args.length > 2 ? decimal(args[2], 0d) : 0d;
            Range tugs = Range.parse(config.getString("tugs", "0"), 0);
            Range experience = Range.parse(config.getString("experience", "0"), 0);
            Range vanillaExp = Range.parse(config.getString("vanilla-exp", "0"), 0);
            return new FishingDrop(Registries.ITEM.get(id), tugs, experience, vanillaExp,
                    Math.max(0d, Math.min(1d, chance)), amount, weight <= 0d ? 1d : weight);
        }

        ItemStack rollStack(PlayerData player) {
            double effectiveLuck = CHANCE_FACTOR * player.getMMOPlayerData().getStatMap().getStat("CHANCE");
            double adjustedChance = Math.pow(chance, Math.pow(1d + Math.max(-.99d, effectiveLuck), CHANCE_POWER));
            if (ThreadLocalRandom.current().nextDouble() >= adjustedChance) return ItemStack.EMPTY;
            int count = amount.roll();
            return count <= 0 ? ItemStack.EMPTY : new ItemStack(item, count);
        }
    }

    private record Range(int min, int max) {
        static Range parse(String raw, int fallback) {
            if (raw == null || raw.isBlank()) return new Range(fallback, fallback);
            String value = raw.trim();
            int dash = value.indexOf('-', 1);
            try {
                if (dash < 0) {
                    int single = (int) Math.round(Double.parseDouble(value));
                    return new Range(single, single);
                }
                int a = (int) Math.round(Double.parseDouble(value.substring(0, dash)));
                int b = (int) Math.round(Double.parseDouble(value.substring(dash + 1)));
                return new Range(Math.min(a, b), Math.max(a, b));
            } catch (RuntimeException ignored) {
                return new Range(fallback, fallback);
            }
        }

        int roll() {
            return min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        }
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

    private static Set<String> csv(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        var result = new java.util.LinkedHashSet<String>();
        for (String part : raw.split(",")) if (!part.isBlank()) result.add(part.trim().toLowerCase(Locale.ROOT));
        return Set.copyOf(result);
    }

    private static String norm(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static double decimal(String raw, double fallback) {
        try { return Double.parseDouble(raw); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static final class Session {
        final FishingBobberEntity hook;
        final UUID playerId;
        final FishingDrop drop;
        final int requiredPulls;
        final int professionExperience;
        final int vanillaExperience;
        int pulls;
        long lastPullTick;
        boolean critical;

        Session(FishingBobberEntity hook, UUID playerId, FishingDrop drop, int requiredPulls,
                int professionExperience, int vanillaExperience, long now) {
            this.hook = hook;
            this.playerId = playerId;
            this.drop = drop;
            this.requiredPulls = requiredPulls;
            this.professionExperience = professionExperience;
            this.vanillaExperience = vanillaExperience;
            this.lastPullTick = now;
        }

        boolean expired(long now) {
            return now - lastPullTick > TIMEOUT_TICKS;
        }
    }
}
