package vn.svframe.fantasyhub.client.mixin;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.entity.LivingEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ItemRenderer.class)
public abstract class ClassItemModelMixin {
    @Inject(method="getModel",at=@At("RETURN"),cancellable=true)
    private void svframe$model(ItemStack stack,World world,LivingEntity entity,int seed,CallbackInfoReturnable<BakedModel> ci){
        var nbt=stack.get(DataComponentTypes.CUSTOM_DATA);if(nbt==null)return;Identifier id=Identifier.tryParse(nbt.copyNbt().getString("SVFRAME_ITEM_MODEL"));
        if(id!=null&&id.getNamespace().equals("fantasyhub")&&(id.getPath().startsWith("item/riders/")||id.getPath().startsWith("item/additions/")||id.getPath().startsWith("item/dragonborn/")||id.getPath().startsWith("item/hunter/")))ci.setReturnValue(MinecraftClient.getInstance().getBakedModelManager().getModel(id));
    }
}
