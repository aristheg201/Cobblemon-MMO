from pathlib import Path
import re


def rep(path, old, new, count=-1):
    p = Path(path)
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise RuntimeError(f'missing block in {path}: {old[:100]!r}')
    p.write_text(s.replace(old, new, count), encoding='utf-8')


def sub(path, pattern, replacement, flags=re.S):
    p = Path(path)
    s = p.read_text(encoding='utf-8')
    out, n = re.subn(pattern, replacement, s, flags=flags)
    if n != 1:
        raise RuntimeError(f'expected 1 regex match in {path}, got {n}: {pattern[:100]}')
    p.write_text(out, encoding='utf-8')

runtime = 'src/main/java/dev/aristheg/alphaencounter/runtime/EncounterRuntime.java'
config = 'src/main/java/dev/aristheg/alphaencounter/config/AlphaEncounterConfigManager.java'
ui = 'src/main/java/dev/aristheg/alphaencounter/config/UiConfigManager.java'
admin = 'src/main/java/dev/aristheg/alphaencounter/command/AdminCommands.java'

# ---- Runtime: reload all presentation config and rebuild bossbars. ----
rep(runtime, 'import net.minecraft.util.math.BlockPos;\n', 'import net.minecraft.util.math.BlockPos;\nimport net.minecraft.util.math.Box;\n')
rep(runtime, '    private long adminSpawns;\n', '    private long adminSpawns;\n    private long restoredEncounters;\n    private long restoredCatchWindows;\n    private long restoreMisses;\n')
rep(runtime, '''    public void reloadConfig() {
        config.load();
    }
''', '''    public void reloadConfig() {
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
''')

# Catch-window removals must dirty persistence.
rep(runtime, '''            Entity entity = findEntity(server, window.entityId);
            if (entity == null || entity.isRemoved()) { it.remove(); continue; }
            if (tick >= window.expiresAtTick) { entity.discard(); it.remove(); }
''', '''            Entity entity = findEntity(server, window.entityId);
            if (entity == null || entity.isRemoved()) { it.remove(); stateDirty = true; continue; }
            if (tick >= window.expiresAtTick) { entity.discard(); it.remove(); stateDirty = true; }
''')

# Persistent state: rebind world-saved entities by entity/canonical Pokemon UUID; never blind-spawn duplicates.
sub(runtime, r'    public void saveState\(MinecraftServer server\) \{.*?\n    \}\n\n    private void restoreState\(MinecraftServer server\) \{.*?\n    \}\n\n    private UUID parseUuid', '''    public void saveState(MinecraftServer server) {
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

    private UUID parseUuid''')

# Normalize partially populated v1 state on load.
rep(runtime, '''            SavedState loaded = GSON.fromJson(Files.readString(config.stateFile()), SavedState.class);
            if (loaded != null) pendingSavedState = loaded;
''', '''            SavedState loaded = GSON.fromJson(Files.readString(config.stateFile()), SavedState.class);
            if (loaded != null) { loaded.normalize(); pendingSavedState = loaded; }
''')

# State schema v2 stores physical entity UUID and catch windows.
sub(runtime, r'    public static final class SavedState \{.*?\n    \}\n    public static final class SavedEncounter \{.*?\n    \}\n\}', '''    public static final class SavedState {
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
}''')

rep(runtime, '    public String perfLine() { return "AlphaEncounter active="+activeByPokemon.size()+" spawnChecks="+spawnChecks+" spawned="+spawnSuccess+" adminSpawned="+adminSpawns+" worldHits="+interceptedHits+" fieldAttacks="+fieldAttacks+" battlesQueued="+battlesQueued+" battlesStarted="+battlesStarted+" battleEnds="+battleEnds+" defeats="+defeats+" "+AlphaEncounterMod.TEXT.integrationStatus(); }', '    public String perfLine() { return "AlphaEncounter active="+activeByPokemon.size()+" catchWindows="+catchWindows.size()+" spawnChecks="+spawnChecks+" spawned="+spawnSuccess+" adminSpawned="+adminSpawns+" worldHits="+interceptedHits+" fieldAttacks="+fieldAttacks+" battlesQueued="+battlesQueued+" battlesStarted="+battlesStarted+" battleEnds="+battleEnds+" defeats="+defeats+" restored="+restoredEncounters+" restoredCatch="+restoredCatchWindows+" restoreMisses="+restoreMisses+" "+AlphaEncounterMod.TEXT.integrationStatus(); }')
rep(runtime, '    public void resetCounters() { spawnChecks=spawnSuccess=interceptedHits=fieldAttacks=battlesQueued=battlesStarted=battleEnds=defeats=adminSpawns=0; }', '    public void resetCounters() { spawnChecks=spawnSuccess=interceptedHits=fieldAttacks=battlesQueued=battlesStarted=battleEnds=defeats=adminSpawns=restoredEncounters=restoredCatchWindows=restoreMisses=0; }')

