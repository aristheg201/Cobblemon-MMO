package vn.svframe.svframelib.manager;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.gson.JsonElement;
import vn.svframe.svframelib.module.MMOPlugin;
import vn.svframe.svframelib.module.Module;

/** Gson-backed native JSON facade matching SVFrameLib 1.7.1. */
public class JsonManager extends Module {
    public JsonManager(MMOPlugin plugin) { super(plugin, "json"); }

    public <T> T parse(String input, Class<T> type) {
        return SVFrameLib.inst().getGson().fromJson(input, type);
    }

    public String toString(JsonElement element) {
        return SVFrameLib.inst().getGson().toJson(element);
    }
}
