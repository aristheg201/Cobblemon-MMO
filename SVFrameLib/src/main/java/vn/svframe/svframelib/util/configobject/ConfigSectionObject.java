package vn.svframe.svframelib.util.configobject;

import vn.svframe.svframelib.MythicLib;
import vn.svframe.svframelib.script.Script;

import java.util.LinkedHashMap;
import java.util.Map;

/** Map/YAML backed native replacement for Bukkit ConfigurationSection. */
public class ConfigSectionObject extends MapConfigObject {
    public ConfigSectionObject(Map<String, ?> section) { this(null, section); }
    public ConfigSectionObject(String key, Map<String, ?> section) { super(key, section); }

    public Script getScriptOrNull(String key) {
        if (!contains(key)) return null;
        return MythicLib.plugin.getSkills().loadScript(key, value(key));
    }
    public Script getScript(String key) {
        Script script = getScriptOrNull(key);
        if (script == null) throw new MissingArgumentException(key);
        return script;
    }
    @Override public ConfigSectionObject getObject(String key) {
        ConfigObject object = super.getObject(key);
        if (object instanceof ConfigSectionObject section) return section;
        if (object instanceof MapConfigObject map) return new ConfigSectionObject(key, map.asMap());
        throw new IllegalArgumentException("Not a config section: " + key);
    }
    @Override public ConfigSectionObject adaptObject(String key) {
        ConfigObject object = super.adaptObject(key);
        if (object instanceof MapConfigObject map) return new ConfigSectionObject(key, map.asMap());
        return new ConfigSectionObject(key, Map.of());
    }
}
