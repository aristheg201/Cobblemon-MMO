package vn.svframe.svframeitems.mixin;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets="net.minecraft.screen.GrindstoneScreenHandler$4")
public interface GrindstoneExperienceAccess {
    @Invoker("getExperience") int svframe$experience(ItemStack stack);
}