# ---- Config migration: migrate old modular/monolithic messages and bossbar fields instead of silently dropping them. ----
rep(config, 'import dev.aristheg.alphaencounter.config.model.BehaviourConfig;\n', 'import dev.aristheg.alphaencounter.config.model.BehaviourConfig;\nimport dev.aristheg.alphaencounter.config.model.BossBarProfile;\n')
rep(config, 'import dev.aristheg.alphaencounter.config.model.GeneralConfig;\n', 'import dev.aristheg.alphaencounter.config.model.GeneralConfig;\nimport dev.aristheg.alphaencounter.config.model.MessageBundle;\nimport dev.aristheg.alphaencounter.config.model.MessageProfile;\n')

rep(config, '''                Path encounterDir = folder.resolve("encounters");
                for (Path file : jsonFiles(encounterDir)) {
                    EncounterDefinition def = read(file, EncounterDefinition.class, null);
                    if (def == null) continue;
''', '''                Path encounterDir = folder.resolve("encounters");
                for (Path file : jsonFiles(encounterDir)) {
                    JsonObject raw = readObject(file);
                    if (raw == null) continue;
                    if (migrateLegacyPresentation(raw, stripJson(file.getFileName().toString()))) writeJson(file, raw);
                    EncounterDefinition def = GSON.fromJson(raw, EncounterDefinition.class);
                    if (def == null) continue;
''')

rep(config, '''        Path behaviourFile = root.resolve("behaviours").resolve(safe(id) + ".json");
        Path tierFile = root.resolve("tiers").resolve(safe(id) + ".json");
        if (Files.notExists(behaviourFile)) writeJson(behaviourFile, behaviour);
        if (Files.notExists(tierFile)) writeJson(tierFile, tier);
''', '''        BossBarProfile bossBar = new BossBarProfile();
        bossBar.id = id;
        bossBar.enabled = bool(old, "bossBar", true);
        bossBar.range = number(old, "bossBarRange", 72.0);
        bossBar.color = string(old, "bossBarColor", "YELLOW");
        bossBar.style = "PROGRESS";
        bossBar.title = "<yellow><bold><ae_name></bold></yellow> <dark_gray>•</dark_gray> <white><ae_hp_percent>%</white> <gray>(<ae_hp>/<ae_max_hp>)</gray>";
        bossBar.normalize(id);

        Path behaviourFile = root.resolve("behaviours").resolve(safe(id) + ".json");
        Path tierFile = root.resolve("tiers").resolve(safe(id) + ".json");
        Path bossBarFile = root.resolve("bossbars").resolve(safe(id) + ".json");
        if (Files.notExists(behaviourFile)) writeJson(behaviourFile, behaviour);
        if (Files.notExists(tierFile)) writeJson(tierFile, tier);
        if (Files.notExists(bossBarFile)) writeJson(bossBarFile, bossBar);
''')

