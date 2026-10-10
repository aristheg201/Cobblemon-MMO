package vn.svframe.svframelib.fabric.mixin;

import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayEntity.class)
public interface DisplayEntityAccessor {
    @Invoker("setBillboardMode")
    void svframelib$setBillboardMode(DisplayEntity.BillboardMode mode);
    @Invoker("setTransformation")
    void svframelib$setTransformation(net.minecraft.util.math.AffineTransformation transformation);
    @Invoker("setInterpolationDuration")
    void svframelib$setInterpolationDuration(int ticks);
    @Invoker("setStartInterpolation")
    void svframelib$setStartInterpolation(int ticks);
    @Invoker("setTeleportDuration")
    void svframelib$setTeleportDuration(int ticks);
}
