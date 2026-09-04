package vn.svframe.svframemmo.cobblemon.cosmetic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.integration.LuckPermsIntegration;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Persistent, data-driven player cosmetic ownership/equip service. */
public final class CosmeticService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SAVE_TYPE = new TypeToken<Map<String, SavedState>>(){}.getType();

    private final Map<String, CosmeticDefinition> definitions = new ConcurrentHashMap<>();
    private volatile Map<String, String> particleAliases = Map.of();
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final CosmeticRenderer renderer = new CosmeticRenderer();
    private volatile MinecraftServer server;
    private Path saveFile;
    private volatile boolean dirty;
    private volatile ExecutorService ioExecutor;

    public void reloadDefinitions() throws java.io.IOException {
        particleAliases = loadParticleAliases();
        CosmeticEmitterMetadata.clear();
        LinkedHashMap<String, CosmeticDefinition> next = new LinkedHashMap<>();
        if (Files.isDirectory(CosmeticDefaults.COSMETICS)) {
            try (var stream = Files.walk(CosmeticDefaults.COSMETICS)) {
                for (Path file : stream.filter(Files::isRegularFile).filter(CosmeticService::yaml).sorted().toList()) {
                    CosmeticDefinition definition = parse(file);
                    if (definition == null) continue;
                    if (next.putIfAbsent(definition.id(), definition) != null)
                        throw new java.io.IOException("Duplicate cosmetic " + definition.id());
                    CosmeticEmitterMetadata.register(file, definition);
                }
            }
        }
        definitions.clear();
        definitions.putAll(next);
        if (!next.isEmpty()) {
            EnumMap<CosmeticDefinition.Slot, Integer> counts = new EnumMap<>(CosmeticDefinition.Slot.class);
            next.values().forEach(definition -> counts.merge(definition.slot(), 1, Integer::sum));
            SVFrameMMOCobblemon.LOG.info("Loaded {} player cosmetic definition(s): {}", next.size(), counts);
        }
    }

    /** Reloads YAML definitions and rebuilds all online ambient renderers without restarting the server. */
    public int reloadAndRefresh() throws java.io.IOException {
        reloadDefinitions();

        boolean changed = false;
        for (PlayerState state : states.values()) {
            var iterator = state.equipped().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<CosmeticDefinition.Slot, String> entry = iterator.next();
                CosmeticDefinition definition = definition(entry.getValue());
                if (definition == null || definition.slot() != entry.getKey()
                        || !state.owned().contains(definition.id())) {
                    iterator.remove();
                    changed = true;
                }
            }
        }
        if (changed) dirty = true;

        renderer.clear();
        MinecraftServer current = server;
        if (current != null) {
            for (ServerPlayerEntity player : current.getPlayerManager().getPlayerList()) onJoin(player);
        }
        return size();
    }

    public synchronized void start(MinecraftServer server) {
        this.server = server;
        this.saveFile = server.getSavePath(WorldSavePath.ROOT).resolve("svframemmo-cobblemon-cosmetics.json");
        load();
        ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "svframemmo-cobblemon-cosmetic-save");
            thread.setDaemon(true);
            return thread;
        });
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) onJoin(player);
    }

    /** Shutdown barrier: drain queued writes and then synchronously write the final RAM snapshot. */
    public void stop() {
        ExecutorService executor;
        synchronized (this) {
            save();
            executor = ioExecutor;
            ioExecutor = null;
        }
        if (executor != null) {
            executor.shutdown();
            try {
                while (!executor.awaitTermination(1L, TimeUnit.SECONDS)) { }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
        synchronized (this) {
            if (saveFile != null) {
                try {
                    write(saveFile, snapshot());
                    dirty = false;
                } catch (Exception error) {
                    throw new IllegalStateException("Could not save cosmetic state", error);
                }
            }
            renderer.clear();
            server = null;
            saveFile = null;
        }
    }

    public void tick(long tick, MinecraftServer server) {
        renderer.tick(tick, server, this);
        if (dirty && tick % 100L == 0L) save();
    }

    public void onJoin(ServerPlayerEntity player) {
        renderer.clearPlayer(player.getUuid());
        PlayerState state = state(player.getUuid());
        state.equipped().values().stream().distinct().forEach(id -> {
            CosmeticDefinition definition = definition(id);
            if (definition != null && state.owned().contains(id)
                    && LuckPermsIntegration.has(player, definition.permission()))
                renderer.equip(player, definition);
        });
    }

    public void onDisconnect(ServerPlayerEntity player) {
        renderer.clearPlayer(player.getUuid());
        // Do not wait for the 100-tick periodic flush when a player logs out.
        save();
    }

    public Collection<CosmeticDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing((CosmeticDefinition d) -> d.slot().ordinal())
                        .thenComparing(CosmeticDefinition::id))
                .toList();
    }

    public CosmeticDefinition definition(String id) {
        return id == null ? null : definitions.get(CosmeticDefinition.normalize(id));
    }

    public int size() { return definitions.size(); }

    public int ownedCount(UUID player) { return state(player).owned().size(); }

    public Set<String> owned(UUID player) { return Set.copyOf(state(player).owned()); }

    public Map<CosmeticDefinition.Slot, String> equipped(UUID player) {
        return Map.copyOf(state(player).equipped());
    }

    public boolean grant(UUID player, String cosmeticId) {
        CosmeticDefinition definition = definition(cosmeticId);
        if (definition == null) return false;
        boolean changed = state(player).owned().add(definition.id());
        if (changed) dirty = true;
        return changed;
    }

    /** Admin/design helper: grants every currently loaded cosmetic definition. */
    public int grantAll(UUID player) {
        PlayerState state = state(player);
        int before = state.owned().size();
        for (CosmeticDefinition definition : definitions.values()) state.owned().add(definition.id());
        int granted = state.owned().size() - before;
        if (granted > 0) dirty = true;
        return granted;
    }

    public boolean revoke(UUID player, String cosmeticId) {
        CosmeticDefinition definition = definition(cosmeticId);
        if (definition == null) return false;
        PlayerState state = state(player);
        boolean changed = state.owned().remove(definition.id());
        if (!changed) return false;

        state.equipped().entrySet().removeIf(entry -> entry.getValue().equals(definition.id()));
        ServerPlayerEntity online = server == null ? null : server.getPlayerManager().getPlayer(player);
        renderer.clearPlayer(player);
        if (online != null) onJoin(online);
        dirty = true;
        return true;
    }

    public Result equip(ServerPlayerEntity player, String cosmeticId) {
        CosmeticDefinition definition = definition(cosmeticId);
        if (definition == null) return Result.fail("Unknown cosmetic.");
        PlayerState state = state(player.getUuid());
        if (!state.owned().contains(definition.id())) return Result.fail("You do not own this cosmetic.");
        if (!LuckPermsIntegration.has(player, definition.permission()))
            return Result.fail("You do not have permission to use this cosmetic.");

        String old = state.equipped().put(definition.slot(), definition.id());
        if (definition.id().equals(old)) return Result.ok(definition);
        if (old != null) {
            CosmeticDefinition previous = definition(old);
            if (previous != null) renderer.unequip(player, previous);
        }
        renderer.equip(player, definition);
        dirty = true;
        return Result.ok(definition);
    }

    public boolean unequip(ServerPlayerEntity player, CosmeticDefinition.Slot slot) {
        if (slot == null) return false;
        String old = state(player.getUuid()).equipped().remove(slot);
        if (old == null) return false;
        CosmeticDefinition definition = definition(old);
        if (definition != null) renderer.unequip(player, definition);
        dirty = true;
        return true;
    }

    public Result preview(ServerPlayerEntity player, String cosmeticId) {
        CosmeticDefinition definition = definition(cosmeticId);
        if (definition == null) return Result.fail("Unknown cosmetic.");
        if (!state(player.getUuid()).owned().contains(definition.id()))
            return Result.fail("You do not own this cosmetic.");
        if (!LuckPermsIntegration.has(player, definition.permission()))
            return Result.fail("You do not have permission to use this cosmetic.");
        renderer.trigger(player, definition, CosmeticDefinition.Trigger.PREVIEW);
        return Result.ok(definition);
    }

    public boolean isEquipped(UUID player, String cosmeticId) {
        return state(player).equipped().containsValue(CosmeticDefinition.normalize(cosmeticId));
    }

    private PlayerState state(UUID id) {
        return states.computeIfAbsent(id,
                ignored -> new PlayerState(new LinkedHashSet<>(), new EnumMap<>(CosmeticDefinition.Slot.class)));
    }

    private synchronized void load() {
        states.clear();
        if (saveFile == null || !Files.exists(saveFile)) return;
        try (Reader reader = Files.newBufferedReader(saveFile)) {
            Map<String, SavedState> saved = GSON.fromJson(reader, SAVE_TYPE);
            if (saved == null) return;
            saved.forEach((id, value) -> {
                try {
                    LinkedHashSet<String> owned = new LinkedHashSet<>();
                    if (value.owned != null)
                        value.owned.forEach(cosmetic -> owned.add(CosmeticDefinition.normalize(cosmetic)));

                    EnumMap<CosmeticDefinition.Slot, String> equipped =
                            new EnumMap<>(CosmeticDefinition.Slot.class);
                    if (value.equipped != null) {
                        value.equipped.forEach((savedKey, cosmetic) -> {
                            String normalized = CosmeticDefinition.normalize(cosmetic);
                            CosmeticDefinition.Slot slot = CosmeticDefinition.Slot.tryParse(savedKey);
                            if (slot == null) {
                                CosmeticDefinition definition = definition(normalized);
                                if (definition != null) slot = definition.slot();
                            }
                            if (slot != null && definition(normalized) != null)
                                equipped.put(slot, normalized);
                        });
                    }
                    states.put(UUID.fromString(id), new PlayerState(owned, equipped));
                } catch (RuntimeException ignored) { }
            });
        } catch (Exception error) {
            throw new IllegalStateException("Could not load cosmetic state", error);
        }
        dirty = false;
    }

    /** Captures an immutable save snapshot on the server thread and enqueues only disk IO. */
    public synchronized void save() {
        Path file = saveFile;
        ExecutorService executor = ioExecutor;
        if (file == null || executor == null || executor.isShutdown() || !dirty) return;
        Map<String, SavedState> out = snapshot();
        dirty = false;
        executor.execute(() -> {
            try {
                write(file, out);
            } catch (Exception error) {
                dirty = true;
                SVFrameMMOCobblemon.LOG.error("Could not asynchronously save cosmetic state", error);
            }
        });
    }

    private Map<String, SavedState> snapshot() {
        LinkedHashMap<String, SavedState> out = new LinkedHashMap<>();
        states.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            LinkedHashMap<String, String> equipped = new LinkedHashMap<>();
            entry.getValue().equipped().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(slot -> equipped.put(slot.getKey().id(), slot.getValue()));
            out.put(entry.getKey().toString(),
                    new SavedState(new ArrayList<>(entry.getValue().owned()), equipped));
        });
        return out;
    }

    private static void write(Path file, Map<String, SavedState> out) throws java.io.IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(out));
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private CosmeticDefinition parse(Path file) throws java.io.IOException {
        Map<String, Object> root = safeMap(YamlLite.parse(file));
        String id = string(root, "id", file.getFileName().toString().replaceFirst("\\.ya?ml$", ""));
        String rawSlot = string(root, "slot", "");
        if (rawSlot.isBlank()) {
            if (root.containsKey("skill"))
                SVFrameMMOCobblemon.LOG.warn("Ignoring legacy skill-bound VFX definition {}. Player cosmetics use slot:, not skill:.", file);
            else
                SVFrameMMOCobblemon.LOG.warn("Ignoring cosmetic definition without slot: {}", file);
            return null;
        }

        CosmeticDefinition.Slot slot;
        try {
            slot = CosmeticDefinition.Slot.parse(rawSlot);
        } catch (IllegalArgumentException error) {
            throw new java.io.IOException("Invalid cosmetic slot '" + rawSlot + "' in " + file, error);
        }

        String name = string(root, "name", id);
        String particleRef = string(root, "particle", "");
        String particle = particleAliases.getOrDefault(particleRef, particleRef);
        String permission = string(root, "permission", "svframemmo.cobblemon.cosmetic.use." + id);
        boolean hide = bool(root.get("hide-without-resource-pack"), false);

        Map<String, Object> fallbackMap = safeMap(root.get("fallback"));
        CosmeticDefinition.Fallback fallback = fallbackMap.isEmpty()
                ? CosmeticDefinition.Fallback.none()
                : new CosmeticDefinition.Fallback(
                        string(fallbackMap, "particle", ""),
                        integer(fallbackMap.get("count"), 0),
                        decimal(fallbackMap.get("spread"), 0.25d),
                        decimal(fallbackMap.get("speed"), 0.01d));

        List<CosmeticDefinition.Phase> phases = new ArrayList<>();
        Map<String, Object> phaseMap = safeMap(root.get("phases"));
        for (Map.Entry<String, Object> entry : phaseMap.entrySet()) {
            CosmeticDefinition.Trigger trigger;
            try {
                trigger = CosmeticDefinition.Trigger.parse(entry.getKey());
            } catch (IllegalArgumentException ignored) {
                SVFrameMMOCobblemon.LOG.warn("Ignoring unsupported cosmetic trigger '{}' in {}", entry.getKey(), file);
                continue;
            }

            for (Map<String, Object> phase : phaseLayers(entry.getValue())) {
                int interval = Math.max(1, integer(phase.get("interval-ticks"), 20));
                int repetitions = integer(phase.get("repetitions"), -1);
                if (repetitions < 1) {
                    int duration = integer(phase.get("duration-ticks"), 0);
                    repetitions = duration > 0
                            ? Math.max(1, Math.min(32, (duration + interval - 1) / interval))
                            : 1;
                }

                CosmeticDefinition.Anchor anchor = slot.defaultAnchor();
                String rawAnchor = string(phase, "anchor", "");
                if (!rawAnchor.isBlank()) {
                    try {
                        anchor = CosmeticDefinition.Anchor.valueOf(
                                rawAnchor.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
                    } catch (IllegalArgumentException error) {
                        throw new java.io.IOException("Invalid cosmetic anchor '" + rawAnchor + "' in " + file, error);
                    }
                }

                phases.add(new CosmeticDefinition.Phase(
                        trigger,
                        anchor,
                        integer(phase.get("delay-ticks"), 0),
                        Math.min(32, repetitions),
                        interval,
                        decimal(phase.get("offset-x"), 0d),
                        decimal(phase.get("offset-y"), 0d),
                        decimal(phase.get("offset-z"), 0d),
                        Math.min(64d, decimal(phase.get("broadcast-radius"), 32d)),
                        Math.min(128, integer(phase.get("max-viewers"), 48)),
                        decimal(phase.get("movement-threshold"), 0.35d),
                        decimal(phase.get("orbit-radius"), 0.80d),
                        integer(phase.get("orbit-period-ticks"), 40)));
            }
        }

        return new CosmeticDefinition(id, slot, name, particle, phases, fallback, hide, permission);
    }

    private Map<String, String> loadParticleAliases() throws java.io.IOException {
        Path file = CosmeticDefaults.VFX.resolve("particles.yml");
        if (!Files.isRegularFile(file)) return Map.of();
        Map<String, Object> root = safeMap(YamlLite.parse(file));
        Map<String, Object> aliases = safeMap(root.get("particles"));
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : aliases.entrySet()) {
            Map<String, Object> section = safeMap(entry.getValue());
            String particle = string(section, "particle", "");
            if (!particle.isBlank()) result.put(entry.getKey(), particle);
        }
        return Map.copyOf(result);
    }

    /** A trigger accepts either one phase map or a YAML list of phase maps. */
    private static List<Map<String, Object>> phaseLayers(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            ArrayList<Map<String, Object>> result = new ArrayList<>();
            for (Object layer : list) {
                if (layer instanceof Map<?, ?>) result.add(safeMap(layer));
            }
            return result;
        }
        if (value instanceof Map<?, ?>) return List.of(safeMap(value));
        return List.of();
    }

    private static Map<String, Object> safeMap(Object value) {
        if (value == null) return Map.of();
        Map<String, Object> map = YamlLite.map(value);
        return map == null ? Map.of() : map;
    }

    private static boolean yaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integer(Object value, int fallback) {
        try {
            return value instanceof Number number
                    ? number.intValue()
                    : value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double decimal(Object value, double fallback) {
        try {
            return value instanceof Number number
                    ? number.doubleValue()
                    : value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean bool(Object value, boolean fallback) {
        return value == null
                ? fallback
                : value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private record PlayerState(Set<String> owned, Map<CosmeticDefinition.Slot, String> equipped) { }

    private static final class SavedState {
        List<String> owned;
        Map<String, String> equipped;

        SavedState() { }

        SavedState(List<String> owned, Map<String, String> equipped) {
            this.owned = owned;
            this.equipped = equipped;
        }
    }

    public record Result(boolean success, String message, CosmeticDefinition definition) {
        static Result ok(CosmeticDefinition definition) { return new Result(true, null, definition); }
        static Result fail(String message) { return new Result(false, message, null); }
    }
}
