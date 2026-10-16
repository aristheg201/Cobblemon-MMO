package vn.svframe.fantasyhub.core;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Uses Filament's public cosmetic API. Equipment stacks and their effects are never removed. */
public final class FilamentCosmetics {
  private record Api(Method add, Method destroy, Method data) {}
  private static final Api API = discover();

  private FilamentCosmetics() {}

  private static Api discover() {
    if (!FabricLoader.getInstance().isModLoaded("filament")) return null;
    try {
      Class<?> access = Class.forName("de.tomalbrc.filament.cosmetic.CosmeticInterface");
      return new Api(
          access.getMethod("filament$addHolder", LivingEntity.class, Item.class, ItemStack.class, String.class),
          access.getMethod("filament$destroyHolder", String.class),
          Class.forName("de.tomalbrc.filament.cosmetic.CosmeticUtil").getMethod("getCosmeticData", ItemStack.class));
    } catch (ReflectiveOperationException incompatible) {
      throw new IllegalStateException("Unsupported Filament cosmetic API; cannot safely hide transformed equipment", incompatible);
    }
  }

  public static void hide(LivingEntity player) {
    if (API == null) return;
    try {
      for (EquipmentSlot slot : EquipmentSlot.values())
        if (slot.isArmorSlot()) API.destroy.invoke(player, slot.getName());
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Cannot hide Filament cosmetic holders", failure);
    }
  }

  public static void restore(LivingEntity player) {
    if (API == null || player.isRemoved()) return;
    try {
      for (EquipmentSlot slot : EquipmentSlot.values()) {
        if (!slot.isArmorSlot()) continue;
        ItemStack stack = player.getEquippedStack(slot);
        if (!stack.isEmpty() && API.data.invoke(null, stack) != null) {
          // Rebuild from the current slot: equipment may have changed while transformed.
          API.destroy.invoke(player, slot.getName());
          API.add.invoke(player, player, stack.getItem(), stack, slot.getName());
        }
      }
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Cannot restore Filament cosmetic holders", failure);
    }
  }
}
