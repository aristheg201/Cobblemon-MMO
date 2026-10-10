package vn.svframe.svframemmo.manager;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import vn.svframe.svframelib.api.MMOLineConfig;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.config.DefaultFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Native tool-to-block permission tree used by custom mining. */
public final class RestrictionManager {
    private static final Logger LOG = Logger.getLogger("SVFrameMMO-Restrictions");
    private static final RestrictionManager INSTANCE = new RestrictionManager();
    private static final long RELOAD_CHECK_TICKS = 20L;

    private final Path file = DefaultFiles.ROOT.resolve("restrictions.yml");
    private volatile Map<Identifier, ToolPermissions> permissions = Map.of();
    private volatile ToolPermissions defaultPermissions;
    private volatile boolean loaded;
    private volatile long loadedModified = Long.MIN_VALUE;
    private volatile long nextReloadCheck;

    private RestrictionManager() { }

    public static RestrictionManager instance() { return INSTANCE; }

    /** Returns the exact permission set for the item, or the configured default set when no exact set exists. */
    public ToolPermissions getPermissions(ItemStack item) {
        ensureCurrent();
        if (item == null || item.isEmpty()) return defaultPermissions;
        ToolPermissions exact = permissions.get(Registries.ITEM.getId(item.getItem()));
        return exact == null ? defaultPermissions : exact;
    }

    /** Checks the configured permission tree without consulting vanilla mining tiers. */
    public boolean checkPermissions(ItemStack item, BlockState block) {
        if (block == null || block.isAir()) return false;
        ToolPermissions found = getPermissions(item);
        return found != null && found.canMine(Registries.BLOCK.getId(block.getBlock()));
    }

    public Collection<ToolPermissions> getAll() {
        ensureCurrent();
        return List.copyOf(permissions.values());
    }

    public synchronized void reload() throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Missing restrictions config: " + file);
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        LinkedHashMap<Identifier, PendingPermissions> pending = new LinkedHashMap<>();
        Identifier defaultId = null;

