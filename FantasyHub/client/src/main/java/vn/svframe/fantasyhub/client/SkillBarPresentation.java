package vn.svframe.fantasyhub.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

final class SkillBarPresentation {
   private SkillBarPresentation() {
   }

   static JsonArray slots(JsonObject state) {
      JsonObject casting = state.getAsJsonObject("casting");
      if (casting != null
         && casting.has("active")
         && casting.get("active").getAsBoolean()
         && casting.has("mode")
         && "SKILL_BAR".equals(casting.get("mode").getAsString())) {
         JsonArray slots = casting.getAsJsonArray("slots");
         return slots == null ? new JsonArray() : slots;
      } else {
         return new JsonArray();
      }
   }
}
