package dev.aristheg.alphaencounter.runtime;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.aristheg.alphaencounter.AlphaEncounterMod;
import dev.aristheg.alphaencounter.bridge.CobblemonBridge;
import dev.aristheg.alphaencounter.config.AlphaEncounterConfigManager;
import dev.aristheg.alphaencounter.config.model.BehaviourConfig;
import dev.aristheg.alphaencounter.config.model.EncounterDefinition;
import dev.aristheg.alphaencounter.config.model.TierConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class EncounterRuntime {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final AlphaEncounterConfigManager config;
    private final Map<UUID, ActiveEncounter> activeByEntity = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveEncounter> activeByPokemon = new ConcurrentHashMap<>();
    private final Map<String, Integer> activeByDefinition = new HashMap<>();
    private final Map<String, Long> globalCooldownUntil = new HashMap<>();
    private final Map<UUID, CatchWindow> catchWindows = new HashMap<>();
    private SavedState pendingSavedState;
    private boolean restoreApplied;
    private boolean stateDirty;
    private long tick;
    private long spawnChecks;
    private long spawnSuccess;
    private long interceptedHits;
    private long fieldAttacks;
    private long battlesQueued;
    private long battlesStarted;
    private long battleEnds;
    private long defeats;
    private long adminSpawns;
    private long restoredEncounters;
    private long restoredCatchWindows;
    private long restoreMisses;

    public EncounterRuntime(AlphaEncounterConfigManager config) {
        this.config = config;
    }

    public void initialize() {
        loadStateFile();
        CobblemonEvents.BATTLE_FAINTED.subscribe(this::onBattleFainted);
        CobblemonEvents.BATTLE_FLED.subscribe(this::onBattleFled);
        CobblemonEvents.BATTLE_VICTORY.subscribe(this::onBattleVictory);
    }

    public void reloadConfig() {
        config.load();
        AlphaEncounterMod.UI.load();
        for (ActiveEncounter active : activeByPokemon.values()) {
            if (active.bossBar != null) active.bossBar.clearPlayers();
            active.bossBar = null;
            active.bossBarViewers.clear();
            EncounterDefinition def = config.encounter(active.definitionId);
            if (def != null) setupBossBar(active, def, config.tier(def.tier));
        }
        AlphaEncounterMod.LOGGER.info("Reloaded Alpha-Encounter gameplay config, messages, and bossbar profiles.");
    }

    public void tick(MinecraftServer server) {
        tick++;
        if (!restoreApplied) restoreState(server);
        processBattleTransitions(server);
        processPendingBattles(server);
        processCatchWindows(server);
        tickFieldAttacks(server);

        int huntCadence = config.general().huntUpdateIntervalTicks;
        if (tick % huntCadence == 0) tickHunts(server);
        if (tick % config.general().bossBarUpdateIntervalTicks == 0) updateBossBars(server);
        if (tick % 100 == 0) cleanupMissingEntries(server);

        int spawnCadence = config.general().spawnCheckIntervalTicks;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            int offset = Math.floorMod(player.getUuid().hashCode(), spawnCadence);
            if ((tick + offset) % spawnCadence == 0) trySpawnNear(player);
        }

        if (stateDirty && tick % config.general().stateSaveIntervalTicks == 0) saveState(server);
    }

    private void trySpawnNear(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world) || player.isSpectator()) return;
        spawnChecks++;
        String dimension = world.getRegistryKey().getValue().toString();
        String biome = world.getBiome(player.getBlockPos()).getKey().map(k -> k.getValue().toString()).orElse("");
        List<EncounterDefinition> candidates = config.candidates(dimension, biome);
        if (candidates.isEmpty()) return;
        if (ThreadLocalRandom.current().nextDouble() > config.general().spawnAttemptChance) return;

        double total = 0;
        for (EncounterDefinition def : candidates) {
            if (eligible(def, dimension)) total += def.spawn.weight;
        }
        if (total <= 0) return;
        double roll = ThreadLocalRandom.current().nextDouble(total);
        EncounterDefinition picked = null;
        for (EncounterDefinition def : candidates) {
            if (!eligible(def, dimension)) continue;
            roll -= def.spawn.weight;
            if (roll <= 0) { picked = def; break; }
        }
        if (picked == null) return;

        BlockPos origin = player.getBlockPos();
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
        int radius = picked.spawn.minDistance + ThreadLocalRandom.current().nextInt(picked.spawn.maxDistance - picked.spawn.minDistance + 1);
        int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
        int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (!biomeAllowed(world, pos, picked.spawn.biomes)) return;
        if (spawnEncounter(picked.id, world, pos, false) != null) spawnSuccess++;
    }

    private boolean eligible(EncounterDefinition def, String dimension) {
        if (def == null || !def.enabled || def.spawn.weight <= 0) return false;
        if (!def.spawn.dimensions.isEmpty() && !def.spawn.dimensions.contains(dimension)) return false;
        if (activeByDefinition.getOrDefault(def.id, 0) >= def.spawn.maxActive) return false;
        return globalCooldownUntil.getOrDefault(def.id, 0L) <= tick;
    }

    private boolean biomeAllowed(ServerWorld world, BlockPos pos, List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) return true;
        return world.getBiome(pos).getKey().map(k -> allowed.contains(k.getValue().toString())).orElse(false);
    }

    public ActiveEncounter spawnEncounter(String id, ServerWorld world, BlockPos pos, boolean admin) {
        EncounterDefinition def = config.encounter(id);
        if (def == null || !def.enabled) return null;
        try {
            PokemonProperties properties = PokemonProperties.Companion.parse(def.pokemon);
            PokemonEntity entity = CobblemonBridge.createEntity(properties, world);
            entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, ThreadLocalRandom.current().nextFloat() * 360f, 0f);
            entity.setQueuedToDespawn(false);
            if (!world.spawnEntity(entity)) return null;

            TierConfig tier = config.tier(def.tier);
            Pokemon pokemon = entity.getPokemon();
            ActiveEncounter active = new ActiveEncounter(def.id, def.tier, entity.getUuid(), pokemon.getUuid(), tier.healthMultiplier);
            active.entityRef = entity;
            active.pokemonRef = pokemon;
            updateLocation(active, entity);
            initializeHealth(active, pokemon);
            active.state = config.behaviourForTier(def.tier).aggressive ? EncounterState.HUNT : EncounterState.IDLE;
            bind(active);
            if (!admin) globalCooldownUntil.put(def.id, tick + def.spawn.globalCooldownTicks);
            else adminSpawns++;
            setupBossBar(active, def, tier);
            broadcastProfile(world.getServer(), active, null, "spawn");
            stateDirty = true;
            AlphaEncounterMod.LOGGER.info("Spawned '{}' properties='{}' pokemonAspects={} entityAspects={} pokemonUUID={} entityUUID={}", def.id, def.pokemon, pokemon.getAspects(), entity.getAspects(), pokemon.getUuid(), entity.getUuid());
            return active;
        } catch (Throwable t) {
            AlphaEncounterMod.LOGGER.error("Failed to spawn encounter '{}'.", id, t);
            return null;
        }
    }

    private void bind(ActiveEncounter active) {
        activeByEntity.put(active.entityId, active);
        activeByPokemon.put(active.pokemonId, active);
        activeByDefinition.merge(active.definitionId, 1, Integer::sum);
    }

    private void rebindEntity(ActiveEncounter active, PokemonEntity replacement) {
        activeByEntity.remove(active.entityId);
        active.entityId = replacement.getUuid();
        active.entityRef = replacement;
        active.pokemonRef = replacement.getPokemon();
        activeByEntity.put(active.entityId, active);
        updateLocation(active, replacement);
    }

    public boolean shouldRedirectDamage(Entity entity) {
        return entity instanceof PokemonEntity pokemon && activeByEntity.containsKey(entity.getUuid()) && !pokemon.isBattling();
    }

    public void onResolvedWorldDamage(PokemonEntity entity, DamageSource source, float damage) {
        if (damage <= 0) return;
        ActiveEncounter active = activeByEntity.get(entity.getUuid());
        if (active == null || active.state == EncounterState.DEFEATED) return;
        initializeHealth(active, entity.getPokemon());
        active.hp = Math.max(1f, active.hp - damage);
        interceptedHits++;
        updateLocation(active, entity);
        CobblemonBridge.playAnimation(entity, animation(active, "hit"));
        if (source.getAttacker() instanceof ServerPlayerEntity player) {
            active.participants.add(player.getUuid());
            active.targetPlayer = player.getUuid();
            queueBattle(active, player);
        }
        stateDirty = true;
    }

    private void queueBattle(ActiveEncounter active, ServerPlayerEntity player) {
        if (active.state == EncounterState.DEFEATED || active.state == EncounterState.BATTLE || active.state == EncounterState.BATTLE_PENDING) return;
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
        for (ActiveEncounter active : activeByPokemon.values()) {
            if (active.state != EncounterState.BATTLE_PENDING) continue;
            PokemonEntity pokemon = resolveEntity(server, active);
            if (pokemon == null) continue;
            if (pokemon.isBattling()) { enterBattle(active, pokemon); continue; }
            if (active.pendingBattlePlayer == null) { active.state = EncounterState.HUNT; continue; }
            if (active.battleAtTick > tick) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(active.pendingBattlePlayer);
            if (player == null || player.isSpectator() || player.getWorld() != pokemon.getWorld()) {
                active.pendingBattlePlayer = null;
                active.state = EncounterState.HUNT;
                continue;
            }
            prepareLocalBar(active, pokemon.getPokemon());
            CobblemonBridge.playAnimation(pokemon, animation(active, "battleStart"));
            boolean invoked = CobblemonBridge.startPve(player, pokemon);
            if (invoked) {
                active.lastBattlePlayer = player.getUuid();
                active.participants.add(player.getUuid());
                broadcastProfile(server, active, player.getUuid(), "battleStart");
            }
            active.pendingBattlePlayer = null;
            if (!invoked || tick - active.pendingSinceTick > config.general().battlePendingTimeoutTicks) {
                active.state = EncounterState.HUNT;
                active.nextReengageTick = tick + config.behaviourForTier(active.tierId).reengageCooldownTicks;
            }
        }
    }

    private void processBattleTransitions(MinecraftServer server) {
        for (ActiveEncounter active : activeByPokemon.values()) {
            if (active.state != EncounterState.BATTLE && active.state != EncounterState.BATTLE_PENDING) continue;
            Pokemon pokemon = resolvePokemon(active);
            PokemonEntity entity = resolveEntity(server, active);
            boolean battling = entity != null && entity.isBattling();
            if (battling) {
                if (active.state != EncounterState.BATTLE) enterBattle(active, entity);
                syncBattleHealth(active, pokemon.getCurrentHealth());
            } else if (active.state == EncounterState.BATTLE) {
                syncBattleHealth(active, pokemon.getCurrentHealth());
                finishBattle(server, active);
            }
        }
    }

    private void enterBattle(ActiveEncounter active, PokemonEntity entity) {
        active.state = EncounterState.BATTLE;
        active.pendingBattlePlayer = null;
        active.lastPokemonHealth = entity.getPokemon().getCurrentHealth();
        battlesStarted++;
        stateDirty = true;
    }

    private void syncBattleHealth(ActiveEncounter active, int current) {
        if (active.maxHp <= 0f) return;
        Pokemon pokemon = resolvePokemon(active);
        int localMax = active.battleLocalMax > 0 ? active.battleLocalMax : Math.max(1, pokemon.getMaxHealth());
        active.battleLocalMax = localMax;
        active.hp = Math.max(0f, Math.min(active.maxHp, active.maxHp * (Math.max(0, current) / (float)localMax)));
        active.lastPokemonHealth = current;
        stateDirty = true;
    }

    private void onBattleFainted(BattleFaintedEvent event) {
        ActiveEncounter active = activeByPokemon.get(event.getKilled().getOriginalPokemon().getUuid());
        if (active == null) return;
        active.hp = 0f;
        active.lastPokemonHealth = 0;
        active.defeatPending = true;
        stateDirty = true;
    }

    private void onBattleFled(BattleFledEvent event) {
        ActiveEncounter active = encounterFromBattle(event.getBattle());
        if (active == null || active.hp <= 0.5f) return;
        active.lastBattlePlayer = event.getPlayer().getUuid();
        active.targetPlayer = active.lastBattlePlayer;
        stateDirty = true;
    }

    private void onBattleVictory(BattleVictoryEvent event) {
        ActiveEncounter active = encounterFromBattle(event.getBattle());
        if (active == null) return;
        Pokemon pokemon = resolvePokemon(active);
        syncBattleHealth(active, pokemon.getCurrentHealth());
        if (pokemon.getCurrentHealth() <= 0 || active.hp <= 0.5f) {
            active.hp = 0f;
            active.defeatPending = true;
        }
        stateDirty = true;
    }

    private ActiveEncounter encounterFromBattle(PokemonBattle battle) {
        for (BattleActor actor : battle.getActors()) {
            for (BattlePokemon battlePokemon : actor.getPokemonList()) {
                ActiveEncounter active = activeByPokemon.get(battlePokemon.getOriginalPokemon().getUuid());
                if (active != null) return active;
            }
        }
        return null;
    }

    private void finishBattle(MinecraftServer server, ActiveEncounter active) {
        battleEnds++;
        PokemonEntity entity = resolveEntity(server, active);
        if (entity != null) CobblemonBridge.playAnimation(entity, animation(active, "battleEnd"));
        if (active.defeatPending || active.hp <= 0.5f || resolvePokemon(active).getCurrentHealth() <= 0) {
            active.hp = 0f;
            defeatEncounter(server, active, false);
            return;
        }
        active.state = EncounterState.HUNT;
        active.targetPlayer = active.lastBattlePlayer;
        active.nextReengageTick = tick + config.behaviourForTier(active.tierId).reengageCooldownTicks;
        active.lastPokemonHealth = -1;
        active.battleLocalMax = -1;
        active.defeatPending = false;
        broadcastProfile(server, active, active.lastBattlePlayer, "battleEnd");
        stateDirty = true;
    }

    private void prepareLocalBar(ActiveEncounter active, Pokemon pokemon) {
        initializeHealth(active, pokemon);
        int localMax = Math.max(1, pokemon.getMaxHealth());
        int localHp = Math.max(1, Math.min(localMax, (int)Math.ceil(localMax * (active.hp / active.maxHp))));
        pokemon.setCurrentHealth(localHp);
        active.battleLocalMax = localMax;
        active.lastPokemonHealth = localHp;
        active.defeatPending = false;
    }

    private void tickHunts(MinecraftServer server) {
        for (ActiveEncounter active : activeByPokemon.values()) {
            if (active.state != EncounterState.HUNT && active.state != EncounterState.IDLE) continue;
            PokemonEntity pokemon = resolveEntity(server, active);
            if (pokemon == null) continue;
            BehaviourConfig behaviour = config.behaviourForTier(active.tierId);
            if (!behaviour.aggressive && active.state == EncounterState.IDLE) continue;
            ServerPlayerEntity target = validTarget(server, pokemon, active.targetPlayer, behaviour.leashRadius);
            if (target == null) target = nearestPlayer(server, pokemon, behaviour.aggroRadius);
            if (target == null) {
                active.targetPlayer = null;
                if (pokemon instanceof MobEntity mob) { mob.setTarget(null); mob.getNavigation().stop(); }
                if (!behaviour.aggressive) active.state = EncounterState.IDLE;
                continue;
            }
            boolean newTarget = !target.getUuid().equals(active.targetPlayer);
            active.targetPlayer = target.getUuid();
            active.state = EncounterState.HUNT;
            if (newTarget) { CobblemonBridge.playAnimation(pokemon, animation(active, "aggro")); broadcastProfile(server, active, target.getUuid(), "aggro"); }
            if (pokemon instanceof MobEntity mob) {
                mob.setTarget(target);
                if (tick >= active.nextPathRefreshTick || mob.getNavigation().isIdle()) {
                    mob.getNavigation().startMovingTo(target, behaviour.chaseSpeed);
                    active.nextPathRefreshTick = tick + config.general().huntUpdateIntervalTicks;
                }
            }
        }
    }

    private void tickFieldAttacks(MinecraftServer server) {
        for (ActiveEncounter active : activeByPokemon.values()) {
            if (active.state != EncounterState.HUNT || active.targetPlayer == null || tick < active.nextFieldAttackTick) continue;
            PokemonEntity pokemon = resolveEntity(server, active);
            if (pokemon == null || pokemon.isBattling()) continue;
            BehaviourConfig behaviour = config.behaviourForTier(active.tierId);
            ServerPlayerEntity target = validTarget(server, pokemon, active.targetPlayer, behaviour.leashRadius);
            if (target == null) continue;
            if (pokemon.squaredDistanceTo(target) > behaviour.fieldAttackRange * behaviour.fieldAttackRange) continue;

            CobblemonBridge.playAnimation(pokemon, behaviour.fieldAttackAnimation);
            boolean damaged = target.damage(pokemon.getDamageSources().mobAttack(pokemon), (float) behaviour.fieldAttackDamage);
            if (damaged && behaviour.fieldAttackKnockback > 0) {
                target.takeKnockback(behaviour.fieldAttackKnockback, pokemon.getX() - target.getX(), pokemon.getZ() - target.getZ());
            }
            active.nextFieldAttackTick = tick + behaviour.fieldAttackCooldownTicks;
            fieldAttacks++;
            // Deliberately no battle transition here. Only a player damaging the boss provokes battle.
        }
    }

    private ServerPlayerEntity validTarget(MinecraftServer server, PokemonEntity pokemon, UUID id, double leash) {
        if (id == null) return null;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
        if (player == null || player.isSpectator() || player.getWorld() != pokemon.getWorld()) return null;
        return pokemon.squaredDistanceTo(player) <= leash * leash ? player : null;
    }

    private ServerPlayerEntity nearestPlayer(MinecraftServer server, PokemonEntity pokemon, double radius) {
        ServerPlayerEntity best = null;
        double bestSq = radius * radius;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || player.getWorld() != pokemon.getWorld()) continue;
            double sq = pokemon.squaredDistanceTo(player);
            if (sq < bestSq) { bestSq = sq; best = player; }
        }
        return best;
    }

    private void updateBossBars(MinecraftServer server) {
        for (ActiveEncounter active : activeByPokemon.values()) {
            if (active.bossBar == null) continue;
            PokemonEntity pokemon = resolveEntity(server, active);
            if (pokemon == null) continue;
            EncounterDefinition def = config.encounter(active.definitionId);
            if (def == null) continue;
            var profile = AlphaEncounterMod.UI.bossBar(def.bossBarProfile);
            active.bossBar.setPercent(active.maxHp <= 0 ? 1f : Math.max(0f, Math.min(1f, active.hp / active.maxHp)));
            active.bossBar.setName(AlphaEncounterMod.TEXT.render(profile.title, textContext(server, active, null)));
            Set<UUID> desired = new HashSet<>(active.participants);
            double rangeSq = profile.range * profile.range;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) if (player.getWorld() == pokemon.getWorld() && pokemon.squaredDistanceTo(player) <= rangeSq) desired.add(player.getUuid());
            for (UUID id : desired) if (active.bossBarViewers.add(id)) { ServerPlayerEntity player = server.getPlayerManager().getPlayer(id); if (player != null) active.bossBar.addPlayer(player); }
            Iterator<UUID> it = active.bossBarViewers.iterator();
            while (it.hasNext()) { UUID id=it.next(); if(desired.contains(id))continue; ServerPlayerEntity player=server.getPlayerManager().getPlayer(id); if(player!=null)active.bossBar.removePlayer(player); it.remove(); }
        }
    }

    private void setupBossBar(ActiveEncounter active, EncounterDefinition def, TierConfig tier) {
        var profile = AlphaEncounterMod.UI.bossBar(def.bossBarProfile == null || def.bossBarProfile.isBlank() ? tier.bossBarProfile : def.bossBarProfile);
        if (!profile.enabled) return;
        BossBar.Color color; BossBar.Style style;
        try { color = BossBar.Color.valueOf(profile.color.toUpperCase(Locale.ROOT)); } catch(Exception ignored){ color=BossBar.Color.PURPLE; }
        try { style = BossBar.Style.valueOf(profile.style.toUpperCase(Locale.ROOT)); } catch(Exception ignored){ style=BossBar.Style.PROGRESS; }
        active.bossBar = new ServerBossBar(Text.literal(def.displayName), color, style);
    }

    private void defeatEncounter(MinecraftServer server, ActiveEncounter active, boolean adminForced) {
        if (active.state == EncounterState.DEFEATED) return;
        active.state = EncounterState.DEFEATED;
        defeats++;
        EncounterDefinition def = config.encounter(active.definitionId);
        Pokemon pokemon = resolvePokemon(active);
        PokemonEntity entity = resolveEntity(server, active);
        if (entity != null) CobblemonBridge.playAnimation(entity, animation(active, "defeat"));
        if (def != null && (!adminForced || def.rewardOnAdminDefeat)) {
            runRewards(server, active, def);
            broadcastProfile(server, active, null, "defeat");
        }

        if (def != null && def.catchable && def.catchPhaseSeconds > 0) {
            pokemon.setCurrentHealth(Math.max(1, Math.round(pokemon.getMaxHealth() * def.catchHealthPercent)));
            ServerWorld world = resolveWorld(server, active.dimension);
            PokemonEntity catchEntity = entity;
            if ((catchEntity == null || catchEntity.isRemoved()) && world != null) {
                catchEntity = CobblemonBridge.respawnCanonical(pokemon, world, BlockPos.ofFloored(active.x, active.y, active.z));
            }
            if (catchEntity != null) {
                catchEntity.setGlowing(true);
                catchWindows.put(catchEntity.getUuid(), new CatchWindow(catchEntity.getUuid(), tick + def.catchPhaseSeconds * 20L));
                broadcastProfile(server, active, null, "catchAvailable");
            }
        } else if (entity != null && !entity.isRemoved()) {
            entity.discard();
        }
        removeActive(active, false);
        stateDirty = true;
    }

    private void runRewards(MinecraftServer server, ActiveEncounter active, EncounterDefinition def) {
        for (UUID id : active.participants) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player == null) continue;
            for (String raw : def.rewardCommands) {
                if (raw == null || raw.isBlank()) continue;
                String command = raw.replace("{player}", player.getName().getString()).replace("{encounter}", active.definitionId).replace("{tier}", active.tierId);
                try { server.getCommandManager().executeWithPrefix(server.getCommandSource(), command); }
                catch (Throwable t) { AlphaEncounterMod.LOGGER.error("Reward command failed: {}", command, t); }
            }
        }
    }

    private void processCatchWindows(MinecraftServer server) {
        Iterator<Map.Entry<UUID, CatchWindow>> it = catchWindows.entrySet().iterator();
        while (it.hasNext()) {
            CatchWindow window = it.next().getValue();
            Entity entity = findEntity(server, window.entityId);
            if (entity == null || entity.isRemoved()) { it.remove(); stateDirty = true; continue; }
            if (tick >= window.expiresAtTick) { entity.discard(); it.remove(); stateDirty = true; }
        }
    }

    private void cleanupMissingEntries(MinecraftServer server) {
        for (ActiveEncounter active : new ArrayList<>(activeByPokemon.values())) {
            if (active.state == EncounterState.BATTLE) continue;
            PokemonEntity entity = resolveEntity(server, active);
            if (entity != null && !entity.isRemoved()) { active.missingSinceTick = 0; continue; }
            if (active.missingSinceTick == 0) active.missingSinceTick = tick;
            if (tick - active.missingSinceTick < config.general().missingEntityGraceTicks) continue;
            AlphaEncounterMod.LOGGER.warn("Encounter '{}' disappeared outside battle; removing runtime entry.", active.definitionId);
            removeActive(active, false);
            stateDirty = true;
        }
    }

    private void initializeHealth(ActiveEncounter active, Pokemon pokemon) {
        if (active.maxHp > 0 && active.hp >= 0) return;
        active.maxHp = Math.max(1, pokemon.getMaxHealth()) * Math.max(1f, active.healthMultiplier);
        active.hp = active.maxHp;
    }

    private Pokemon resolvePokemon(ActiveEncounter active) {
        if (active.pokemonRef != null) return active.pokemonRef;
        if (active.entityRef != null) active.pokemonRef = active.entityRef.getPokemon();
        return active.pokemonRef;
    }

    private PokemonEntity resolveEntity(MinecraftServer server, ActiveEncounter active) {
        if (active.entityRef != null && !active.entityRef.isRemoved()) { updateLocation(active, active.entityRef); return active.entityRef; }
        Entity raw = findEntity(server, active.entityId);
        if (raw instanceof PokemonEntity pokemon) {
            active.entityRef = pokemon;
            active.pokemonRef = pokemon.getPokemon();
            updateLocation(active, pokemon);
            return pokemon;
        }
        return null;
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
        try { return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dimension))); }
        catch (Exception ignored) { return null; }
    }

    private void updateLocation(ActiveEncounter active, PokemonEntity entity) {
        active.dimension = entity.getWorld().getRegistryKey().getValue().toString();
        active.x = entity.getX(); active.y = entity.getY(); active.z = entity.getZ();
    }

    private void removeActive(ActiveEncounter active, boolean discard) {
        activeByEntity.remove(active.entityId);
        activeByPokemon.remove(active.pokemonId);
        activeByDefinition.computeIfPresent(active.definitionId, (k, v) -> v <= 1 ? null : v - 1);
        if (active.bossBar != null) active.bossBar.clearPlayers();
        if (discard && active.entityRef != null && !active.entityRef.isRemoved()) active.entityRef.discard();
    }

    private String animation(ActiveEncounter active, String phase) {
        EncounterDefinition def = config.encounter(active.definitionId);
        if (def == null) return "";
        return switch (phase) {
            case "aggro" -> def.animations.aggro;
            case "hit" -> def.animations.hit;
            case "battleStart" -> def.animations.battleStart;
            case "battleEnd" -> def.animations.battleEnd;
            case "defeat" -> def.animations.defeat;
            default -> "";
        };
    }

    private void broadcastProfile(MinecraftServer server, ActiveEncounter active, UUID playerId, String key) {
        EncounterDefinition def = config.encounter(active.definitionId);
        if (def == null) return;
        var profile = AlphaEncounterMod.UI.messages(def.messageProfile);
        String raw = switch (key) { case "spawn" -> profile.spawn; case "aggro" -> profile.aggro; case "battleStart" -> profile.battleStart; case "battleEnd" -> profile.battleEnd; case "defeat" -> profile.defeat; case "catchAvailable" -> profile.catchAvailable; case "despawn" -> profile.despawn; default -> ""; };
        ServerPlayerEntity player = playerId == null ? null : server.getPlayerManager().getPlayer(playerId);
        AlphaEncounterMod.TEXT.broadcast(server, raw, textContext(server, active, player));
    }

    public dev.aristheg.alphaencounter.text.TextContext textContext(MinecraftServer server, ActiveEncounter active, ServerPlayerEntity player) {
        Pokemon pokemon = resolvePokemon(active);
        PokemonEntity entity = resolveEntity(server, active);
        EncounterDefinition def = config.encounter(active.definitionId);
        String biome = "";
        if (entity != null) biome = entity.getWorld().getBiome(entity.getBlockPos()).getKey().map(k -> k.getValue().toString()).orElse("");
        return new dev.aristheg.alphaencounter.text.TextContext(server, player, active, def, pokemon, entity, biome);
    }

    private void loadStateFile() {
        try {
            if (Files.notExists(config.stateFile())) return;
            SavedState loaded = GSON.fromJson(Files.readString(config.stateFile()), SavedState.class);
            if (loaded != null) { loaded.normalize(); pendingSavedState = loaded; }
        } catch (Exception e) { AlphaEncounterMod.LOGGER.error("Failed loading persistent state.", e); }
    }

    public void saveState(MinecraftServer server) {
        try {
            Files.createDirectories(config.stateFile().getParent());
            SavedState saved = new SavedState();
            for (Map.Entry<String, Long> e : globalCooldownUntil.entrySet()) {
                long remaining = Math.max(0, e.getValue() - tick);
                if (remaining > 0) saved.cooldownRemaining.put(e.getKey(), remaining);
            }
            for (ActiveEncounter active : activeByPokemon.values()) {
                PokemonEntity entity = resolveEntity(server, active);
                if (entity != null) updateLocation(active, entity);
                saved.active.add(SavedEncounter.from(active));
            }
            for (CatchWindow window : catchWindows.values()) {
                long remaining = Math.max(0, window.expiresAtTick - tick);
                Entity raw = findEntity(server, window.entityId);
                if (remaining <= 0 || !(raw instanceof PokemonEntity entity) || entity.isRemoved()) continue;
                SavedCatchWindow catchWindow = new SavedCatchWindow();
                catchWindow.entityId = entity.getUuid().toString();
                catchWindow.pokemonId = entity.getPokemon().getUuid().toString();
                catchWindow.dimension = entity.getWorld().getRegistryKey().getValue().toString();
                catchWindow.x = entity.getX(); catchWindow.y = entity.getY(); catchWindow.z = entity.getZ();
                catchWindow.remainingTicks = remaining;
                saved.catchWindows.add(catchWindow);
            }
            Files.writeString(config.stateFile(), GSON.toJson(saved));
            stateDirty = false;
        } catch (Exception e) { AlphaEncounterMod.LOGGER.error("Failed saving persistent state.", e); }
    }

    private void restoreState(MinecraftServer server) {
        restoreApplied = true;
        if (pendingSavedState == null) return;
        pendingSavedState.normalize();
        for (Map.Entry<String, Long> e : pendingSavedState.cooldownRemaining.entrySet()) {
            globalCooldownUntil.put(e.getKey(), tick + Math.max(0, e.getValue()));
        }

        for (SavedEncounter saved : pendingSavedState.active) {
            EncounterDefinition def = config.encounter(saved.definitionId);
            if (def == null || !def.enabled) continue;
            ServerWorld world = resolveWorld(server, saved.dimension);
            if (world == null) { restoreMisses++; continue; }
            BlockPos pos = BlockPos.ofFloored(saved.x, saved.y, saved.z);
            if (config.general().loadEncounterChunksOnRestore) world.getChunk(pos);

            PokemonEntity entity = findSavedPokemonEntity(server, world, saved.entityId, saved.pokemonId, saved.x, saved.y, saved.z);
            if (entity == null) {
                restoreMisses++;
                AlphaEncounterMod.LOGGER.warn("Could not rebind saved encounter '{}' pokemonUUID={} entityUUID={}; skipping instead of spawning a duplicate.", saved.definitionId, saved.pokemonId, saved.entityId);
                continue;
            }
            Pokemon pokemon = entity.getPokemon();
            if (activeByPokemon.containsKey(pokemon.getUuid()) || activeByEntity.containsKey(entity.getUuid())) {
                AlphaEncounterMod.LOGGER.warn("Saved encounter '{}' already rebound; ignoring duplicate state entry.", saved.definitionId);
                continue;
            }

            TierConfig tier = config.tier(def.tier);
            ActiveEncounter active = new ActiveEncounter(def.id, def.tier, entity.getUuid(), pokemon.getUuid(), tier.healthMultiplier);
            active.entityRef = entity;
            active.pokemonRef = pokemon;
            active.maxHp = saved.maxHp > 0 ? saved.maxHp : Math.max(1, pokemon.getMaxHealth()) * Math.max(1f, tier.healthMultiplier);
            active.hp = Math.max(1, Math.min(active.maxHp, saved.hp > 0 ? saved.hp : active.maxHp));
            active.state = "IDLE".equalsIgnoreCase(saved.state) && !config.behaviourForTier(def.tier).aggressive ? EncounterState.IDLE : EncounterState.HUNT;
            active.targetPlayer = parseUuid(saved.targetPlayer);
            active.lastBattlePlayer = parseUuid(saved.lastBattlePlayer);
            for (String value : saved.participants) { UUID id = parseUuid(value); if (id != null) active.participants.add(id); }
            entity.setQueuedToDespawn(false);
            if (pokemon.getCurrentHealth() <= 0) pokemon.setCurrentHealth(Math.max(1, pokemon.getMaxHealth()));
            updateLocation(active, entity);
            bind(active);
            setupBossBar(active, def, tier);
            restoredEncounters++;
            AlphaEncounterMod.LOGGER.info("Rebound saved encounter '{}' pokemonUUID={} entityUUID={} sharedHP={}/{}", def.id, pokemon.getUuid(), entity.getUuid(), Math.round(active.hp), Math.round(active.maxHp));
        }

        for (SavedCatchWindow saved : pendingSavedState.catchWindows) {
            if (saved.remainingTicks <= 0) continue;
            ServerWorld world = resolveWorld(server, saved.dimension);
            if (world == null) { restoreMisses++; continue; }
            BlockPos pos = BlockPos.ofFloored(saved.x, saved.y, saved.z);
            if (config.general().loadEncounterChunksOnRestore) world.getChunk(pos);
            PokemonEntity entity = findSavedPokemonEntity(server, world, saved.entityId, saved.pokemonId, saved.x, saved.y, saved.z);
            if (entity == null) {
                restoreMisses++;
                AlphaEncounterMod.LOGGER.warn("Could not rebind saved catch window pokemonUUID={} entityUUID={}; dropping stale window.", saved.pokemonId, saved.entityId);
                continue;
            }
            entity.setQueuedToDespawn(false);
            entity.setGlowing(true);
            catchWindows.put(entity.getUuid(), new CatchWindow(entity.getUuid(), tick + saved.remainingTicks));
            restoredCatchWindows++;
        }
        pendingSavedState = null;
        stateDirty = true;
    }

    private PokemonEntity findSavedPokemonEntity(MinecraftServer server, ServerWorld world, String entityIdRaw, String pokemonIdRaw, double x, double y, double z) {
        UUID expectedEntity = parseUuid(entityIdRaw);
        UUID expectedPokemon = parseUuid(pokemonIdRaw);
        if (expectedEntity != null) {
            Entity raw = findEntity(server, expectedEntity);
            if (raw instanceof PokemonEntity entity && (expectedPokemon == null || expectedPokemon.equals(entity.getPokemon().getUuid()))) return entity;
        }
        if (expectedPokemon == null) return null;
        Box box = new Box(x - 32.0, y - 32.0, z - 32.0, x + 32.0, y + 32.0, z + 32.0);
        for (PokemonEntity entity : world.getEntitiesByClass(PokemonEntity.class, box, candidate -> expectedPokemon.equals(candidate.getPokemon().getUuid()))) return entity;
        return null;
    }

    private UUID parseUuid(String value) {
        try { return value == null ? null : UUID.fromString(value); } catch (Exception ignored) { return null; }
    }

    public List<ActiveEncounter> activeSorted() {
        return activeByPokemon.values().stream().sorted(Comparator.comparing(a -> a.definitionId)).toList();
    }

    public ActiveEncounter resolveAdminTarget(net.minecraft.server.command.ServerCommandSource source, String token) {
        if (token == null || token.isBlank() || token.equalsIgnoreCase("nearest")) return nearestActive(source, null);
        try {
            UUID id = UUID.fromString(token);
            ActiveEncounter found = activeByEntity.get(id);
            if (found == null) found = activeByPokemon.get(id);
            if (found != null) return found;
        } catch (Exception ignored) {}
        return nearestActive(source, token);
    }

    private ActiveEncounter nearestActive(net.minecraft.server.command.ServerCommandSource source, String defFilter) {
        ActiveEncounter best = null;
        double bestSq = Double.MAX_VALUE;
        for (ActiveEncounter active : activeByPokemon.values()) {
            if (defFilter != null && !defFilter.equals(active.definitionId)) continue;
            PokemonEntity entity = active.entityRef;
            if (entity == null || entity.isRemoved() || entity.getWorld() != source.getWorld()) continue;
            double dx = entity.getX() - source.getPosition().x, dy = entity.getY() - source.getPosition().y, dz = entity.getZ() - source.getPosition().z;
            double sq = dx*dx + dy*dy + dz*dz;
            if (sq < bestSq) { bestSq = sq; best = active; }
        }
        return best;
    }

    public String inspectLine(MinecraftServer server, ActiveEncounter active) {
        if (active == null) return "no encounter";
        Pokemon pokemon = resolvePokemon(active);
        PokemonEntity entity = resolveEntity(server, active);
        EncounterDefinition def = config.encounter(active.definitionId);
        return active.definitionId + " state=" + active.state + " sharedHP=" + Math.round(active.hp) + "/" + Math.round(active.maxHp)
            + " pokemonUUID=" + active.pokemonId + " entityUUID=" + active.entityId
            + " properties='" + (def == null ? "?" : def.pokemon) + "' pokemonAspects=" + (pokemon == null ? "?" : pokemon.getAspects())
            + " entityAspects=" + (entity == null ? "<removed>" : entity.getAspects());
    }

    public boolean adminDespawn(ActiveEncounter active) { if (active == null) return false; removeActive(active, true); stateDirty = true; return true; }
    public boolean adminDefeat(MinecraftServer server, ActiveEncounter active) { if (active == null) return false; active.hp = 0; defeatEncounter(server, active, true); return true; }
    public boolean adminBattle(ActiveEncounter active, ServerPlayerEntity player) { if (active == null || player == null) return false; queueBattle(active, player); return true; }
    public boolean adminAnimation(MinecraftServer server, ActiveEncounter active, String animation) { PokemonEntity e = active == null ? null : resolveEntity(server, active); return e != null && CobblemonBridge.playAnimation(e, animation); }
    public boolean adminAttack(MinecraftServer server, ActiveEncounter active, ServerPlayerEntity player) {
        if (active == null || player == null) return false;
        PokemonEntity pokemon = resolveEntity(server, active);
        if (pokemon == null || pokemon.getWorld() != player.getWorld()) return false;
        BehaviourConfig b = config.behaviourForTier(active.tierId);
        CobblemonBridge.playAnimation(pokemon, b.fieldAttackAnimation);
        return player.damage(pokemon.getDamageSources().mobAttack(pokemon), (float)b.fieldAttackDamage);
    }
    public boolean adminSetHp(MinecraftServer server, ActiveEncounter active, float percent) {
        if (active == null) return false;
        Pokemon pokemon = resolvePokemon(active);
        if (pokemon == null) return false;
        initializeHealth(active, pokemon);
        active.hp = active.maxHp * Math.max(0.01f, Math.min(1f, percent / 100f));
        if (active.state == EncounterState.BATTLE) prepareLocalBar(active, pokemon);
        stateDirty = true;
        return true;
    }
    public void resetCooldown(String id) { if (id == null || id.equalsIgnoreCase("all")) globalCooldownUntil.clear(); else globalCooldownUntil.remove(id); stateDirty = true; }
    public int activeCount() { return activeByPokemon.size(); }
    public long cooldownRemainingTicks(String id) { return Math.max(0L, globalCooldownUntil.getOrDefault(id, 0L) - tick); }
    public ActiveEncounter resolveExternalTarget(net.minecraft.server.command.ServerCommandSource source, String token) { return resolveAdminTarget(source, token); }
    public String perfLine() { return "AlphaEncounter active="+activeByPokemon.size()+" catchWindows="+catchWindows.size()+" spawnChecks="+spawnChecks+" spawned="+spawnSuccess+" adminSpawned="+adminSpawns+" worldHits="+interceptedHits+" fieldAttacks="+fieldAttacks+" battlesQueued="+battlesQueued+" battlesStarted="+battlesStarted+" battleEnds="+battleEnds+" defeats="+defeats+" restored="+restoredEncounters+" restoredCatch="+restoredCatchWindows+" restoreMisses="+restoreMisses+" "+AlphaEncounterMod.TEXT.integrationStatus(); }
    public void resetCounters() { spawnChecks=spawnSuccess=interceptedHits=fieldAttacks=battlesQueued=battlesStarted=battleEnds=defeats=adminSpawns=restoredEncounters=restoredCatchWindows=restoreMisses=0; }

    public static final class CatchWindow {
        public final UUID entityId; public final long expiresAtTick;
        public CatchWindow(UUID entityId, long expiresAtTick) { this.entityId=entityId; this.expiresAtTick=expiresAtTick; }
    }
    public static final class SavedState {
        public int version = 2;
        public Map<String, Long> cooldownRemaining = new HashMap<>();
        public List<SavedEncounter> active = new ArrayList<>();
        public List<SavedCatchWindow> catchWindows = new ArrayList<>();
        public void normalize() {
            if (cooldownRemaining == null) cooldownRemaining = new HashMap<>();
            if (active == null) active = new ArrayList<>();
            if (catchWindows == null) catchWindows = new ArrayList<>();
        }
    }
    public static final class SavedEncounter {
        public String definitionId, tierId, entityId, pokemonId, state, targetPlayer, lastBattlePlayer, dimension;
        public float maxHp, hp; public double x,y,z; public List<String> participants = new ArrayList<>();
        static SavedEncounter from(ActiveEncounter a) {
            SavedEncounter s = new SavedEncounter();
            s.definitionId=a.definitionId; s.tierId=a.tierId; s.entityId=a.entityId==null?null:a.entityId.toString(); s.pokemonId=a.pokemonId==null?null:a.pokemonId.toString(); s.state=a.state==null?null:a.state.name();
            s.maxHp=a.maxHp; s.hp=a.hp; s.targetPlayer=a.targetPlayer==null?null:a.targetPlayer.toString(); s.lastBattlePlayer=a.lastBattlePlayer==null?null:a.lastBattlePlayer.toString(); s.dimension=a.dimension; s.x=a.x; s.y=a.y; s.z=a.z;
            for(UUID id:a.participants)s.participants.add(id.toString());
            return s;
        }
    }
    public static final class SavedCatchWindow {
        public String entityId, pokemonId, dimension;
        public long remainingTicks;
        public double x,y,z;
    }
}
