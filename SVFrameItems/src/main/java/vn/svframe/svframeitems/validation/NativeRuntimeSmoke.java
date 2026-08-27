package vn.svframe.svframeitems.validation;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
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

        var commandRoot=SVFrameItems.server().getCommandManager().getDispatcher().getRoot().getChild("svframeitems");
        require(commandRoot!=null,"command root registered");
        var levelZero=SVFrameItems.server().getCommandSource().withLevel(0);
        require(commandRoot.getChild("upgrade")!=null&&commandRoot.getChild("upgrade").canUse(levelZero),"player upgrade command accessible without operator level");
        require(commandRoot.getChild("socket")!=null&&commandRoot.getChild("socket").canUse(levelZero),"player socket command accessible without operator level");
        require(commandRoot.getChild("craft")!=null&&commandRoot.getChild("craft").canUse(levelZero),"player craft command accessible without operator level");
        require(commandRoot.getChild("inspect")!=null&&commandRoot.getChild("inspect").canUse(levelZero),"player inspect command accessible without operator level");
        require(commandRoot.getChild("items")!=null&&commandRoot.getChild("items").canUse(levelZero),"player discovery command accessible without operator level");
        require(commandRoot.getChild("recipes")!=null&&commandRoot.getChild("recipes").canUse(levelZero),"player recipe discovery command accessible without operator level");
        require(commandRoot.getChild("reload")!=null&&!commandRoot.getChild("reload").canUse(levelZero),"reload remains administrator-only");
        require(commandRoot.getChild("give")!=null&&!commandRoot.getChild("give").canUse(levelZero),"give remains administrator-only");
        require(commandRoot.getChild("loot")!=null&&!commandRoot.getChild("loot").canUse(levelZero),"loot remains administrator-only");

        ItemStack blade=SVFrameItems.generator().generate("trailblazer_blade",new ItemGenerator.GenerationContext(10,7));
        ItemInstance original=ItemCodec.read(blade).orElseThrow();
        LoreComponent initialLore=blade.get(DataComponentTypes.LORE);
        require(initialLore!=null&&initialLore.lines().stream().anyMatch(line->line.getString().startsWith("Rarity: ")),"player-facing rarity lore");
        require(initialLore.lines().stream().anyMatch(line->line.getString().equals("Level: 10")),"player-facing level lore");
        require(initialLore.lines().stream().anyMatch(line->line.getString().equals("Stats")),"player-facing stat lore");
        require(initialLore.lines().stream().anyMatch(line->line.getString().equals("Sockets")),"player-facing socket lore");
        require(original.equals(ItemCodec.read(blade.copy()).orElseThrow()),"copy persistence");
        require(blade.getMaxCount()==1,"weapon max stack");
        require(ItemCodec.setMetadata(blade,"integration:owner","alpha"),"metadata write");
        blade.set(DataComponentTypes.DAMAGE,3);
        UpgradeService.Result upgraded=SVFrameItems.upgrades().attempt(blade,new SplittableRandom(1));
        require(upgraded.success(),"upgrade success");
        LoreComponent upgradedLore=upgraded.item().get(DataComponentTypes.LORE);
        require(upgradedLore!=null&&upgradedLore.lines().stream().anyMatch(line->line.getString().equals("Upgrade: +1")),"upgrade lore refresh");
        require(Integer.valueOf(3).equals(upgraded.item().get(DataComponentTypes.DAMAGE)),"foreign data component preserved by upgrade");
        require("alpha".equals(ItemCodec.metadata(upgraded.item(),"integration:owner").orElse(null)),"metadata preserved by upgrade");
        ItemStack stackedTarget=blade.copy();stackedTarget.setCount(2);
        require(SVFrameItems.upgrades().attempt(stackedTarget,new SplittableRandom(1)).status()==UpgradeService.Status.TARGET_STACKED,"stacked upgrade target rejected");

        ItemStack gem=SVFrameItems.generator().generate("ruby_gem",new ItemGenerator.GenerationContext(10,11));
        require(gem.getMaxCount()==64,"gem max stack");
        require(ItemCodec.setMetadata(gem,"integration:origin","external"),"gem metadata write");
        require(SVFrameItems.sockets().insert(stackedTarget,gem).status()==SocketService.Status.TARGET_STACKED,"stacked socket target rejected");
        SocketService.InsertResult inserted=SVFrameItems.sockets().insert(upgraded.item(),gem);
        require(inserted.success(),"socket insert");
        LoreComponent socketLore=inserted.target().get(DataComponentTypes.LORE);
        require(socketLore!=null&&socketLore.lines().stream().anyMatch(line->line.getString().contains("ruby_gem")),"socket lore refresh");
        require(Integer.valueOf(3).equals(inserted.target().get(DataComponentTypes.DAMAGE)),"foreign data component preserved by socket");
        SocketService.UnsocketResult removed=SVFrameItems.sockets().unsocket(inserted.target(),inserted.socketIndex());
        require(removed.success(),"unsocket");
        require("external".equals(ItemCodec.metadata(removed.gem(),"integration:origin").orElse(null)),"embedded gem metadata roundtrip");
        require("alpha".equals(ItemCodec.metadata(removed.target(),"integration:owner").orElse(null)),"target metadata roundtrip");

        SimpleInventory inventory=new SimpleInventory(2);inventory.setStack(0,new ItemStack(Items.IRON_INGOT,12));inventory.setStack(1,new ItemStack(Items.DIAMOND,2));
        require(SVFrameItems.recipes().canCraft(inventory,"trailblazer_blade"),"recipe can craft");
        RecipeService.Result crafted=SVFrameItems.recipes().craft(inventory,"trailblazer_blade");
        require(crafted.success()&&ItemCodec.read(crafted.output()).map(value->value.definitionId().equals("trailblazer_blade")).orElse(false),"recipe output identity");
        require(crafted.output().get(DataComponentTypes.LORE)!=null,"crafted output player-facing lore");
        require(inventory.getStack(0).isEmpty()&&inventory.getStack(1).isEmpty(),"recipe consumption");

        var loot=SVFrameItems.loot().roll("starter_drops",99,new SplittableRandom(3));
        require(!loot.isEmpty(),"loot generated");
        for(ItemStack stack:loot){
            require(ItemCodec.read(stack).map(value->value.itemLevel()==20).orElse(false),"loot level context");
            require(stack.get(DataComponentTypes.LORE)!=null,"loot output player-facing lore");
        }
        System.out.println("SVFRAMEITEMS_RUNTIME_SMOKE=PASS item="+original.definitionId()+" lootStacks="+loot.size()+" playerFacing=PASS");
    }
    private static void require(boolean value,String label){if(!value)throw new IllegalStateException("SVFrameItems runtime smoke failed: "+label);}
}
