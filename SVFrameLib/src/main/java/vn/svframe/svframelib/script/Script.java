package vn.svframe.svframelib.script;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.MMOLineConfig;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.script.condition.Condition;
import vn.svframe.svframelib.script.mechanic.Mechanic;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.PostLoadAction;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import vn.svframe.svframelib.util.configobject.ConfigSectionObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Native Fabric port of the SVFrameLib script object, preserving the 1.7.1
 * post-load lifecycle while replacing server-plugin platform ConfigurationSection with ConfigObject.
 */
public class Script {
    private final String id;
    private final boolean publik;
    private final List<Condition> conditions = new ArrayList<>();
    private final List<Mechanic> mechanics = new ArrayList<>();

    private final PostLoadAction postLoadAction = new PostLoadAction(raw -> {
        if (!(raw instanceof ConfigObject config))
            throw new IllegalArgumentException("Script post-load config must be a ConfigObject");
        loadObjects(config, "conditions", true);
        loadObjects(config, "mechanics", false);
    });

    public Script(String key, List<String> mechanics) {
        Objects.requireNonNull(key, "Script id cannot be null");
        Map<String,Object> adapted = new LinkedHashMap<>();
        adapted.put("mechanics", mechanics == null ? List.of() : List.copyOf(mechanics));
        postLoadAction.cacheConfig(new ConfigSectionObject(key, adapted));
        this.id = key;
        this.publik = false;
    }

    public Script(ConfigObject config) {
        Objects.requireNonNull(config, "Script config cannot be null");
        if (!config.hasKey()) throw new IllegalArgumentException("Script configuration requires a key");
        postLoadAction.cacheConfig(config);
        this.id = config.getKey();
        this.publik = config.getBoolean("public", false);
    }

    public Script(String id, boolean publik) {
        this.id = Objects.requireNonNull(id, "Script id cannot be null");
        this.publik = publik;
    }

    /** Retained for native callers already constructing explicit condition/mechanic lists. */
    public Script(String id, boolean publik, List<String> conditionLines, List<String> mechanicLines) {
        this.id = Objects.requireNonNull(id, "Script id cannot be null");
        this.publik = publik;
        Map<String,Object> adapted = new LinkedHashMap<>();
        adapted.put("conditions", conditionLines == null ? List.of() : List.copyOf(conditionLines));
        adapted.put("mechanics", mechanicLines == null ? List.of() : List.copyOf(mechanicLines));
        postLoadAction.cacheConfig(new ConfigSectionObject(id, adapted));
        postLoadAction.performAction();
    }

    public PostLoadAction getPostLoadAction() { return postLoadAction; }

    private void loadObjects(ConfigObject config, String path, boolean condition) {
        if (!config.contains(path)) return;
        Object raw = config.get(path);
        if (raw instanceof Map<?,?> section) {
            for (Map.Entry<?,?> entry : section.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Supplier<ConfigObject> supplier = () -> adaptSectionEntry(key, entry.getValue());
                if (condition) registerCondition(key, supplier); else registerMechanic(key, supplier);
            }
            return;
        }
        if (raw instanceof List<?> list) {
            for (Object obj : list) {
                String line = String.valueOf(obj);
                Supplier<ConfigObject> supplier = () -> new MMOLineConfig(line);
                if (condition) registerCondition(line, supplier); else registerMechanic(line, supplier);
            }
            return;
        }
        throw new IllegalArgumentException("Script '" + id + "' path '" + path + "' must be a section or list");
    }

    private static ConfigObject adaptSectionEntry(String key, Object value) {
        if (value instanceof ConfigObject config) return config;
        if (value instanceof Map<?,?> raw) {
            Map<String,Object> mapped = new LinkedHashMap<>();
            raw.forEach((k, v) -> mapped.put(String.valueOf(k), v));
            return new ConfigSectionObject(key, mapped);
        }
        if (value == null) return new ConfigSectionObject(key, Map.of());
        return new ConfigSectionObject(key, Map.of("type", String.valueOf(value)));
    }

    private void registerCondition(String key, Supplier<ConfigObject> config) {
        try {
            conditions.add(SVFrameLib.plugin.getSkills().loadCondition(config.get()));
        } catch (RuntimeException exception) {
            SVFrameLib.plugin.getLogger().log(Level.WARNING,
                    "Could not load condition '" + key + "' from script '" + id + "': " + exception.getMessage());
        }
    }

    private void registerMechanic(String key, Supplier<ConfigObject> config) {
        try {
            mechanics.add(SVFrameLib.plugin.getSkills().loadMechanic(config.get()));
        } catch (RuntimeException exception) {
            SVFrameLib.plugin.getLogger().log(Level.WARNING,
                    "Could not load mechanic '" + key + "' from script '" + id + "': " + exception.getMessage());
        }
    }

    public String getId() { return id; }
    public boolean isPublic() { return publik; }
    public List<Mechanic> getMechanics() { return mechanics; }
    public List<Condition> getConditions() { return conditions; }
    public boolean cast(MMOPlayerData playerData) { return cast(SkillMetadata.of(playerData)); }

    public boolean cast(SkillMetadata meta) {
        int conditionCounter = 0;
        for (Condition condition : conditions) {
            try {
                conditionCounter++;
                if (!condition.checkIfMet(meta)) return false;
            } catch (RuntimeException exception) {
                SVFrameLib.plugin.getLogger().log(Level.WARNING,
                        "Could not check condition n" + conditionCounter + " from script '" + id + "': " + exception.getMessage());
                return false;
            }
        }
        new MechanicQueue(meta, this).next();
        return true;
    }
}
