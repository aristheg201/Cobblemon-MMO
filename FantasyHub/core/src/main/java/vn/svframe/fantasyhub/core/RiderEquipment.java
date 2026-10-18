package vn.svframe.fantasyhub.core;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.text.Text;

/** Uses SVFrameItems as the single owner of armor bonuses and enchantment policy. */
final class RiderEquipment {
  static void install() {
    vn.svframe.svframeitems.SVFrameItems.generator()
        .registerMechanic(
            (stack, definition, instance, phase) -> {
              if (java.util.List.of("agito_","zolda_","ryuki_","knight_","decade_").stream().anyMatch(definition.id()::startsWith))
                stack.set(
                    DataComponentTypes.CUSTOM_NAME,
                    Text.translatable("item.fantasyhub." + definition.id()));
            });
  }
}
