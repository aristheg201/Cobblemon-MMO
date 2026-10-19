package vn.svframe.fantasyhub.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

final class SkillBarPresentation {
   private SkillBarPresentation() {
   }

   record Layout(int x,int y,int width,int slotWidth,int slotHeight,int gap) {}

   static float renderScale(double guiScale,float preference) {
      return (float)Math.min(1,2/Math.max(1,guiScale))*Math.clamp(preference,.5f,1f);
   }

   static Layout layout(int screenWidth,int screenHeight,int count,int staminaTop) {
      int cell=22,gap=2,height=28,width=Math.max(0,count*cell+Math.max(0,count-1)*gap);
      int y=Math.min(screenHeight-105,staminaTop-height-10);
      return new Layout((screenWidth-width)/2,Math.max(26,y),width,cell,height,gap);
   }

   static double cooldownFraction(JsonObject skill) {
      double remaining=skill.has("cooldownRemaining")?skill.get("cooldownRemaining").getAsDouble():0;
      var p=skill.getAsJsonObject("parameters");
      double total=p!=null&&p.has("cooldown")?p.get("cooldown").getAsDouble():remaining;
      return Math.clamp(total<=0?0:remaining/total,0,1);
   }

   static String cooldownLabel(double remaining) {
      if(remaining<=0)return "";
      return remaining<1?String.format(java.util.Locale.ROOT,"%.1f",Math.ceil(remaining*10)/10):String.valueOf((int)Math.ceil(remaining));
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
