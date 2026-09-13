package dev.aristheg.alphaencounter.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.aristheg.alphaencounter.integration.AlphaLootGuard;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Covers Cobblemon's direct held-item death drop path.
 *
 * PokemonServerDelegate#doDeathDrops drops a held item through Entity#dropItem
 * before processing the normal DropTable. That path never reaches
 * LootDroppedEvent or ItemDropEntry, so it must be guarded separately.
 */
@Mixin(Entity.class)
public abstract class EntityDropItemMixin {
    @Inject(
        method = "dropItem(Lnet/minecraft/item/ItemConvertible;)Lnet/minecraft/entity/ItemEntity;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void alphaEncounter$filterManagedPokemonDirectItemDrop(
        ItemConvertible item,
        CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (!((Object) this instanceof PokemonEntity pokemon) || item == null) return;
        Identifier itemId = Registries.ITEM.getId(item.asItem());
        if (itemId != null && AlphaLootGuard.shouldBlockItemDrop(pokemon, itemId)) {
            cir.setReturnValue(null);
        }
    }
}
