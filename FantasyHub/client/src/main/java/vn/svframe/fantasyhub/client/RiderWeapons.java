package vn.svframe.fantasyhub.client;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.*;
import net.minecraft.util.Hand;

/** Form projections do not replace or mutate the player's real inventory stacks. */
public final class RiderWeapons {
  private static final java.util.Map<String, ItemStack> PRESENTATION_STACKS =
      new java.util.HashMap<>();

  private RiderWeapons() {}

  public static boolean overrides(int entity) {
    var state = RiderVisuals.state(entity);
    return state != null
        && switch (state.form()) {
          case "ryuki",
                  "knight",
                  "decade",
                  "agito_storm",
                  "agito_flame",
                  "agito_trinity",
                  "agito_shining" ->
              true;
          default -> false;
        };
  }

  public static ItemStack projected(int entity, Hand hand) {
    var state = RiderVisuals.state(entity);
    if (state == null) return ItemStack.EMPTY;
    String model =
        switch (state.form()) {
          case "ryuki" ->
              hand == Hand.MAIN_HAND && !RiderAnimator.posing(entity, "PUNCH") ? "drag_saber" : "";
          case "knight" ->
              hand == Hand.MAIN_HAND
                  ? (state.force() == 1 ? "dark_blade" : "dark_visor")
                  : state.force() == 1 ? "dark_blade" : "";
          case "decade" ->
              hand == Hand.MAIN_HAND
                  ? (RiderEffects.bookerGun(entity) ? "ride_booker_gun" : "ride_booker_sword")
                  : "";
          case "agito_storm" -> hand == Hand.MAIN_HAND ? "storm_halberd" : "";
          case "agito_flame" -> hand == Hand.MAIN_HAND ? "flame_saber" : "";
          case "agito_trinity" -> hand == Hand.MAIN_HAND ? "flame_saber" : "storm_halberd";
          case "agito_shining" -> "shining_caliber";
          default -> "";
        };
    if (model.isEmpty()) return ItemStack.EMPTY;
    return modelStack(model);
  }

  /** Render-only stacks, owned here and never inserted into an inventory. */
  static ItemStack modelStack(String model) {
    return PRESENTATION_STACKS.computeIfAbsent(
        model,
        key -> {
          var stack = new ItemStack(Items.IRON_SWORD);
          NbtComponent.set(
              DataComponentTypes.CUSTOM_DATA,
              stack,
              n -> n.putString("SVFRAME_ITEM_MODEL", "fantasyhub:item/riders/" + key));
          return stack;
        });
  }
}
