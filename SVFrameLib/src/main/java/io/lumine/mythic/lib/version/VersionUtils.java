package io.lumine.mythic.lib.version;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Native 1.21.1 utility bridge for version-sensitive inventory/attribute APIs. */
public final class VersionUtils {
    private static final Identifier NSK_TRICK = Identifier.of("svframelib", "empty_attribute");

    private VersionUtils() { }

    public static EntityAttributeModifier attrMod(Identifier key, double value, EntityAttributeModifier.Operation operation) {
        return new EntityAttributeModifier(key, value, operation);
    }

    public static EntityAttributeModifier emptyAttributeModifier() {
        return attrMod(NSK_TRICK, 0d, EntityAttributeModifier.Operation.ADD_VALUE);
    }

    public static boolean matches(EntityAttributeModifier modifier, Identifier key) {
        return modifier != null && modifier.idMatches(key);
    }

    public static EntityAttributeModifier getModifier(EntityAttributeInstance instance, Identifier key) {
        return instance == null || key == null ? null : instance.getModifier(key);
    }

    /**
     * Exact modern replacement for the legacy empty-attribute trick: an empty
     * attribute component with tooltip display disabled.
     */
    public static ItemStack addEmptyAttributeModifier(ItemStack item) {
        if (item != null && !item.isEmpty()) {
            item.set(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                    AttributeModifiersComponent.DEFAULT.withShowInTooltip(false));
        }
        return item;
    }

    public static VInventoryView getOpen(ServerPlayerEntity player) { return wrap(player); }
    public static VInventoryView getView(ServerPlayerEntity player) { return wrap(player); }
    public static String name(Identifier id) { return id == null ? "" : id.getPath().toUpperCase(java.util.Locale.ROOT); }

    private static VInventoryView wrap(ServerPlayerEntity player) {
        ScreenHandler handler = player.currentScreenHandler;
        return new VInventoryView() {
            public String getTitle() { return ""; }
            public ScreenHandlerType<?> getType() { return handler.getType(); }
            public Inventory getTopInventory() {
                return handler instanceof GenericContainerScreenHandler generic ? generic.getInventory() : new SimpleInventory(0);
            }
            public net.minecraft.entity.player.PlayerInventory getBottomInventory() { return player.getInventory(); }
            public void setCursor(ItemStack stack) { handler.setCursorStack(stack); }
            public ServerPlayerEntity getPlayer() { return player; }
            public void close() { player.closeHandledScreen(); }
        };
    }
}
