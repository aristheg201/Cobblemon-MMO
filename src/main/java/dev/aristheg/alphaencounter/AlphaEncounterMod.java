package dev.aristheg.alphaencounter;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AlphaEncounterMod implements ModInitializer {
    public static final String MOD_ID = "alpha_encounter";
    public static final Logger LOGGER = LoggerFactory.getLogger("Alpha-Encounter");
    public static final Runtime RUNTIME = new Runtime();

    @Override
    public void onInitialize() {
        RUNTIME.reload();
        ServerTickEvents.END_SERVER_TICK.register(RUNTIME::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            CommandManager.literal("alphaencounter")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("reload").executes(ctx -> {
                    RUNTIME.reload();
                    ctx.getSource().sendFeedback(() -> Text.literal("Alpha-Encounter config reloaded."), false);
                    return 1;
                }))
                .then(CommandManager.literal("debug").executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal(RUNTIME.perfLine()), false);
                    return 1;
                }))
        ));
        LOGGER.info("Alpha-Encounter initialized (standalone Cobblemon integration; no SVFrame dependency).");
    }

    public static final class Runtime {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private final Map<UUID, ActiveEncounter> activeByEntity = new ConcurrentHashMap<>();
        private final Map<String, Integer> activeByDefinition = new HashMap<>();
        private final Map<String, Long> globalCooldownUntil = new HashMap<>();
        private Config config = Config.defaults();
        private long tick;
        private long spawnChecks;
        private long spawnSuccess;
        private long interceptedHits;
        private long battlesQueued;

        public void reload() {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("alpha-encounter");
            Path file = dir.resolve("config.json");
            try {
                Files.createDirectories(dir);
                if (Files.notExists(file)) {
                    Files.writeString(file, GSON.toJson(Config.defaults()));
                }
                Config loaded = GSON.fromJson(Files.readString(file), Config.class);
                config = loaded == null ? Config.defaults() : loaded.normalized();
                LOGGER.info("Loaded {} Alpha-Encounter definitions and {} tiers.", config.encounters.size(), config.tiers.size());
            } catch (Exception e) {
                LOGGER.error("Failed to load Alpha-Encounter config; keeping previous config.", e);
            }
        }

        public void tick(MinecraftServer server) {
            tick++;
            processPendingBattles(server);
            cleanupDeadEntries(server);
            int cadence = Math.max(20, config.general.spawnCheckIntervalTicks);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                // Stable staggering prevents all players from issuing spawn work on the same tick.
                int offset = Math.floorMod(player.getUuid().hashCode(), cadence);
                if ((tick + offset) % cadence == 0) trySpawnNear(player);
            }
        }

        private void trySpawnNear(ServerPlayerEntity player) {
            if (!(player.getWorld() instanceof ServerWorld world) || player.isSpectator()) return;
            spawnChecks++;
            String dimension = world.getRegistryKey().getValue().toString();
            List<EncounterDefinition> eligible = new ArrayList<>();
            double totalWeight = 0.0;
            for (EncounterDefinition def : config.encounters) {
                if (!def.enabled || def.spawn == null || def.spawn.weight <= 0.0) continue;
                if (!def.spawn.dimensions.isEmpty() && !def.spawn.dimensions.contains(dimension)) continue;
                if (activeByDefinition.getOrDefault(def.id, 0) >= Math.max(1, def.spawn.maxActive)) continue;
                if (globalCooldownUntil.getOrDefault(def.id, 0L) > tick) continue;
                eligible.add(def);
                totalWeight += def.spawn.weight;
            }
            if (eligible.isEmpty() || totalWeight <= 0.0) return;
            if (ThreadLocalRandomHolder.nextDouble() > config.general.spawnAttemptChance) return;

            double roll = ThreadLocalRandomHolder.nextDouble() * totalWeight;
            EncounterDefinition picked = null;
            for (EncounterDefinition def : eligible) {
                roll -= def.spawn.weight;
                if (roll <= 0.0) { picked = def; break; }
            }
            if (picked == null) return;

            BlockPos origin = player.getBlockPos();
            int min = Math.max(8, picked.spawn.minDistance);
            int max = Math.max(min, picked.spawn.maxDistance);
            double angle = ThreadLocalRandomHolder.nextDouble() * Math.PI * 2.0;
            int radius = min + ThreadLocalRandomHolder.nextInt(max - min + 1);
            int x = origin.getX() + (int)Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int)Math.round(Math.sin(angle) * radius);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos spawnPos = new BlockPos(x, y, z);
            if (!biomeAllowed(world, spawnPos, picked.spawn.biomes)) return;

            try {
                PokemonEntity entity = PokemonProperties.Companion.parse(picked.pokemon).createEntity(world);
                entity.refreshPositionAndAngles(x + 0.5, y, z + 0.5, ThreadLocalRandomHolder.nextFloat() * 360f, 0f);
                if (!world.spawnEntity(entity)) return;
                Tier tier = config.tiers.getOrDefault(picked.tier, Tier.defaults());
                ActiveEncounter active = new ActiveEncounter(picked.id, picked.tier, entity.getUuid(), tier.healthMultiplier);
                activeByEntity.put(entity.getUuid(), active);
                activeByDefinition.merge(picked.id, 1, Integer::sum);
                globalCooldownUntil.put(picked.id, tick + Math.max(0, picked.spawn.globalCooldownTicks));
                spawnSuccess++;
                LOGGER.info("Spawned encounter '{}' ({}) at {} {} {} in {}.", picked.id, picked.pokemon, x, y, z, dimension);
            } catch (Throwable t) {
                LOGGER.error("Failed to spawn encounter '{}'.", picked.id, t);
            }
        }

        private boolean biomeAllowed(ServerWorld world, BlockPos pos, List<String> allowed) {
            if (allowed == null || allowed.isEmpty()) return true;
            return world.getBiome(pos).getKey().map(key -> allowed.contains(key.getValue().toString())).orElse(false);
        }

        public boolean isManaged(Entity entity) {
            return entity != null && activeByEntity.containsKey(entity.getUuid());
        }

        public boolean shouldRedirectDamage(Entity entity) {
            return entity instanceof PokemonEntity && isManaged(entity) && !CobblemonBridge.isInBattle((PokemonEntity) entity);
        }

        public void onResolvedWorldDamage(PokemonEntity entity, DamageSource source, float resolvedDamage) {
            if (resolvedDamage <= 0f) return;
            ActiveEncounter active = activeByEntity.get(entity.getUuid());
            if (active == null) return;
            if (active.maxHp <= 0f) {
                float base = Math.max(1f, entity.getMaxHealth());
                active.maxHp = base * Math.max(1f, active.healthMultiplier);
                active.hp = active.maxHp;
            }
            active.hp = Math.max(0f, active.hp - resolvedDamage);
            interceptedHits++;

            Entity attacker = source.getAttacker();
            if (attacker instanceof ServerPlayerEntity player && active.pendingBattlePlayer == null && active.hp > 0f) {
                active.pendingBattlePlayer = player.getUuid();
                active.battleAtTick = tick + 1;
                battlesQueued++;
            }
        }

        private void processPendingBattles(MinecraftServer server) {
            for (ActiveEncounter active : activeByEntity.values()) {
                if (active.pendingBattlePlayer == null || active.battleAtTick > tick) continue;
                UUID playerId = active.pendingBattlePlayer;
                active.pendingBattlePlayer = null;
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null) continue;
                Entity raw = findEntity(server, active.entityId);
                if (!(raw instanceof PokemonEntity pokemon) || CobblemonBridge.isInBattle(pokemon)) continue;
                CobblemonBridge.startPve(player, pokemon);
            }
        }

        private void cleanupDeadEntries(MinecraftServer server) {
            if (tick % 100 != 0) return;
            Iterator<Map.Entry<UUID, ActiveEncounter>> it = activeByEntity.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, ActiveEncounter> entry = it.next();
                Entity entity = findEntity(server, entry.getKey());
                if (entity != null && !entity.isRemoved() && entry.getValue().hp != 0f) continue;
                ActiveEncounter removed = entry.getValue();
                it.remove();
                activeByDefinition.computeIfPresent(removed.definitionId, (k, v) -> v <= 1 ? null : v - 1);
            }
        }

        private Entity findEntity(MinecraftServer server, UUID id) {
            for (ServerWorld world : server.getWorlds()) {
                Entity entity = world.getEntity(id);
                if (entity != null) return entity;
            }
            return null;
        }

        public String perfLine() {
            return "AlphaEncounter active=" + activeByEntity.size() + " spawnChecks=" + spawnChecks + " spawned=" + spawnSuccess + " worldHits=" + interceptedHits + " battlesQueued=" + battlesQueued;
        }
    }

    public static final class CobblemonBridge {
        private CobblemonBridge() {}

        public static boolean isInBattle(PokemonEntity entity) {
            try {
                Method method = entity.getClass().getMethod("getBattleId");
                return method.invoke(entity) != null;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }

        public static void startPve(ServerPlayerEntity player, PokemonEntity pokemon) {
            try {
                Class<?> builderClass = Class.forName("com.cobblemon.mod.common.battles.BattleBuilder");
                Field instanceField = builderClass.getField("INSTANCE");
                Object builder = instanceField.get(null);
                for (Method method : builderClass.getMethods()) {
                    if (!method.getName().equals("pve")) continue;
                    Class<?>[] p = method.getParameterTypes();
                    if (p.length == 2 && p[0].isAssignableFrom(player.getClass()) && p[1].isAssignableFrom(pokemon.getClass())) {
                        method.invoke(builder, player, pokemon);
                        return;
                    }
                    if (p.length == 3 && p[1].isAssignableFrom(player.getClass()) && p[2].isAssignableFrom(pokemon.getClass())) {
                        Object format = p[0].getField("GEN_9_SINGLES").get(null);
                        method.invoke(builder, format, player, pokemon);
                        return;
                    }
                }
                LOGGER.error("Cobblemon BattleBuilder.pve overload not found; encounter remains in field state.");
            } catch (Throwable t) {
                LOGGER.error("Could not start Cobblemon PVE battle.", t);
            }
        }
    }

    public static final class ActiveEncounter {
        public final String definitionId;
        public final String tierId;
        public final UUID entityId;
        public final float healthMultiplier;
        public float maxHp = -1f;
        public float hp = -1f;
        public UUID pendingBattlePlayer;
        public long battleAtTick;
        ActiveEncounter(String definitionId, String tierId, UUID entityId, float healthMultiplier) {
            this.definitionId = definitionId;
            this.tierId = tierId;
            this.entityId = entityId;
            this.healthMultiplier = healthMultiplier;
        }
    }

    public static final class Config {
        public General general = new General();
        public Map<String, Tier> tiers = new LinkedHashMap<>();
        public List<EncounterDefinition> encounters = new ArrayList<>();

        static Config defaults() {
            Config c = new Config();
            c.tiers.put("regional", new Tier(4f, false));
            c.tiers.put("apex", new Tier(12f, true));
            EncounterDefinition godzilla = new EncounterDefinition();
            godzilla.id = "mount_yeager_godzilla";
            godzilla.tier = "apex";
            godzilla.pokemon = "tyranitar level=95 alpha=true cosmetic_item=godzilla";
            godzilla.spawn.dimensions.add("minecraft:overworld");
            godzilla.spawn.biomes.add("bestiary:mount_yeager");
            godzilla.spawn.weight = 0.25;
            godzilla.spawn.maxActive = 1;
            godzilla.spawn.globalCooldownTicks = 72000;
            c.encounters.add(godzilla);
            return c;
        }

        Config normalized() {
            if (general == null) general = new General();
            if (tiers == null) tiers = new LinkedHashMap<>();
            if (encounters == null) encounters = new ArrayList<>();
            encounters.removeIf(Objects::isNull);
            for (EncounterDefinition e : encounters) {
                if (e.id == null || e.id.isBlank()) e.id = "unnamed";
                if (e.tier == null || e.tier.isBlank()) e.tier = "regional";
                if (e.pokemon == null || e.pokemon.isBlank()) e.enabled = false;
                if (e.spawn == null) e.spawn = new Spawn();
            }
            return this;
        }
    }

    public static final class General {
        public int spawnCheckIntervalTicks = 100;
        public double spawnAttemptChance = 0.12;
    }

    public static final class Tier {
        public float healthMultiplier = 4f;
        public boolean aggressive = false;
        public Tier() {}
        public Tier(float healthMultiplier, boolean aggressive) { this.healthMultiplier = healthMultiplier; this.aggressive = aggressive; }
        static Tier defaults() { return new Tier(); }
    }

    public static final class EncounterDefinition {
        public String id = "unnamed";
        public boolean enabled = true;
        public String tier = "regional";
        public String pokemon = "pikachu level=50 alpha=true";
        public Spawn spawn = new Spawn();
    }

    public static final class Spawn {
        public List<String> dimensions = new ArrayList<>();
        public List<String> biomes = new ArrayList<>();
        public double weight = 1.0;
        public int minDistance = 32;
        public int maxDistance = 80;
        public int maxActive = 1;
        public long globalCooldownTicks = 12000;
    }

    private static final class ThreadLocalRandomHolder {
        private static final java.util.concurrent.ThreadLocalRandom R = java.util.concurrent.ThreadLocalRandom.current();
        static double nextDouble() { return R.nextDouble(); }
        static int nextInt(int bound) { return R.nextInt(bound); }
        static float nextFloat() { return R.nextFloat(); }
    }
}