        for (Map.Entry<String, Object> entry : root.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> rawSection))
                throw new IOException("Restriction set '" + entry.getKey() + "' is not a section");
            Identifier toolId = parseItemId(entry.getKey());
            if (pending.containsKey(toolId)) throw new IOException("Duplicate restriction set for '" + toolId + "'");
            Map<String, Object> section = stringMap(rawSection);
            boolean isDefault = bool(section.get("default"), false);
            if (isDefault) {
                if (defaultId != null) throw new IOException("More than one default tool permission set: '" + defaultId + "' and '" + toolId + "'");
                defaultId = toolId;
            }
            Identifier parent = section.containsKey("parent") ? parseItemId(String.valueOf(section.get("parent"))) : null;
            LinkedHashSet<Identifier> mineable = new LinkedHashSet<>();
            for (String raw : strings(section.get("can-mine"))) mineable.add(parseBlockId(raw));
            pending.put(toolId, new PendingPermissions(toolId, isDefault, parent, Set.copyOf(mineable)));
        }

        for (PendingPermissions value : pending.values()) {
            if (value.parentId != null && !pending.containsKey(value.parentId))
                throw new IOException("Restriction set '" + value.toolId + "' references missing parent '" + value.parentId + "'");
        }

        LinkedHashMap<Identifier, ToolPermissions> built = new LinkedHashMap<>();
        for (PendingPermissions value : pending.values())
            built.put(value.toolId, new ToolPermissions(value.toolId, value.defaultSet, value.mineable));
        for (PendingPermissions value : pending.values()) {
            if (value.parentId != null) built.get(value.toolId).explicitParent = built.get(value.parentId);
        }

        ToolPermissions nextDefault = defaultId == null ? null : built.get(defaultId);
        for (ToolPermissions value : built.values()) value.defaultFallback = nextDefault;
        validateAcyclic(built.values());

        permissions = Map.copyOf(built);
        defaultPermissions = nextDefault;
        loadedModified = Files.getLastModifiedTime(file).toMillis();
        loaded = true;
        nextReloadCheck = SVFrameMMO.currentTick() + RELOAD_CHECK_TICKS;
        LOG.info("Loaded " + built.size() + " native mining restriction sets" + (nextDefault == null ? "" : "; default=" + nextDefault.toolId));
    }

    private void ensureCurrent() {
        long tick = SVFrameMMO.currentTick();
        if (loaded && tick < nextReloadCheck) return;
        synchronized (this) {
            tick = SVFrameMMO.currentTick();
            if (loaded && tick < nextReloadCheck) return;
            nextReloadCheck = tick + RELOAD_CHECK_TICKS;
            try {
                long modified = Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : Long.MIN_VALUE;
                if (!loaded || modified != loadedModified) reload();
            } catch (Exception exception) {
                LOG.log(Level.SEVERE, "Could not reload mining restrictions; keeping last valid permission tree", exception);
            }
        }
    }

    private static void validateAcyclic(Collection<ToolPermissions> values) throws IOException {
        for (ToolPermissions start : values) {
            HashSet<ToolPermissions> seen = new HashSet<>();
            ToolPermissions cursor = start;
            while (cursor != null) {
                if (!seen.add(cursor)) throw new IOException("Restriction parent cycle detected at '" + cursor.toolId + "'");
                cursor = cursor.getParent();
            }
        }
    }

    private static Identifier parseItemId(String raw) throws IOException {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) throw new IOException("Empty tool identifier in restrictions.yml");
        if (value.indexOf('{') >= 0) {
            MMOLineConfig line = new MMOLineConfig(value);
            if (!normalize(line.getKey()).equals("vanilla"))
                throw new IOException("Unsupported non-vanilla tool type '" + line.getKey() + "'");
            value = line.getString("type", "");
        }
        Identifier id = registryId(value);
        if (id == null || !Registries.ITEM.containsId(id)) throw new IOException("Unknown tool item '" + raw + "'");
        return id;
    }

    private static Identifier parseBlockId(String raw) throws IOException {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) throw new IOException("Empty block identifier in restrictions.yml");
        if (value.indexOf('{') >= 0) {
            MMOLineConfig line = new MMOLineConfig(value);
            if (!normalize(line.getKey()).equals("vanilla"))
                throw new IOException("Unsupported non-vanilla block type '" + line.getKey() + "'");
            value = line.getString("type", "");
        }
        Identifier id = registryId(value);
        if (id == null || !Registries.BLOCK.containsId(id)) throw new IOException("Unknown block '" + raw + "'");
        return id;
    }

    private static Identifier registryId(String raw) {
        if (raw == null) return null;
        String value = raw.trim().replace('?', '.').replace('%', '.').toLowerCase(Locale.ROOT);
        if (value.isBlank()) return null;
        if (value.indexOf(':') < 0) value = "minecraft:" + value;
        return Identifier.tryParse(value);
    }

    private static boolean bool(Object raw, boolean fallback) {
        return raw == null ? fallback : raw instanceof Boolean value ? value : Boolean.parseBoolean(String.valueOf(raw));
    }

    private static List<String> strings(Object raw) {
        if (raw instanceof Collection<?> collection) {
            ArrayList<String> result = new ArrayList<>(collection.size());
            for (Object value : collection) if (value != null) result.add(String.valueOf(value));
            return List.copyOf(result);
        }
        return raw == null ? List.of() : List.of(String.valueOf(raw));
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    private record PendingPermissions(Identifier toolId, boolean defaultSet, Identifier parentId, Set<Identifier> mineable) { }

    public static final class ToolPermissions {
        private final Identifier toolId;
        private final boolean defaultSet;
        private final Set<Identifier> mineable;
        private ToolPermissions explicitParent;
        private ToolPermissions defaultFallback;

        private ToolPermissions(Identifier toolId, boolean defaultSet, Set<Identifier> mineable) {
            this.toolId = toolId;
            this.defaultSet = defaultSet;
            this.mineable = mineable;
        }

        public Identifier getToolId() { return toolId; }
        public boolean isDefault() { return defaultSet; }
        public Set<Identifier> getMineableBlocks() { return mineable; }
        public ToolPermissions getParent() {
            return explicitParent != null ? explicitParent : defaultSet ? null : defaultFallback;
        }

        public boolean canMine(Identifier blockId) {
            ToolPermissions cursor = this;
            HashSet<ToolPermissions> visited = new HashSet<>();
            while (cursor != null && visited.add(cursor)) {
                if (cursor.mineable.contains(blockId)) return true;
                cursor = cursor.getParent();
            }
            return false;
        }
    }
}
