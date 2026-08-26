package vn.svframe.svframelib.manager;

import vn.svframe.svframelib.MythicLib;
import vn.svframe.svframelib.gson.JsonElement;
import vn.svframe.svframelib.module.MMOPlugin;
import vn.svframe.svframelib.module.Module;

/** Gson-backed native JSON facade matching MythicLib 1.7.1. */
public class JsonManager extends Module {
    public JsonManager(MMOPlugin plugin) { super(plugin, "json"); }

    public <T> T parse(String input, Class<T> type) {
        return MythicLib.inst().getGson().fromJson(input, type);
    }

    public String toString(JsonElement element) {
        return MythicLib.inst().getGson().toJson(element);
    }
}
