package vn.svframe.fantasyhub.protocol;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.util.Pair;

/** Optional public-API adapters. No third-party classes enter a mandatory class signature. */
public final class BeltEquipment {
  private record Backend(Class<?> api, Class<?> contract, Object accessory) {}

  private static final List<Backend> BACKENDS = new ArrayList<>();
  public static Predicate<LivingEntity> classAllowed = entity -> true;

  private BeltEquipment() {}

  public static void install() {
    register(
        "accessories",
        "io.wispforest.accessories.api.AccessoriesAPI",
        "io.wispforest.accessories.api.Accessory",
        "registerAccessory");
    register(
        "trinkets",
        "dev.emi.trinkets.api.TrinketsApi",
        "dev.emi.trinkets.api.Trinket",
        "registerTrinket");
  }

  private static void register(String mod, String apiName, String typeName, String method) {
    if (!FabricLoader.getInstance().isModLoaded(mod)) return;
    try {
      Class<?> api = Class.forName(apiName), contract = Class.forName(typeName);
      Object adapter =
          Proxy.newProxyInstance(
              contract.getClassLoader(),
              new Class<?>[] {contract},
              (self, m, args) -> {
                if (m.getDeclaringClass() == Object.class)
                  return switch (m.getName()) {
                    case "toString" -> "FantasyHub Kabuto belt (" + mod + ")";
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> null;
                  };
                if (m.getName().equals("canEquip")) {
                  LivingEntity entity;
                  String slot;
                  if (mod.equals("accessories")) {
                    Class<?> reference =
                        Class.forName("io.wispforest.accessories.api.slot.SlotReference");
                    entity = (LivingEntity) reference.getMethod("entity").invoke(args[1]);
                    slot = (String) reference.getMethod("slotName").invoke(args[1]);
                  } else {
                    entity = (LivingEntity) args[2];
                    slot =
                        (String)
                            Class.forName("dev.emi.trinkets.api.SlotReference")
                                .getMethod("getId")
                                .invoke(args[1]);
                  }
                  // Existing equipped stack is allowed when an API revalidates
                  // it. A second physical belt never contributes twice.
                  ItemStack existing = equipped(entity);
                  return (slot.equals("belt") || slot.startsWith("legs/belt/"))
                      && classAllowed.test(entity)
                      && (existing.isEmpty() || existing == args[0]);
                }
                if (m.isDefault())
                  return InvocationHandler.invokeDefault(
                      self, m, args == null ? new Object[0] : args);
                throw new UnsupportedOperationException(
                    "Unrecognized " + mod + " API method: " + m);
              });
      api.getMethod(method, Item.class, contract).invoke(null, Protocol.zecterBelt, adapter);
      BACKENDS.add(new Backend(api, contract, adapter));
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Cannot initialize installed " + mod + " belt API", failure);
    }
  }

  /** At most one belt, even if Accessories' Trinkets compatibility exposes it twice. */
  public static ItemStack equipped(LivingEntity entity) {
    try {
      for (Backend backend : BACKENDS) {
        if (backend.api().getName().contains("accessories")) {
          Class<?> capability =
              Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
          Object cap = capability.getMethod("get", LivingEntity.class).invoke(null, entity);
          if (cap == null) continue;
          for (Object row :
              (List<?>)
                  capability
                      .getMethod("getEquipped", Item.class)
                      .invoke(cap, Protocol.zecterBelt)) {
            Class<?> entry = Class.forName("io.wispforest.accessories.api.slot.SlotEntryReference");
            Object ref = entry.getMethod("reference").invoke(row);
            String slot =
                (String)
                    Class.forName("io.wispforest.accessories.api.slot.SlotReference")
                        .getMethod("slotName")
                        .invoke(ref);
            if (slot.equals("belt")) return (ItemStack) entry.getMethod("stack").invoke(row);
          }
        } else {
          Optional<?> component =
              (Optional<?>)
                  backend
                      .api()
                      .getMethod("getTrinketComponent", LivingEntity.class)
                      .invoke(null, entity);
          if (component.isEmpty()) continue;
          Class<?> type = Class.forName("dev.emi.trinkets.api.TrinketComponent");
          for (Object row :
              (List<?>)
                  type.getMethod("getEquipped", Item.class)
                      .invoke(component.get(), Protocol.zecterBelt)) {
            Pair<?, ?> pair = (Pair<?, ?>) row;
            String slot =
                (String)
                    Class.forName("dev.emi.trinkets.api.SlotReference")
                        .getMethod("getId")
                        .invoke(pair.getLeft());
            if (slot.startsWith("legs/belt/")) return (ItemStack) pair.getRight();
          }
        }
      }
      return ItemStack.EMPTY;
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Installed belt API failed", failure);
    }
  }
}
