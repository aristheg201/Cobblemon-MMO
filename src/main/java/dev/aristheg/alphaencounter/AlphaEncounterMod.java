package dev.aristheg.alphaencounter;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class AlphaEncounterMod implements ModInitializer {
    public static final String MOD_ID = "alpha_encounter";
    public static final Logger LOGGER = LoggerFactory.getLogger("Alpha-Encounter");
    public static final Runtime RUNTIME = new Runtime();

    @Override
    public void onInitialize() {
        RUNTIME.initialize();
        ServerTickEvents.END_SERVER_TICK.register(RUNTIME::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(RUNTIME::saveState);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AdminCommands.register(dispatcher));
        LOGGER.info("Alpha-Encounter initialized (standalone Cobblemon integration; no SVFrame dependency).");
    }

    public enum EncounterState {
        IDLE,
        HUNT,
        BATTLE_PENDING,
        BATTLE,
        DEFEATED
    }

    public static final class Runtime {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private final Map<UUID, ActiveEncounter> activeByEntity = new ConcurrentHashMap<>();
        private final Map<String, Integer> activeByDefinition = new HashMap<>();
        private final Map<String, Long> globalCooldownUntil = new HashMap<>();
        private final Map<UUID, CatchWindow> catchWindows = new HashMap<>();
        private Config config = Config.defaults();
        private SavedState pendingSavedState;
        private boolean restoreApplied;
        private boolean stateDirty;
        private long tick;
        private long spawnChecks;
        private long spawnSuccess;
        private long interceptedHits;
        private long battlesQueued;
        private long battlesStarted;
        private long battleEnds;
        private long defeats;
        private long adminSpawns;

        public void initialize() {
            reloadConfig();
            loadStateFile();
        }

        public void reloadConfig() {
            Path file = configFile();
            try {
                Files.createDirectories(file.getParent());
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

        private void loadStateFile() {
            Path file = stateFile();
            try {
                if (Files.notExists(file)) return;
                SavedState loaded = GSON.fromJson(Files.readString(file), SavedState.class);
                if (loaded != null) pendingSavedState = loaded.normalized();
            } catch (Exception e) {
                LOGGER.error("Failed to load Alpha-Encounter persistent state. Active encounters will start fresh.", e);
            }
        }

        public void tick(MinecraftServer server) {
            tick++;
            if (!restoreApplied) restoreState(server);
            processBattleTransitions(server);
            processPendingBattles(server);
            processCatchWindows(server);

            int huntCadence = Math.max(1, config.general.huntUpdateIntervalTicks);
            if (tick % huntCadence == 0) tickHunts(server);

            int bossBarCadence = Math.max(1, config.general.bossBarUpdateIntervalTicks);
            if (tick % bossBarCadence == 0) updateBossBars(server);

            if (tick % 100 == 0) cleanupMissingEntries(server);

            int spawnCadence = Math.max(20, config.general.spawnCheckIntervalTicks);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                int offset = Math.floorMod(player.getUuid().hashCode(), spawnCadence);
                if ((tick + offset) % spawnCadence == 0) trySpawnNear(player);
            }

            int saveCadence = Math.max(200, config.general.stateSaveIntervalTicks);
            if (stateDirty && tick % saveCadence == 0) saveState(server);
        }

        private void trySpawnNear(ServerPlayerEntity player) {
            if (!(player.getWorld() instanceof ServerWorld world) || player.isSpectator()) return;
            spawnChecks++;
            String dimension = world.getRegistryKey().getValue().toString();
            List<EncounterDefinition> eligible = new ArrayList<>();
            double totalWeight = 0.0;
            for (EncounterDefinition def : config.encounters) {
                if (!eligibleForNaturalSpawn(def, dimension)) continue;
                eligible.add(def);
                totalWeight += def.spawn.weight;
            }
            if (eligible.isEmpty() || totalWeight <= 0.0) return;
            if (ThreadLocalRandom.current().nextDouble() > config.general.spawnAttemptChance) return;

            double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
            EncounterDefinition picked = null;
            for (EncounterDefinition def : eligible) {
                roll -= def.spawn.weight;
                if (roll <= 0.0) {
                    picked = def;
                    break;
                }
            }
            if (picked == null) return;

            BlockPos origin = player.getBlockPos();
            int min = Math.max(8, picked.spawn.minDistance);
            int max = Math.max(min, picked.spawn.maxDistance);
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
            int radius = min + ThreadLocalRandom.current().nextInt(max - min + 1);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos spawnPos = new BlockPos(x, y, z);
            if (!biomeAllowed(world, spawnPos, picked.spawn.biomes)) return;

            ActiveEncounter spawned = spawnEncounter(picked.id, world, spawnPos, false);
            if (spawned != null) spawnSuccess++;
        }

        private boolean eligibleForNaturalSpawn(EncounterDefinition def, String dimension) {
            if (def == null || !def.enabled || def.spawn == null || def.spawn.weight <= 0.0) return false;
            if (!def.spawn.dimensions.isEmpty() && !def.spawn.dimensions.contains(dimension)) return false;
            if (activeByDefinition.getOrDefault(def.id, 0) >= Math.max(1, def.spawn.maxActive)) return false;
            return globalCooldownUntil.getOrDefault(def.id, 0L) <= tick;
        }

        private boolean biomeAllowed(ServerWorld world, BlockPos pos, List<String> allowed) {
            if (allowed == null || allowed.isEmpty()) return true;
            return world.getBiome(pos).getKey().map(key -> allowed.contains(key.getValue().toString())).orElse(false);
        }

        public ActiveEncounter spawnEncounter(String definitionId, ServerWorld world, BlockPos pos, boolean admin) {
            EncounterDefinition def = definition(definitionId);
            if (def == null || !def.enabled) return null;
            try {
                PokemonEntity entity = PokemonProperties.Companion.parse(def.pokemon).createEntity(world);
                entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, ThreadLocalRandom.current().nextFloat() * 360f, 0f);
                if (!world.spawnEntity(entity)) return null;

                Tier tier = tier(def.tier);
                ActiveEncounter active = new ActiveEncounter(def.id, def.tier, entity.getUuid(), tier.healthMultiplier);
                active.entityRef = entity;
                active.dimension = world.getRegistryKey().getValue().toString();
                active.x = entity.getX();
                active.y = entity.getY();
                active.z = entity.getZ();
                initializeHealth(active, entity);
                active.state = tier.aggressive ? EncounterState.HUNT : EncounterState.IDLE;
                activeByEntity.put(entity.getUuid(), active);
                activeByDefinition.merge(def.id, 1, Integer::sum);
                if (!admin) globalCooldownUntil.put(def.id, tick + Math.max(0, def.spawn.globalCooldownTicks));
                if (admin) adminSpawns++;
                setupBossBar(active, def, tier);
                broadcast(world.getServer(), format(def.spawnMessage, active, null));
                stateDirty = true;
                LOGGER.info("Spawned encounter '{}' ({}) at {} {} {} in {}{}.", def.id, def.pokemon, pos.getX(), pos.getY(), pos.getZ(), active.dimension, admin ? " [admin]" : "");
                return active;
            } catch (Throwable t) {
                LOGGER.error("Failed to spawn encounter '{}'.", definitionId, t);
                return null;
            }
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
            if (active == null || active.state == EncounterState.DEFEATED) return;
            initializeHealth(active, entity);

            // Field damage may challenge/chip a boss but can never finish it. The battle layer owns defeat.
            active.hp = Math.max(1f, active.hp - resolvedDamage);
            interceptedHits++;
            active.x = entity.getX();
            active.y = entity.getY();
            active.z = entity.getZ();
            CobblemonBridge.playAnimation(entity, animation(active, "hit"));

            Entity attacker = source.getAttacker();
            if (attacker instanceof ServerPlayerEntity player) {
                active.participants.add(player.getUuid());
                active.targetPlayer = player.getUuid();
                queueBattle(active, player);
            }
            stateDirty = true;
        }

        private void queueBattle(ActiveEncounter active, ServerPlayerEntity player) {
            if (active == null || player == null || active.state == EncounterState.DEFEATED || active.state == EncounterState.BATTLE || active.state == EncounterState.BATTLE_PENDING) return;
            active.pendingBattlePlayer = player.getUuid();
            active.targetPlayer = player.getUuid();
            active.participants.add(player.getUuid());
            active.battleAtTick = tick + 1;
            active.pendingSinceTick = tick;
            active.state = EncounterState.BATTLE_PENDING;
            battlesQueued++;
            stateDirty = true;
        }

        private void processPendingBattles(MinecraftServer server) {
            for (ActiveEncounter active : activeByEntity.values()) {
                if (active.state != EncounterState.BATTLE_PENDING) continue;
                PokemonEntity pokemon = resolveEntity(server, active);
                if (pokemon == null) continue;
                if (CobblemonBridge.isInBattle(pokemon)) {
                    enterBattle(active, pokemon);
                    continue;
                }
                if (active.pendingBattlePlayer == null) {
                    active.state = EncounterState.HUNT;
                    continue;
                }
                if (active.battleAtTick > tick) continue;

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(active.pendingBattlePlayer);
                if (player == null || player.isSpectator() || player.getWorld() != pokemon.getWorld()) {
                    active.pendingBattlePlayer = null;
                    active.state = EncounterState.HUNT;
                    continue;
                }

                preparePokemonHealthForBattle(active, pokemon);
                CobblemonBridge.playAnimation(pokemon, animation(active, "battleStart"));
                boolean invoked = CobblemonBridge.startPve(player, pokemon);
                active.pendingBattlePlayer = null;
                if (invoked) {
                    active.lastBattlePlayer = player.getUuid();
                    active.participants.add(player.getUuid());
                }

                if (!invoked || tick - active.pendingSinceTick > 40) {
                    active.state = EncounterState.HUNT;
                    active.nextReengageTick = tick + Math.max(10, tier(active.tierId).reengageCooldownTicks);
                }
            }
        }

        private void processBattleTransitions(MinecraftServer server) {
            for (ActiveEncounter active : new ArrayList<>(activeByEntity.values())) {
                PokemonEntity pokemon = resolveEntity(server, active);
                if (pokemon == null) continue;
                boolean battling = CobblemonBridge.isInBattle(pokemon);
                if (battling) {
                    if (active.state != EncounterState.BATTLE) enterBattle(active, pokemon);
                    syncBattleHealth(active, pokemon);
                } else if (active.state == EncounterState.BATTLE) {
                    syncBattleHealth(active, pokemon);
                    battleEnds++;
                    CobblemonBridge.playAnimation(pokemon, animation(active, "battleEnd"));
                    if (active.hp <= 0.5f) {
                        defeatEncounter(server, active, false);
                    } else {
                        if (CobblemonBridge.currentHealth(pokemon) <= 0) {
                            restorePokemonForNextPhase(active, pokemon);
                        }
                        active.state = EncounterState.HUNT;
                        active.targetPlayer = active.lastBattlePlayer;
                        active.nextReengageTick = tick + Math.max(10, tier(active.tierId).reengageCooldownTicks);
                        stateDirty = true;
                    }
                }
            }
        }

        private void enterBattle(ActiveEncounter active, PokemonEntity pokemon) {
            active.state = EncounterState.BATTLE;
            active.pendingBattlePlayer = null;
            active.lastPokemonHealth = CobblemonBridge.currentHealth(pokemon);
            battlesStarted++;
            stateDirty = true;
        }

        private void preparePokemonHealthForBattle(ActiveEncounter active, PokemonEntity pokemon) {
            initializeHealth(active, pokemon);
            int max = Math.max(1, CobblemonBridge.maxHealth(pokemon));
            int desired = Math.max(1, Math.min(max, (int) Math.ceil(active.hp)));
            CobblemonBridge.setCurrentHealth(pokemon, desired);
            active.lastPokemonHealth = desired;
        }

        private void syncBattleHealth(ActiveEncounter active, PokemonEntity pokemon) {
            initializeHealth(active, pokemon);
            int current = Math.max(0, CobblemonBridge.currentHealth(pokemon));
            if (active.lastPokemonHealth < 0) {
                active.lastPokemonHealth = current;
                return;
            }

            int delta = active.lastPokemonHealth - current;
            if (delta > 0) {
                active.hp = Math.max(0f, active.hp - delta);
                stateDirty = true;
            } else if (delta < 0) {
                active.hp = Math.min(active.maxHp, active.hp + (-delta));
                stateDirty = true;
            }
            active.lastPokemonHealth = current;
        }

        private void restorePokemonForNextPhase(ActiveEncounter active, PokemonEntity pokemon) {
            int max = Math.max(1, CobblemonBridge.maxHealth(pokemon));
            int desired = Math.max(1, Math.min(max, (int) Math.ceil(active.hp)));
            CobblemonBridge.setCurrentHealth(pokemon, desired);
            active.lastPokemonHealth = desired;
        }

        private void tickHunts(MinecraftServer server) {
            for (ActiveEncounter active : activeByEntity.values()) {
                if (active.state == EncounterState.DEFEATED || active.state == EncounterState.BATTLE || active.state == EncounterState.BATTLE_PENDING) continue;
                PokemonEntity pokemon = resolveEntity(server, active);
                if (pokemon == null) continue;
                Tier tier = tier(active.tierId);
                if (!tier.aggressive && active.state == EncounterState.IDLE) continue;

                ServerPlayerEntity target = validTarget(server, pokemon, active.targetPlayer, tier.leashRadius);
                if (target == null) target = nearestPlayer(server, pokemon, tier.aggroRadius);
                if (target == null) {
                    active.targetPlayer = null;
                    CobblemonBridge.clearHuntTarget(pokemon);
                    if (!tier.aggressive) active.state = EncounterState.IDLE;
                    continue;
                }

                boolean newTarget = !target.getUuid().equals(active.targetPlayer);
                active.targetPlayer = target.getUuid();
                active.state = EncounterState.HUNT;
                if (newTarget) CobblemonBridge.playAnimation(pokemon, animation(active, "aggro"));
                CobblemonBridge.hunt(pokemon, target, tier.chaseSpeed);

                double trigger = Math.max(1.5, tier.battleTriggerDistance);
                if (tick >= active.nextReengageTick && pokemon.squaredDistanceTo(target) <= trigger * trigger) {
                    queueBattle(active, target);
                }
            }
        }

        private ServerPlayerEntity validTarget(MinecraftServer server, PokemonEntity pokemon, UUID id, double leashRadius) {
            if (id == null) return null;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player == null || player.isSpectator() || player.getWorld() != pokemon.getWorld()) return null;
            double leash = Math.max(8.0, leashRadius);
            return pokemon.squaredDistanceTo(player) <= leash * leash ? player : null;
        }

        private ServerPlayerEntity nearestPlayer(MinecraftServer server, PokemonEntity pokemon, double radius) {
            double maxSq = Math.max(8.0, radius) * Math.max(8.0, radius);
            ServerPlayerEntity best = null;
            double bestSq = maxSq;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isSpectator() || player.getWorld() != pokemon.getWorld()) continue;
                double distance = pokemon.squaredDistanceTo(player);
                if (distance < bestSq) {
                    bestSq = distance;
                    best = player;
                }
            }
            return best;
        }

        private void updateBossBars(MinecraftServer server) {
            for (ActiveEncounter active : activeByEntity.values()) {
                if (active.bossBar == null) continue;
                PokemonEntity pokemon = resolveEntity(server, active);
                if (pokemon == null) continue;
                EncounterDefinition def = definition(active.definitionId);
                Tier tier = tier(active.tierId);
                float progress = active.maxHp <= 0f ? 1f : Math.max(0f, Math.min(1f, active.hp / active.maxHp));
                active.bossBar.setPercent(progress);
                active.bossBar.setName(Text.literal((def == null ? active.definitionId : def.displayName) + "  " + Math.round(active.hp) + "/" + Math.round(active.maxHp)));

                Set<UUID> desired = new HashSet<>(active.participants);
                double rangeSq = Math.max(16.0, tier.bossBarRange) * Math.max(16.0, tier.bossBarRange);
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    if (player.getWorld() == pokemon.getWorld() && pokemon.squaredDistanceTo(player) <= rangeSq) desired.add(player.getUuid());
                }
                for (UUID id : desired) {
                    if (active.bossBarViewers.add(id)) {
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                        if (player != null) active.bossBar.addPlayer(player);
                    }
                }
                Iterator<UUID> viewers = active.bossBarViewers.iterator();
                while (viewers.hasNext()) {
                    UUID id = viewers.next();
                    if (desired.contains(id)) continue;
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                    if (player != null) active.bossBar.removePlayer(player);
                    viewers.remove();
                }
            }
        }

        private void setupBossBar(ActiveEncounter active, EncounterDefinition def, Tier tier) {
            if (!tier.bossBar) return;
            BossBar.Color color;
            try {
                color = BossBar.Color.valueOf(tier.bossBarColor.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                color = BossBar.Color.PURPLE;
            }
            active.bossBar = new ServerBossBar(Text.literal(def.displayName), color, BossBar.Style.PROGRESS);
        }

        private void defeatEncounter(MinecraftServer server, ActiveEncounter active, boolean adminForced) {
            if (active == null || active.state == EncounterState.DEFEATED) return;
            active.state = EncounterState.DEFEATED;
            defeats++;
            EncounterDefinition def = definition(active.definitionId);
            PokemonEntity pokemon = resolveEntity(server, active);
            if (pokemon != null) CobblemonBridge.playAnimation(pokemon, animation(active, "defeat"));

            if (def != null && (!adminForced || def.rewardOnAdminDefeat)) {
                runRewards(server, active, def);
                broadcast(server, format(def.defeatMessage, active, null));
            }

            boolean catchPhase = def != null && def.catchable && def.catchPhaseSeconds > 0;
            if (catchPhase) {
                PokemonEntity catchPokemon = pokemon;
                if (catchPokemon == null || catchPokemon.isRemoved()) {
                    ServerWorld world = resolveWorld(server, active.dimension);
                    if (world != null) {
                        catchPokemon = spawnCatchClone(def, world, BlockPos.ofFloored(active.x, active.y, active.z));
                    }
                }
                if (catchPokemon != null && !catchPokemon.isRemoved()) {
                    int max = Math.max(1, CobblemonBridge.maxHealth(catchPokemon));
                    int hp = Math.max(1, Math.round(max * Math.max(0.01f, Math.min(1f, def.catchHealthPercent))));
                    CobblemonBridge.setCurrentHealth(catchPokemon, hp);
                    catchPokemon.setGlowing(true);
                    catchWindows.put(catchPokemon.getUuid(), new CatchWindow(catchPokemon.getUuid(), tick + def.catchPhaseSeconds * 20L));
                    broadcast(server, format(def.catchMessage, active, null));
                }
            } else if (pokemon != null && !pokemon.isRemoved()) {
                pokemon.discard();
            }

            removeActive(active, false);
            stateDirty = true;
        }

        private PokemonEntity spawnCatchClone(EncounterDefinition def, ServerWorld world, BlockPos pos) {
            try {
                PokemonEntity entity = PokemonProperties.Companion.parse(def.pokemon).createEntity(world);
                entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
                return world.spawnEntity(entity) ? entity : null;
            } catch (Throwable t) {
                LOGGER.error("Failed to spawn catch-phase clone for '{}'.", def.id, t);
                return null;
            }
        }

        private void processCatchWindows(MinecraftServer server) {
            if (catchWindows.isEmpty()) return;
            Iterator<Map.Entry<UUID, CatchWindow>> it = catchWindows.entrySet().iterator();
            while (it.hasNext()) {
                CatchWindow window = it.next().getValue();
                Entity entity = findEntity(server, window.entityId);
                if (entity == null || entity.isRemoved()) {
                    it.remove();
                    continue;
                }
                if (tick < window.expiresAtTick) continue;
                entity.discard();
                it.remove();
            }
        }

        private void runRewards(MinecraftServer server, ActiveEncounter active, EncounterDefinition def) {
            if (def.rewardCommands == null || def.rewardCommands.isEmpty()) return;
            for (UUID id : active.participants) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null) continue;
                for (String raw : def.rewardCommands) {
                    if (raw == null || raw.isBlank()) continue;
                    String command = raw
                        .replace("{player}", player.getName().getString())
                        .replace("{encounter}", active.definitionId)
                        .replace("{tier}", active.tierId);
                    try {
                        server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
                    } catch (Throwable t) {
                        LOGGER.error("Reward command failed for encounter '{}': {}", active.definitionId, command, t);
                    }
                }
            }
        }

        private void cleanupMissingEntries(MinecraftServer server) {
            for (ActiveEncounter active : new ArrayList<>(activeByEntity.values())) {
                PokemonEntity entity = resolveEntity(server, active);
                if (entity != null && !entity.isRemoved()) {
                    active.missingSinceTick = 0;
                    continue;
                }
                if (active.missingSinceTick == 0) active.missingSinceTick = tick;
                if (tick - active.missingSinceTick < 200) continue;
                LOGGER.warn("Managed encounter '{}' disappeared; removing runtime entry.", active.definitionId);
                removeActive(active, false);
                stateDirty = true;
            }
        }

        private void initializeHealth(ActiveEncounter active, PokemonEntity entity) {
            if (active.maxHp > 0f && active.hp >= 0f) return;
            float base = Math.max(1f, CobblemonBridge.maxHealth(entity));
            active.maxHp = base * Math.max(1f, active.healthMultiplier);
            active.hp = active.maxHp;
        }

        private PokemonEntity resolveEntity(MinecraftServer server, ActiveEncounter active) {
            if (active.entityRef != null && !active.entityRef.isRemoved()) {
                updateLocation(active, active.entityRef);
                return active.entityRef;
            }
            Entity raw = findEntity(server, active.entityId);
            if (raw instanceof PokemonEntity pokemon) {
                active.entityRef = pokemon;
                updateLocation(active, pokemon);
                return pokemon;
            }
            return null;
        }

        private void updateLocation(ActiveEncounter active, PokemonEntity entity) {
            active.dimension = entity.getWorld().getRegistryKey().getValue().toString();
            active.x = entity.getX();
            active.y = entity.getY();
            active.z = entity.getZ();
        }

        private Entity findEntity(MinecraftServer server, UUID id) {
            if (id == null) return null;
            for (ServerWorld world : server.getWorlds()) {
                Entity entity = world.getEntity(id);
                if (entity != null) return entity;
            }
            return null;
        }

        private ServerWorld resolveWorld(MinecraftServer server, String dimension) {
            if (dimension == null || dimension.isBlank()) return server.getOverworld();
            try {
                RegistryKey<net.minecraft.world.World> key = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dimension));
                return server.getWorld(key);
            } catch (Exception ignored) {
                return null;
            }
        }

        private void removeActive(ActiveEncounter active, boolean discardEntity) {
            activeByEntity.remove(active.entityId);
            activeByDefinition.computeIfPresent(active.definitionId, (k, v) -> v <= 1 ? null : v - 1);
            if (active.bossBar != null) active.bossBar.clearPlayers();
            if (discardEntity && active.entityRef != null && !active.entityRef.isRemoved()) active.entityRef.discard();
        }

        public void saveState(MinecraftServer server) {
            try {
                Files.createDirectories(stateFile().getParent());
                SavedState saved = new SavedState();
                for (Map.Entry<String, Long> entry : globalCooldownUntil.entrySet()) {
                    long remaining = Math.max(0L, entry.getValue() - tick);
                    if (remaining > 0) saved.cooldownRemaining.put(entry.getKey(), remaining);
                }
                for (ActiveEncounter active : activeByEntity.values()) {
                    PokemonEntity entity = resolveEntity(server, active);
                    if (entity != null) updateLocation(active, entity);
                    saved.active.add(SavedEncounter.from(active));
                }
                Files.writeString(stateFile(), GSON.toJson(saved));
                stateDirty = false;
            } catch (Exception e) {
                LOGGER.error("Failed to save Alpha-Encounter persistent state.", e);
            }
        }

        private void restoreState(MinecraftServer server) {
            restoreApplied = true;
            if (pendingSavedState == null) return;
            for (Map.Entry<String, Long> entry : pendingSavedState.cooldownRemaining.entrySet()) {
                globalCooldownUntil.put(entry.getKey(), tick + Math.max(0L, entry.getValue()));
            }
            for (SavedEncounter saved : pendingSavedState.active) {
                EncounterDefinition def = definition(saved.definitionId);
                if (def == null || !def.enabled) continue;
                ServerWorld world = resolveWorld(server, saved.dimension);
                if (world == null) continue;
                BlockPos pos = BlockPos.ofFloored(saved.x, saved.y, saved.z);
                if (config.general.loadEncounterChunksOnRestore) world.getChunk(pos);

                PokemonEntity entity = null;
                try {
                    UUID old = UUID.fromString(saved.entityId);
                    Entity found = world.getEntity(old);
                    if (found instanceof PokemonEntity p) entity = p;
                } catch (Exception ignored) {
                }
                if (entity == null) {
                    try {
                        entity = PokemonProperties.Companion.parse(def.pokemon).createEntity(world);
                        entity.refreshPositionAndAngles(saved.x, saved.y, saved.z, 0f, 0f);
                        if (!world.spawnEntity(entity)) entity = null;
                    } catch (Throwable t) {
                        LOGGER.error("Failed restoring encounter '{}'.", saved.definitionId, t);
                    }
                }
                if (entity == null) continue;

                Tier tier = tier(saved.tierId == null ? def.tier : saved.tierId);
                ActiveEncounter active = new ActiveEncounter(def.id, saved.tierId == null ? def.tier : saved.tierId, entity.getUuid(), tier.healthMultiplier);
                active.entityRef = entity;
                active.maxHp = saved.maxHp > 0 ? saved.maxHp : Math.max(1f, CobblemonBridge.maxHealth(entity) * tier.healthMultiplier);
                active.hp = Math.max(1f, Math.min(active.maxHp, saved.hp <= 0 ? active.maxHp : saved.hp));
                active.state = saved.state == EncounterState.IDLE && !tier.aggressive ? EncounterState.IDLE : EncounterState.HUNT;
                active.targetPlayer = parseUuid(saved.targetPlayer);
                active.lastBattlePlayer = parseUuid(saved.lastBattlePlayer);
                for (String value : saved.participants) {
                    UUID id = parseUuid(value);
                    if (id != null) active.participants.add(id);
                }
                active.nextReengageTick = tick + Math.max(10, tier.reengageCooldownTicks);
                updateLocation(active, entity);
                activeByEntity.put(entity.getUuid(), active);
                activeByDefinition.merge(def.id, 1, Integer::sum);
                setupBossBar(active, def, tier);
            }
            LOGGER.info("Restored {} active Alpha-Encounter entries.", activeByEntity.size());
            pendingSavedState = null;
        }

        private UUID parseUuid(String value) {
            if (value == null || value.isBlank()) return null;
            try {
                return UUID.fromString(value);
            } catch (Exception ignored) {
                return null;
            }
        }

        public EncounterDefinition definition(String id) {
            if (id == null) return null;
            for (EncounterDefinition def : config.encounters) if (id.equals(def.id)) return def;
            return null;
        }

        public List<String> definitionIds() {
            return config.encounters.stream().map(def -> def.id).sorted().toList();
        }

        private Tier tier(String id) {
            return config.tiers.getOrDefault(id, Tier.defaults());
        }

        private String animation(ActiveEncounter active, String phase) {
            EncounterDefinition def = definition(active.definitionId);
            if (def == null || def.animations == null) return "";
            return switch (phase) {
                case "aggro" -> def.animations.aggro;
                case "hit" -> def.animations.hit;
                case "battleStart" -> def.animations.battleStart;
                case "battleEnd" -> def.animations.battleEnd;
                case "defeat" -> def.animations.defeat;
                default -> "";
            };
        }

        private String format(String raw, ActiveEncounter active, ServerPlayerEntity player) {
            if (raw == null || raw.isBlank()) return "";
            EncounterDefinition def = definition(active.definitionId);
            return raw
                .replace("{encounter}", active.definitionId)
                .replace("{name}", def == null ? active.definitionId : def.displayName)
                .replace("{tier}", active.tierId)
                .replace("{player}", player == null ? "" : player.getName().getString());
        }

        private void broadcast(MinecraftServer server, String message) {
            if (server == null || message == null || message.isBlank()) return;
            server.getPlayerManager().broadcast(Text.literal(message), false);
        }

        private Path configFile() {
            return FabricLoader.getInstance().getConfigDir().resolve("alpha-encounter").resolve("config.json");
        }

        private Path stateFile() {
            return FabricLoader.getInstance().getConfigDir().resolve("alpha-encounter").resolve("state.json");
        }

        public String perfLine() {
            return "AlphaEncounter active=" + activeByEntity.size()
                + " catchWindows=" + catchWindows.size()
                + " spawnChecks=" + spawnChecks
                + " spawned=" + spawnSuccess
                + " adminSpawned=" + adminSpawns
                + " worldHits=" + interceptedHits
                + " battlesQueued=" + battlesQueued
                + " battlesStarted=" + battlesStarted
                + " battleEnds=" + battleEnds
                + " defeats=" + defeats;
        }

        public void resetCounters() {
            spawnChecks = 0;
            spawnSuccess = 0;
            interceptedHits = 0;
            battlesQueued = 0;
            battlesStarted = 0;
            battleEnds = 0;
            defeats = 0;
            adminSpawns = 0;
        }

        public List<ActiveEncounter> activeSorted() {
            return activeByEntity.values().stream().sorted(Comparator.comparing(a -> a.definitionId)).toList();
        }

        public ActiveEncounter resolveAdminTarget(ServerCommandSource source, String token) {
            if (activeByEntity.isEmpty()) return null;
            if (token == null || token.isBlank() || token.equalsIgnoreCase("nearest")) return nearestActive(source, null);
            try {
                ActiveEncounter byUuid = activeByEntity.get(UUID.fromString(token));
                if (byUuid != null) return byUuid;
            } catch (Exception ignored) {
            }
            return nearestActive(source, token);
        }

        private ActiveEncounter nearestActive(ServerCommandSource source, String definitionFilter) {
            ServerWorld world = source.getWorld();
            double sx = source.getPosition().x;
            double sy = source.getPosition().y;
            double sz = source.getPosition().z;
            ActiveEncounter best = null;
            double bestSq = Double.MAX_VALUE;
            for (ActiveEncounter active : activeByEntity.values()) {
                if (definitionFilter != null && !definitionFilter.equals(active.definitionId)) continue;
                PokemonEntity entity = active.entityRef;
                if (entity == null || entity.isRemoved() || entity.getWorld() != world) continue;
                double dx = entity.getX() - sx;
                double dy = entity.getY() - sy;
                double dz = entity.getZ() - sz;
                double dist = dx * dx + dy * dy + dz * dz;
                if (dist < bestSq) {
                    bestSq = dist;
                    best = active;
                }
            }
            return best;
        }

        public boolean adminDespawn(ActiveEncounter active) {
            if (active == null) return false;
            removeActive(active, true);
            stateDirty = true;
            return true;
        }

        public boolean adminDefeat(MinecraftServer server, ActiveEncounter active) {
            if (active == null) return false;
            active.hp = 0f;
            defeatEncounter(server, active, true);
            return true;
        }

        public boolean adminSetHp(MinecraftServer server, ActiveEncounter active, float percent) {
            if (active == null) return false;
            PokemonEntity pokemon = resolveEntity(server, active);
            if (pokemon == null) return false;
            initializeHealth(active, pokemon);
            float clamped = Math.max(0.01f, Math.min(1f, percent / 100f));
            active.hp = active.maxHp * clamped;
            if (CobblemonBridge.isInBattle(pokemon)) preparePokemonHealthForBattle(active, pokemon);
            stateDirty = true;
            return true;
        }

        public boolean adminBattle(MinecraftServer server, ActiveEncounter active, ServerPlayerEntity player) {
            if (active == null || player == null) return false;
            PokemonEntity pokemon = resolveEntity(server, active);
            if (pokemon == null || pokemon.getWorld() != player.getWorld()) return false;
            active.state = EncounterState.HUNT;
            queueBattle(active, player);
            return true;
        }

        public boolean adminAnimation(MinecraftServer server, ActiveEncounter active, String animation) {
            if (active == null || animation == null || animation.isBlank()) return false;
            PokemonEntity pokemon = resolveEntity(server, active);
            return pokemon != null && CobblemonBridge.playAnimation(pokemon, animation);
        }

        public void resetCooldown(String id) {
            if (id == null || id.equalsIgnoreCase("all")) globalCooldownUntil.clear();
            else globalCooldownUntil.remove(id);
            stateDirty = true;
        }
    }

    public static final class AdminCommands {
        private AdminCommands() {
        }

        public static void register(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher) {
            dispatcher.register(CommandManager.literal("alphaencounter")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("help").executes(ctx -> help(ctx.getSource())))
                .then(CommandManager.literal("reload").executes(ctx -> {
                    RUNTIME.reloadConfig();
                    feedback(ctx.getSource(), "Alpha-Encounter config reloaded.");
                    return 1;
                }))
                .then(CommandManager.literal("save").executes(ctx -> {
                    RUNTIME.saveState(ctx.getSource().getServer());
                    feedback(ctx.getSource(), "Alpha-Encounter state saved.");
                    return 1;
                }))
                .then(CommandManager.literal("debug")
                    .executes(ctx -> {
                        feedback(ctx.getSource(), RUNTIME.perfLine());
                        return 1;
                    })
                    .then(CommandManager.literal("reset").executes(ctx -> {
                        RUNTIME.resetCounters();
                        feedback(ctx.getSource(), "Alpha-Encounter counters reset.");
                        return 1;
                    })))
                .then(CommandManager.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(CommandManager.literal("spawn")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            RUNTIME.definitionIds().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> spawnAtSource(ctx.getSource(), StringArgumentType.getString(ctx, "id")))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(ctx -> spawnAtPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "id"), EntityArgumentType.getPlayer(ctx, "player"))))))
                .then(CommandManager.literal("inspect")
                    .then(CommandManager.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("nearest");
                            RUNTIME.definitionIds().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> inspect(ctx.getSource(), StringArgumentType.getString(ctx, "target")))))
                .then(CommandManager.literal("despawn")
                    .then(CommandManager.argument("target", StringArgumentType.word())
                        .executes(ctx -> despawn(ctx.getSource(), StringArgumentType.getString(ctx, "target")))))
                .then(CommandManager.literal("defeat")
                    .then(CommandManager.argument("target", StringArgumentType.word())
                        .executes(ctx -> defeat(ctx.getSource(), StringArgumentType.getString(ctx, "target")))))
                .then(CommandManager.literal("sethp")
                    .then(CommandManager.argument("target", StringArgumentType.word())
                        .then(CommandManager.argument("percent", FloatArgumentType.floatArg(1f, 100f))
                            .executes(ctx -> setHp(ctx.getSource(), StringArgumentType.getString(ctx, "target"), FloatArgumentType.getFloat(ctx, "percent"))))))
                .then(CommandManager.literal("battle")
                    .then(CommandManager.argument("target", StringArgumentType.word())
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(ctx -> battle(ctx.getSource(), StringArgumentType.getString(ctx, "target"), EntityArgumentType.getPlayer(ctx, "player"))))))
                .then(CommandManager.literal("anim")
                    .then(CommandManager.argument("target", StringArgumentType.word())
                        .then(CommandManager.argument("animation", StringArgumentType.word())
                            .executes(ctx -> animation(ctx.getSource(), StringArgumentType.getString(ctx, "target"), StringArgumentType.getString(ctx, "animation"))))))
                .then(CommandManager.literal("reset")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("all");
                            RUNTIME.definitionIds().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            RUNTIME.resetCooldown(id);
                            feedback(ctx.getSource(), "Reset spawn cooldown: " + id);
                            return 1;
                        }))));
        }

        private static int help(ServerCommandSource source) {
            feedback(source, "/alphaencounter reload | save | debug [reset] | list");
            feedback(source, "/alphaencounter spawn <id> [player] | inspect <nearest|uuid|id>");
            feedback(source, "/alphaencounter despawn <target> | defeat <target> | sethp <target> <1-100>");
            feedback(source, "/alphaencounter battle <target> <player> | anim <target> <animation> | reset <id|all>");
            return 1;
        }

        private static int list(ServerCommandSource source) {
            List<ActiveEncounter> active = RUNTIME.activeSorted();
            feedback(source, "Active Alpha-Encounters: " + active.size());
            for (ActiveEncounter entry : active) {
                feedback(source, shortLine(entry));
            }
            return active.size();
        }

        private static int spawnAtSource(ServerCommandSource source, String id) {
            ServerWorld world = source.getWorld();
            BlockPos pos = BlockPos.ofFloored(source.getPosition());
            ActiveEncounter active = RUNTIME.spawnEncounter(id, world, pos, true);
            if (active == null) {
                feedback(source, "Failed to spawn encounter '" + id + "'. Check definition ID/config.");
                return 0;
            }
            feedback(source, "Spawned " + shortLine(active));
            return 1;
        }

        private static int spawnAtPlayer(ServerCommandSource source, String id, ServerPlayerEntity player) {
            ActiveEncounter active = RUNTIME.spawnEncounter(id, player.getServerWorld(), player.getBlockPos(), true);
            if (active == null) {
                feedback(source, "Failed to spawn encounter '" + id + "'.");
                return 0;
            }
            feedback(source, "Spawned at " + player.getName().getString() + ": " + shortLine(active));
            return 1;
        }

        private static int inspect(ServerCommandSource source, String token) {
            ActiveEncounter active = RUNTIME.resolveAdminTarget(source, token);
            if (active == null) {
                feedback(source, "No matching active encounter for '" + token + "'.");
                return 0;
            }
            feedback(source, shortLine(active));
            feedback(source, "entity=" + active.entityId + " dim=" + active.dimension + " pos=" + String.format(Locale.ROOT, "%.1f %.1f %.1f", active.x, active.y, active.z));
            feedback(source, "target=" + active.targetPlayer + " lastBattle=" + active.lastBattlePlayer + " participants=" + active.participants.size() + " nextReengage=" + Math.max(0, active.nextReengageTick - RUNTIME.tick));
            return 1;
        }

        private static int despawn(ServerCommandSource source, String token) {
            ActiveEncounter active = RUNTIME.resolveAdminTarget(source, token);
            if (!RUNTIME.adminDespawn(active)) {
                feedback(source, "No matching active encounter.");
                return 0;
            }
            feedback(source, "Despawned encounter.");
            return 1;
        }

        private static int defeat(ServerCommandSource source, String token) {
            ActiveEncounter active = RUNTIME.resolveAdminTarget(source, token);
            if (!RUNTIME.adminDefeat(source.getServer(), active)) {
                feedback(source, "No matching active encounter.");
                return 0;
            }
            feedback(source, "Forced encounter defeat.");
            return 1;
        }

        private static int setHp(ServerCommandSource source, String token, float percent) {
            ActiveEncounter active = RUNTIME.resolveAdminTarget(source, token);
            if (!RUNTIME.adminSetHp(source.getServer(), active, percent)) {
                feedback(source, "No matching active encounter.");
                return 0;
            }
            feedback(source, "Set encounter HP to " + percent + "%.");
            return 1;
        }

        private static int battle(ServerCommandSource source, String token, ServerPlayerEntity player) {
            ActiveEncounter active = RUNTIME.resolveAdminTarget(source, token);
            if (!RUNTIME.adminBattle(source.getServer(), active, player)) {
                feedback(source, "Could not queue battle (missing encounter/player or wrong dimension).");
                return 0;
            }
            feedback(source, "Queued encounter battle against " + player.getName().getString() + ".");
            return 1;
        }

        private static int animation(ServerCommandSource source, String token, String animation) {
            ActiveEncounter active = RUNTIME.resolveAdminTarget(source, token);
            if (!RUNTIME.adminAnimation(source.getServer(), active, animation)) {
                feedback(source, "Could not play animation.");
                return 0;
            }
            feedback(source, "Played animation '" + animation + "'.");
            return 1;
        }

        private static String shortLine(ActiveEncounter active) {
            return active.definitionId + " [" + active.state + "] HP=" + Math.round(active.hp) + "/" + Math.round(active.maxHp) + " uuid=" + active.entityId;
        }

        private static void feedback(ServerCommandSource source, String message) {
            source.sendFeedback(() -> Text.literal(message), false);
        }
    }

    public static final class CobblemonBridge {
        private CobblemonBridge() {
        }

        public static boolean isInBattle(PokemonEntity entity) {
            try {
                return entity.isBattling();
            } catch (Throwable ignored) {
                try {
                    Method method = entity.getClass().getMethod("getBattleId");
                    return method.invoke(entity) != null;
                } catch (ReflectiveOperationException ignoredToo) {
                    return false;
                }
            }
        }

        public static int currentHealth(PokemonEntity entity) {
            try {
                return entity.getPokemon().getCurrentHealth();
            } catch (Throwable t) {
                return Math.max(0, Math.round(entity.getHealth()));
            }
        }

        public static int maxHealth(PokemonEntity entity) {
            try {
                return entity.getPokemon().getMaxHealth();
            } catch (Throwable t) {
                return Math.max(1, Math.round(entity.getMaxHealth()));
            }
        }

        public static void setCurrentHealth(PokemonEntity entity, int value) {
            try {
                entity.getPokemon().setCurrentHealth(Math.max(0, value));
            } catch (Throwable t) {
                entity.setHealth(Math.max(1f, value));
            }
        }

        public static boolean playAnimation(PokemonEntity entity, String animation) {
            if (entity == null || animation == null || animation.isBlank()) return false;
            try {
                entity.playAnimation(animation, java.util.List.of());
                return true;
            } catch (Throwable t) {
                LOGGER.debug("Could not play Cobblemon animation '{}' on {}.", animation, entity.getUuid(), t);
                return false;
            }
        }

        public static void hunt(PokemonEntity pokemon, ServerPlayerEntity player, double speed) {
            if (pokemon instanceof MobEntity mob) {
                mob.setTarget(player);
                mob.getNavigation().startMovingTo(player, Math.max(0.1, speed));
            }
        }

        public static void clearHuntTarget(PokemonEntity pokemon) {
            if (pokemon instanceof MobEntity mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
        }

        public static boolean startPve(ServerPlayerEntity player, PokemonEntity pokemon) {
            try {
                Class<?> builderClass = Class.forName("com.cobblemon.mod.common.battles.BattleBuilder");
                Field instanceField = builderClass.getField("INSTANCE");
                Object builder = instanceField.get(null);
                for (Method method : builderClass.getMethods()) {
                    if (!method.getName().equals("pve")) continue;
                    Class<?>[] p = method.getParameterTypes();
                    if (p.length == 2 && p[0].isAssignableFrom(player.getClass()) && p[1].isAssignableFrom(pokemon.getClass())) {
                        method.invoke(builder, player, pokemon);
                        return true;
                    }
                    if (p.length == 3 && p[1].isAssignableFrom(player.getClass()) && p[2].isAssignableFrom(pokemon.getClass())) {
                        Object format = null;
                        try {
                            format = p[0].getField("GEN_9_SINGLES").get(null);
                        } catch (Throwable ignored) {
                            for (Field field : p[0].getFields()) {
                                if (field.getName().toUpperCase(Locale.ROOT).contains("SINGLES")) {
                                    format = field.get(null);
                                    break;
                                }
                            }
                        }
                        if (format != null) {
                            method.invoke(builder, format, player, pokemon);
                            return true;
                        }
                    }
                }
                LOGGER.error("Cobblemon BattleBuilder.pve overload not found; encounter remains in field state.");
            } catch (Throwable t) {
                LOGGER.error("Could not start Cobblemon PVE battle.", t);
            }
            return false;
        }
    }

    public static final class ActiveEncounter {
        public final String definitionId;
        public final String tierId;
        public final UUID entityId;
        public final float healthMultiplier;
        public float maxHp = -1f;
        public float hp = -1f;
        public EncounterState state = EncounterState.IDLE;
        public UUID targetPlayer;
        public UUID pendingBattlePlayer;
        public UUID lastBattlePlayer;
        public long battleAtTick;
        public long pendingSinceTick;
        public long nextReengageTick;
        public long missingSinceTick;
        public int lastPokemonHealth = -1;
        public final Set<UUID> participants = new HashSet<>();
        public String dimension = "minecraft:overworld";
        public double x;
        public double y;
        public double z;
        public transient PokemonEntity entityRef;
        public transient ServerBossBar bossBar;
        public transient Set<UUID> bossBarViewers = new HashSet<>();

        ActiveEncounter(String definitionId, String tierId, UUID entityId, float healthMultiplier) {
            this.definitionId = definitionId;
            this.tierId = tierId;
            this.entityId = entityId;
            this.healthMultiplier = healthMultiplier;
        }
    }

    public static final class CatchWindow {
        public final UUID entityId;
        public final long expiresAtTick;

        CatchWindow(UUID entityId, long expiresAtTick) {
            this.entityId = entityId;
            this.expiresAtTick = expiresAtTick;
        }
    }

    public static final class SavedState {
        public Map<String, Long> cooldownRemaining = new HashMap<>();
        public List<SavedEncounter> active = new ArrayList<>();

        SavedState normalized() {
            if (cooldownRemaining == null) cooldownRemaining = new HashMap<>();
            if (active == null) active = new ArrayList<>();
            active.removeIf(Objects::isNull);
            return this;
        }
    }

    public static final class SavedEncounter {
        public String definitionId;
        public String tierId;
        public String entityId;
        public float maxHp;
        public float hp;
        public EncounterState state;
        public String targetPlayer;
        public String lastBattlePlayer;
        public List<String> participants = new ArrayList<>();
        public String dimension;
        public double x;
        public double y;
        public double z;

        static SavedEncounter from(ActiveEncounter active) {
            SavedEncounter saved = new SavedEncounter();
            saved.definitionId = active.definitionId;
            saved.tierId = active.tierId;
            saved.entityId = active.entityId.toString();
            saved.maxHp = active.maxHp;
            saved.hp = active.hp;
            saved.state = active.state;
            saved.targetPlayer = active.targetPlayer == null ? null : active.targetPlayer.toString();
            saved.lastBattlePlayer = active.lastBattlePlayer == null ? null : active.lastBattlePlayer.toString();
            for (UUID participant : active.participants) saved.participants.add(participant.toString());
            saved.dimension = active.dimension;
            saved.x = active.x;
            saved.y = active.y;
            saved.z = active.z;
            return saved;
        }
    }

    public static final class Config {
        public General general = new General();
        public Map<String, Tier> tiers = new LinkedHashMap<>();
        public List<EncounterDefinition> encounters = new ArrayList<>();

        static Config defaults() {
            Config c = new Config();
            c.tiers.put("regional", new Tier(4f, false, 32, 64, 1.0, 3.0, 60, true, 72, "YELLOW"));
            c.tiers.put("signature", new Tier(7f, true, 40, 80, 1.1, 3.0, 50, true, 88, "BLUE"));
            c.tiers.put("apex", new Tier(12f, true, 56, 112, 1.25, 3.5, 40, true, 112, "PURPLE"));

            EncounterDefinition godzilla = new EncounterDefinition();
            godzilla.id = "mount_yeager_godzilla";
            godzilla.displayName = "Tyranitar Godzilla";
            godzilla.tier = "apex";
            godzilla.pokemon = "tyranitar level=95 alpha=true cosmetic_item=godzilla";
            godzilla.spawn.dimensions.add("minecraft:overworld");
            godzilla.spawn.biomes.add("bestiary:mount_yeager");
            godzilla.spawn.weight = 0.25;
            godzilla.spawn.maxActive = 1;
            godzilla.spawn.globalCooldownTicks = 72000;
            godzilla.animations.aggro = "cry";
            godzilla.animations.hit = "recoil";
            godzilla.animations.battleStart = "special";
            godzilla.animations.battleEnd = "cry";
            godzilla.catchable = true;
            godzilla.catchPhaseSeconds = 120;
            godzilla.catchHealthPercent = 0.10f;
            godzilla.spawnMessage = "[Alpha] {name} has emerged in Mount Yeager.";
            godzilla.defeatMessage = "[Alpha] {name} has been defeated.";
            godzilla.catchMessage = "[Alpha] {name} is vulnerable to capture for a short time.";
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
                if (e.displayName == null || e.displayName.isBlank()) e.displayName = e.id;
                if (e.tier == null || e.tier.isBlank()) e.tier = "regional";
                if (e.pokemon == null || e.pokemon.isBlank()) e.enabled = false;
                if (e.spawn == null) e.spawn = new Spawn();
                if (e.animations == null) e.animations = new Animations();
                if (e.rewardCommands == null) e.rewardCommands = new ArrayList<>();
            }
            return this;
        }
    }

    public static final class General {
        public int spawnCheckIntervalTicks = 100;
        public double spawnAttemptChance = 0.12;
        public int huntUpdateIntervalTicks = 10;
        public int bossBarUpdateIntervalTicks = 5;
        public int stateSaveIntervalTicks = 1200;
        public boolean loadEncounterChunksOnRestore = true;
    }

    public static final class Tier {
        public float healthMultiplier = 4f;
        public boolean aggressive = false;
        public double aggroRadius = 32;
        public double leashRadius = 64;
        public double chaseSpeed = 1.0;
        public double battleTriggerDistance = 3.0;
        public int reengageCooldownTicks = 60;
        public boolean bossBar = true;
        public double bossBarRange = 72;
        public String bossBarColor = "YELLOW";

        public Tier() {
        }

        public Tier(float healthMultiplier, boolean aggressive, double aggroRadius, double leashRadius, double chaseSpeed, double battleTriggerDistance, int reengageCooldownTicks, boolean bossBar, double bossBarRange, String bossBarColor) {
            this.healthMultiplier = healthMultiplier;
            this.aggressive = aggressive;
            this.aggroRadius = aggroRadius;
            this.leashRadius = leashRadius;
            this.chaseSpeed = chaseSpeed;
            this.battleTriggerDistance = battleTriggerDistance;
            this.reengageCooldownTicks = reengageCooldownTicks;
            this.bossBar = bossBar;
            this.bossBarRange = bossBarRange;
            this.bossBarColor = bossBarColor;
        }

        static Tier defaults() {
            return new Tier();
        }
    }

    public static final class EncounterDefinition {
        public String id = "unnamed";
        public String displayName = "Unnamed Alpha";
        public boolean enabled = true;
        public String tier = "regional";
        public String pokemon = "pikachu level=50 alpha=true";
        public Spawn spawn = new Spawn();
        public Animations animations = new Animations();
        public boolean catchable = false;
        public int catchPhaseSeconds = 0;
        public float catchHealthPercent = 0.10f;
        public boolean rewardOnAdminDefeat = false;
        public List<String> rewardCommands = new ArrayList<>();
        public String spawnMessage = "";
        public String defeatMessage = "";
        public String catchMessage = "";
    }

    public static final class Animations {
        public String aggro = "cry";
        public String hit = "recoil";
        public String battleStart = "";
        public String battleEnd = "";
        public String defeat = "";
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
}
