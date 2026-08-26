package io.lumine.mythic.lib.api.crafting.uimanager;

import io.lumine.mythic.lib.api.util.ui.FriendlyFeedbackProvider;
import net.minecraft.item.ItemStack;

/**
 * Native Fabric counterpart of MythicLib's crafting UI countermatch hook.
 * Implementations may veto an item/filter match and optionally report why.
 */
@FunctionalInterface
public interface UIFilterCountermatch {
    boolean preventsMatching(ItemStack item, FriendlyFeedbackProvider feedback);
}
