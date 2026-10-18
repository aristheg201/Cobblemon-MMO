package vn.svframe.fantasyhub.core;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import vn.svframe.fantasyhub.protocol.Protocol;

/** One owned, temporary native display. Crash-persisted summons are retired when loaded. */
final class RiderSummons {
  private static final String TAG = "fantasyhub_rider_summon";
  private static final Set<UUID> OWNED = new HashSet<>();

  static void install() {
    ServerEntityEvents.ENTITY_LOAD.register(
        (entity, world) -> {
          if (entity.getCommandTags().contains(TAG) && !OWNED.contains(entity.getUuid()))
            entity.discard();
        });
  }

  static DisplayEntity.ItemDisplayEntity magnugiga(ServerPlayerEntity owner, Vec3d position) {
    return spawn(owner, position, "magnugiga", 1.6f);
  }

  static DisplayEntity.ItemDisplayEntity spawn(ServerPlayerEntity owner, Vec3d position, String model, float scale) {
    var stack = new ItemStack(Protocol.visualBone);
    NbtComponent.set(
        DataComponentTypes.CUSTOM_DATA,
        stack,
        n -> n.putString("SVFRAME_ITEM_MODEL", "fantasyhub:item/riders/" + model));
    var display =
        new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, owner.getServerWorld());
    var tag = new NbtCompound();
    tag.put("item", stack.encode(owner.getRegistryManager()));
    tag.putString("item_display", "fixed");
    var transform = new NbtCompound();
    transform.put("translation", vector(0, 0, 0));
    transform.put("scale", vector(scale, scale, scale));
    transform.put("left_rotation", vector(0, 0, 0, 1));
    transform.put("right_rotation", vector(0, 0, 0, 1));
    tag.put("transformation", transform);
    display.readNbt(tag);
    display.setPosition(position);
    display.setYaw(owner.getYaw());
    display.addCommandTag(TAG);
    OWNED.add(display.getUuid());
    if (!owner.getServerWorld().spawnEntity(display)) {
      OWNED.remove(display.getUuid());
      return null;
    }
    return display;
  }

  static void remove(DisplayEntity.ItemDisplayEntity summon) {
    if (summon == null) return;
    OWNED.remove(summon.getUuid());
    summon.discard();
  }

  private static NbtList vector(float... values) {
    var result = new NbtList();
    for (float value : values) result.add(NbtFloat.of(value));
    return result;
  }
}
