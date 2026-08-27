package vn.svframe.svframelib.api.util;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/** Public compatibility facade retaining the SVFrameLib 1.7.1 API package. */
public class SmartGive {
    private final vn.svframe.svframelib.util.SmartGive delegate;

    public SmartGive(ServerPlayerEntity player) {
        this.delegate = new vn.svframe.svframelib.util.SmartGive(player);
    }

    public void give(ItemStack... items) {
        delegate.give(items);
    }

    public void give(List<ItemStack> items) {
        delegate.give(items);
    }
}
