package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vn.svframe.fantasyhub.protocol.Protocol;

/** Dedicated native armor textures avoid the source ItemsAdder leather atlas entirely. */
@Mixin(ArmorFeatureRenderer.class)
public abstract class ClassArmorMixin {
    @Unique private Identifier svframe$texture;
    @Inject(method="renderArmor",at=@At("HEAD"),cancellable=true)
    private void svframe$armor(MatrixStack matrices,VertexConsumerProvider vertices,LivingEntity entity,EquipmentSlot slot,int light,BipedEntityModel<?> model,CallbackInfo ci) {
        svframe$texture=null;var stack=entity.getEquippedStack(slot);
        if(stack.isOf(Protocol.dragonbornHat)){ci.cancel();return;}
        var data=stack.get(DataComponentTypes.CUSTOM_DATA);if(data==null)return;
        String name=data.copyNbt().getString("SVFRAME_ARMOR_TEXTURE");Identifier base=Identifier.tryParse(name);
        if(base!=null&&base.getNamespace().equals("fantasyhub")&&base.getPath().startsWith("dragonborn/"))
            svframe$texture=Identifier.of(base.getNamespace(),"textures/armor/"+base.getPath()+"_layer_"+(slot==EquipmentSlot.LEGS?2:1)+".png");
    }
    @ModifyArgs(method="renderArmor",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/feature/ArmorFeatureRenderer;renderArmorParts(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/model/BipedEntityModel;ILnet/minecraft/util/Identifier;)V"))
    private void svframe$texture(Args args){
        if(svframe$texture==null)return;Identifier original=args.get(5);
        if(original.getPath().endsWith("_overlay.png"))args.set(4,0);else{args.set(5,svframe$texture);args.set(4,0xffffffff);}
    }
}
