package vn.svframe.svframemmo.cobblemon.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.config.IntegrationConfig;
import vn.svframe.svframemmo.cobblemon.fusion.FusionTier;

import java.util.Map;
import java.util.Optional;

/** Exact Potara match: vanilla item id + CustomModelData. */
public final class PotaraTierResolver {
    public Optional<ResolvedPotara> held(ServerPlayerEntity player) {
        for (Hand hand : Hand.values()) {
            ItemStack stack = player.getStackInHand(hand);
            FusionTier tier = resolve(stack);
            if (tier != null) return Optional.of(new ResolvedPotara(hand, stack, tier));
        }
        return Optional.empty();
    }

    public FusionTier resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Integer customModelData = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (customModelData == null) return null;
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        for (Map.Entry<FusionTier, IntegrationConfig.PotaraItem> entry : SVFrameMMOCobblemon.config().potara.byTier().entrySet()) {
            IntegrationConfig.PotaraItem configured = entry.getValue();
            if (configured.customModelData == customModelData && configured.itemId().equals(itemId)) return entry.getKey();
        }
        return null;
    }

    public record ResolvedPotara(Hand hand, ItemStack stack, FusionTier tier) { }
}
