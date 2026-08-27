package vn.svframe.svframeitems.validation;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframeitems.item.*;
import vn.svframe.svframeitems.model.ItemInstance;

import java.util.SplittableRandom;

/** Actual Minecraft/Fabric runtime verification, enabled only by the CI environment flag. */
public final class NativeRuntimeSmoke {
    private NativeRuntimeSmoke() {}
    public static void runIfRequested() {
        if (!"1".equals(System.getenv("SVFRAMEITEMS_CI_RUNTIME_SMOKE"))) return;
        ItemStack blade=SVFrameItems.generator().generate("trailblazer_blade",new ItemGenerator.GenerationContext(10,7));
        ItemInstance original=ItemCodec.read(blade).orElseThrow();
        require(original.equals(ItemCodec.read(blade.copy()).orElseThrow()),"copy persistence");
        require(blade.getMaxCount()==1,"weapon max stack");
        require(ItemCodec.setMetadata(blade,"integration:owner","alpha"),"metadata write");
        blade.set(DataComponentTypes.DAMAGE,3);
        UpgradeService.Result upgraded=SVFrameItems.upgrades().attempt(blade,new SplittableRandom(1));
        require(upgraded.success(),"upgrade success");
        require(Integer.valueOf(3).equals(upgraded.item().get(DataComponentTypes.DAMAGE)),"foreign data component preserved by upgrade");
        require("alpha".equals(ItemCodec.metadata(upgraded.item(),"integration:owner").orElse(null)),"metadata preserved by upgrade");

        ItemStack gem=SVFrameItems.generator().generate("ruby_gem",new ItemGenerator.GenerationContext(10,11));
        require(gem.getMaxCount()==64,"gem max stack");
        require(ItemCodec.setMetadata(gem,"integration:origin","external"),"gem metadata write");
        SocketService.InsertResult inserted=SVFrameItems.sockets().insert(upgraded.item(),gem);
        require(inserted.success(),"socket insert");
        require(Integer.valueOf(3).equals(inserted.target().get(DataComponentTypes.DAMAGE)),"foreign data component preserved by socket");
        SocketService.UnsocketResult removed=SVFrameItems.sockets().unsocket(inserted.target(),inserted.socketIndex());
        require(removed.success(),"unsocket");
        require("external".equals(ItemCodec.metadata(removed.gem(),"integration:origin").orElse(null)),"embedded gem metadata roundtrip");
        require("alpha".equals(ItemCodec.metadata(removed.target(),"integration:owner").orElse(null)),"target metadata roundtrip");

        SimpleInventory inventory=new SimpleInventory(2);inventory.setStack(0,new ItemStack(Items.IRON_INGOT,12));inventory.setStack(1,new ItemStack(Items.DIAMOND,2));
        require(SVFrameItems.recipes().canCraft(inventory,"trailblazer_blade"),"recipe can craft");
        RecipeService.Result crafted=SVFrameItems.recipes().craft(inventory,"trailblazer_blade");
        require(crafted.success()&&ItemCodec.read(crafted.output()).map(value->value.definitionId().equals("trailblazer_blade")).orElse(false),"recipe output identity");
        require(inventory.getStack(0).isEmpty()&&inventory.getStack(1).isEmpty(),"recipe consumption");

        var loot=SVFrameItems.loot().roll("starter_drops",99,new SplittableRandom(3));
        require(!loot.isEmpty(),"loot generated");
        for(ItemStack stack:loot)require(ItemCodec.read(stack).map(value->value.itemLevel()==20).orElse(false),"loot level context");
        System.out.println("SVFRAMEITEMS_RUNTIME_SMOKE=PASS item="+original.definitionId()+" lootStacks="+loot.size());
    }
    private static void require(boolean value,String label){if(!value)throw new IllegalStateException("SVFrameItems runtime smoke failed: "+label);}
}
