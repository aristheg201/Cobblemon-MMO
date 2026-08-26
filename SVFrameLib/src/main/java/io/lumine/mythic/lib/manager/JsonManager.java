package io.lumine.mythic.lib.manager;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.gson.JsonElement;
import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.module.Module;

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
