package vn.svframe.svframemmo.mixin;

import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnchantmentScreenHandler.class)
public interface EnchantmentScreenHandlerAccessor {
    @Accessor("context")
    ScreenHandlerContext svframemmo$context();
}
