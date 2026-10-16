package vn.svframe.fantasyhub.client;

import com.google.gson.JsonObject;

/** Older servers and missing/invalid preferences preserve the vanilla EXP HUD. */
final class ExperiencePresentation {
    private ExperiencePresentation() {}
    static boolean rpg(JsonObject state) {
        var mode=state.get("experienceDisplay");
        return mode!=null&&mode.isJsonPrimitive()&&mode.getAsJsonPrimitive().isString()
                &&mode.getAsString().equalsIgnoreCase("MMO");
    }
}
