package io.lumine.mythic.lib.api.crafting.uimanager;

import io.lumine.mythic.lib.api.util.ui.FriendlyFeedbackProvider;
import net.minecraft.item.ItemStack;

@FunctionalInterface
public interface UIFilterCountermatch {
    boolean preventsMatching(ItemStack item, FriendlyFeedbackProvider feedback);
}
