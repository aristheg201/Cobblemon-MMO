package vn.svframe.fantasyhub.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class AbilityHud {
   static int lastRenderedSlots;
   private static final Set<String> ICONS = Set.of(
      "death_sentence", "death_strike", "phantom_charge", "cursed_seal", "necrotic_whirlwind", "wraithbound_chains"
   );

   private AbilityHud() {
   }

   static Identifier icon(String id) {
      String prefix = "CLS_DEATH_KNIGHT_";
      if (!id.startsWith(prefix)) {
         return null;
      } else {
         String suffix = id.substring(prefix.length()).toLowerCase(Locale.ROOT);
         return ICONS.contains(suffix) ? Identifier.of("death_knight", "textures/death_knight_texture/icon_" + suffix + ".png") : null;
      }
   }

   static void render(DrawContext context) {
      JsonArray slots = SkillBarPresentation.slots(FantasyHubClient.state());
      int count = slots.size();
      if (count != 0) {
         MinecraftClient client = MinecraftClient.getInstance();
         int size = 22;
         int gap = 3;
         int width = count * (size + gap) - gap;
         int x = (context.getScaledWindowWidth() - width) / 2;
         int y = context.getScaledWindowHeight() - 91;
         if (CompactHud.visible("stamina")) {
            y = Math.min(y, context.getScaledWindowHeight() - 39 - CompactHud.staminaOffset() - 32);
         }

         for (int index = 0; index < count; index++) {
            JsonObject skill = slots.get(index).getAsJsonObject();
            int slot = skill.get("inputSlot").getAsInt();
            int left = x + index * (size + gap);
            FantasyPanel.frame(context, left, y, size, size);
            String key = String.valueOf(slot);
            String id = skill.get("id").getAsString();
            if (!id.isEmpty()) {
               Identifier icon = icon(id);
               if (icon != null) {
                  context.drawTexture(icon, left + 3, y + 3, 16, 16, 0.0F, 0.0F, 64, 64, 64, 64);
               } else if (skill.has("iconMaterial")) {
                  Identifier material = Identifier.tryParse(skill.get("iconMaterial").getAsString());
                  if (material != null) {
                     ItemStack stack = new ItemStack((ItemConvertible)Registries.ITEM.get(material));
                     if (skill.has("iconCustomModelData") && !skill.get("iconCustomModelData").isJsonNull()) {
                        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(skill.get("iconCustomModelData").getAsInt()));
                     }

                     context.drawItem(stack, left + 3, y + 3);
                  }
               } else {
                  String name = FantasyHubClient.name(skill.get("name").getAsString()).getString();
                  context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(client.textRenderer.trimToWidth(name, 16)), left + 11, y + 7, -1518694);
               }

               double remaining = skill.get("cooldownRemaining").getAsDouble();
               if (remaining > 0.0) {
                  context.fill(left + 2, y + 2, left + size - 2, y + size - 2, -1258291200);
                  context.drawCenteredTextWithShadow(
                     client.textRenderer, Text.literal(String.format(Locale.ROOT, "%.0f", Math.ceil(remaining))), left + 11, y + 7, -860739
                  );
               } else {
                  JsonObject parameters = skill.getAsJsonObject("parameters");
                  boolean ready = parameters == null
                     || (!parameters.has("mana") || FantasyHubClient.number("mana") >= parameters.get("mana").getAsDouble())
                        && (!parameters.has("stamina") || FantasyHubClient.number("stamina") >= parameters.get("stamina").getAsDouble());
                  context.fill(left + 3, y + size - 3, left + size - 3, y + size - 2, ready ? -3560872 : -6213063);
               }
            }

            context.drawText(client.textRenderer, Text.literal(key), left + 1, y + 1, -2112631, true);
         }
         lastRenderedSlots=count;
      }
   }
}