rep(config, '''        Path encounterFile = categoryDir.resolve("encounters").resolve(safe(id) + ".json");
        if (Files.notExists(encounterFile)) writeJson(encounterFile, encounter);
    }

    private String deriveCategory(JsonObject encounter) {
''', '''        Path encounterFile = categoryDir.resolve("encounters").resolve(safe(id) + ".json");
        if (Files.notExists(encounterFile)) {
            migrateLegacyPresentation(encounter, id);
            writeJson(encounterFile, encounter);
        }
    }

    private boolean migrateLegacyPresentation(JsonObject encounter, String fallbackId) throws IOException {
        boolean changed = false;
        String id = string(encounter, "id", fallbackId);
        String tier = string(encounter, "tier", "regional");
        boolean hasLegacyMessages = encounter.has("spawnMessage") || encounter.has("defeatMessage") || encounter.has("catchMessage");
        String messageProfile = string(encounter, "messageProfile", "");
        if (messageProfile.isBlank()) {
            if (hasLegacyMessages) {
                messageProfile = "legacy_" + safe(id);
                writeLegacyMessageProfile(messageProfile, encounter);
            } else messageProfile = tier;
            encounter.addProperty("messageProfile", messageProfile);
            changed = true;
        }
        if (string(encounter, "bossBarProfile", "").isBlank()) {
            encounter.addProperty("bossBarProfile", tier);
            changed = true;
        }
        changed |= encounter.remove("spawnMessage") != null;
        changed |= encounter.remove("defeatMessage") != null;
        changed |= encounter.remove("catchMessage") != null;
        return changed;
    }

    private void writeLegacyMessageProfile(String id, JsonObject encounter) throws IOException {
        Path file = root.resolve("messages/legacy.json");
        MessageBundle bundle = read(file, MessageBundle.class, new MessageBundle());
        bundle.normalize("legacy");
        bundle.language = "legacy";
        MessageProfile profile = bundle.profiles.getOrDefault(id, new MessageProfile());
        profile.id = id;
        if (encounter.has("spawnMessage")) profile.spawn = legacyText(string(encounter, "spawnMessage", ""));
        if (encounter.has("defeatMessage")) profile.defeat = legacyText(string(encounter, "defeatMessage", ""));
        if (encounter.has("catchMessage")) profile.catchAvailable = legacyText(string(encounter, "catchMessage", ""));
        profile.normalize(id);
        bundle.profiles.put(id, profile);
        writeJson(file, bundle);
    }

    private String legacyText(String value) {
        if (value == null) return "";
        return value.replace("{encounter}", "<ae_id>")
            .replace("{name}", "<ae_name>")
            .replace("{tier}", "<ae_tier>")
            .replace("{player}", "<player_name>");
    }

    private JsonObject readObject(Path file) {
        if (Files.notExists(file)) return null;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception e) {
            AlphaEncounterMod.LOGGER.error("Could not read JSON object {}.", file, e);
            return null;
        }
    }

    private String deriveCategory(JsonObject encounter) {
''')

# ---- UI: merge migrated legacy profiles under every selected language. ----
rep(ui, '''            messages = read(config.root().resolve("messages").resolve(language + ".json"), MessageBundle.class, defaultMessages(language));
            messages.normalize(language);
            bossBars.clear();
''', '''            messages = read(config.root().resolve("messages").resolve(language + ".json"), MessageBundle.class, defaultMessages(language));
            messages.normalize(language);
            Path legacyFile = config.root().resolve("messages/legacy.json");
            if (Files.exists(legacyFile)) {
                MessageBundle legacy = read(legacyFile, MessageBundle.class, null);
                if (legacy != null) {
                    legacy.normalize("legacy");
                    for (Map.Entry<String, MessageProfile> entry : legacy.profiles.entrySet()) messages.profiles.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
            bossBars.clear();
''')

# Admin feedback accurately reports full reload.
rep(admin, 'Alpha-Encounter config tree reloaded.', 'Alpha-Encounter gameplay config, messages, and bossbars reloaded.')

# Documentation: explicit restart semantics and persisted catch windows.
readme = Path('README.md')
s = readme.read_text(encoding='utf-8')
needle = '- Optional catch and reward phases run after the single final defeat.\n'
if needle in s and 'world-saved Pokemon entity' not in s:
    s = s.replace(needle, needle + '- On restart, active encounters rebind to the world-saved Pokemon entity by entity UUID/canonical Pokemon UUID; restore never blind-spawns a replacement, preventing duplicate bosses.\n- Catch windows are persisted with their remaining ticks and rebound on restart.\n')
readme.write_text(s, encoding='utf-8')

print('Production hardening patch applied.')
