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
    @Unique private boolean fantasyhub$kabutoArms;
    @Inject(method="renderArmor",at=@At("HEAD"),cancellable=true)
    private void svframe$armor(MatrixStack matrices,VertexConsumerProvider vertices,LivingEntity entity,EquipmentSlot slot,int light,BipedEntityModel<?> model,CallbackInfo ci) {
        if(vn.svframe.fantasyhub.client.KabutoVisuals.state(entity.getId())!=null||vn.svframe.fantasyhub.client.RiderVisuals.state(entity.getId())!=null){ci.cancel();return;}
        svframe$texture=null;fantasyhub$kabutoArms=false;var stack=entity.getEquippedStack(slot);
        if(entity instanceof net.minecraft.entity.player.PlayerEntity&&!vn.svframe.fantasyhub.client.RiderArmorFeature.form(stack).isEmpty()){ci.cancel();return;}
        if(stack.isOf(Protocol.kabutoHat)||stack.isOf(Protocol.dragonbornHat)||vn.svframe.fantasyhub.client.HunterArmorFeature.hat(stack)||(entity instanceof net.minecraft.entity.player.PlayerEntity&&!vn.svframe.fantasyhub.client.HunterArmorFeature.collection(stack).isEmpty())){ci.cancel();return;}
        // Only this newly supplied three-piece Wolven set has narrower arms.
        // Reset all axes on entry because armor models are reused across players.
        model.leftArm.xScale=model.rightArm.xScale=model.leftArm.zScale=model.rightArm.zScale=1;
        var data=stack.get(DataComponentTypes.CUSTOM_DATA);if(data==null)return;
        if(entity instanceof net.minecraft.entity.player.PlayerEntity&&data.copyNbt().getString("SVFRAME_ARMOR_TEXTURE").equals("fantasyhub:additions/kabuto")){ci.cancel();return;}
        if(slot==EquipmentSlot.CHEST&&data.copyNbt().getString("SVFRAME_ARMOR_TEXTURE").equals("fantasyhub:additions/grandmaster_wolven"))model.leftArm.xScale=model.rightArm.xScale=model.leftArm.zScale=model.rightArm.zScale=.9f;
        String name=data.copyNbt().getString("SVFRAME_ARMOR_TEXTURE");fantasyhub$kabutoArms=slot==EquipmentSlot.CHEST&&name.equals("fantasyhub:additions/kabuto");Identifier base=Identifier.tryParse(name);
        if(base!=null&&base.getNamespace().equals("fantasyhub")&&(base.getPath().startsWith("additions/")||base.getPath().startsWith("dragonborn/")||base.getPath().equals("hunter/halfmoon")))
            svframe$texture=Identifier.of(base.getNamespace(),"textures/armor/"+base.getPath()+"_layer_"+(slot==EquipmentSlot.LEGS?2:1)+".png");
    }
    @Inject(method="renderArmor",at=@At("RETURN"))
    private void fantasyhub$resetArmScale(MatrixStack matrices,VertexConsumerProvider vertices,LivingEntity entity,EquipmentSlot slot,int light,BipedEntityModel<?> model,CallbackInfo ci){model.leftArm.xScale=model.rightArm.xScale=model.leftArm.zScale=model.rightArm.zScale=1;if(fantasyhub$kabutoArms)model.leftArm.visible=model.rightArm.visible=true;}
    @ModifyArgs(method="renderArmor",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/feature/ArmorFeatureRenderer;renderArmorParts(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/model/BipedEntityModel;ILnet/minecraft/util/Identifier;)V"))
    private void svframe$texture(Args args){
        if(fantasyhub$kabutoArms){BipedEntityModel<?> armor=args.get(3);armor.leftArm.visible=armor.rightArm.visible=false;}
        if(svframe$texture==null)return;Identifier original=args.get(5);
        if(original.getPath().endsWith("_overlay.png"))args.set(4,0);else{args.set(5,svframe$texture);args.set(4,0xffffffff);}
    }
}
