package vn.svframe.fantasyhub.protocol.mixin;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.gen.Accessor;import net.minecraft.entity.attribute.ClampedEntityAttribute;
@Mixin(ClampedEntityAttribute.class)
public interface HealthAttributeAccess {@Mutable @Accessor("maxValue") void svframe$maximum(double value);}
