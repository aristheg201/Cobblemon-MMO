package vn.svframe.svframeitems.item;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;
import java.util.*;

public final class RecipeService {
    public enum Status { SUCCESS, UNKNOWN_RECIPE, MISSING_INGREDIENTS }
    public record Result(Status status, ItemStack output) { public boolean success(){return status==Status.SUCCESS;} }
    private final SVFrameItemsRegistry registry; private final ItemGenerator generator;
    public RecipeService(SVFrameItemsRegistry registry,ItemGenerator generator){this.registry=Objects.requireNonNull(registry);this.generator=Objects.requireNonNull(generator);}
    public boolean canCraft(Inventory inventory,String recipeId){RecipeDefinition recipe=registry.recipe(recipeId);return recipe!=null&&plan(inventory,recipe).isPresent();}
    public Result craft(ServerPlayerEntity player,String recipeId){return craft(player.getInventory(),recipeId);}
    public Result craft(Inventory inventory,String recipeId){RecipeDefinition recipe=registry.recipe(recipeId);if(recipe==null)return new Result(Status.UNKNOWN_RECIPE,ItemStack.EMPTY);Optional<List<RecipePlanner.Consumption>> plan=plan(inventory,recipe);if(plan.isEmpty())return new Result(Status.MISSING_INGREDIENTS,ItemStack.EMPTY);for(RecipePlanner.Consumption c:plan.get())inventory.getStack(c.slot()).decrement(c.count());ItemStack output=generator.generate(recipe.outputItemId(),recipe.outputLevel());output.setCount(recipe.outputAmount());inventory.markDirty();return new Result(Status.SUCCESS,output);}
    private static Optional<List<RecipePlanner.Consumption>> plan(Inventory inventory,RecipeDefinition recipe){List<RecipePlanner.StackView> slots=new ArrayList<>(inventory.size());for(int slot=0;slot<inventory.size();slot++){ItemStack stack=inventory.getStack(slot);if(stack.isEmpty()){slots.add(new RecipePlanner.StackView("",null,0));continue;}String vanilla=Registries.ITEM.getId(stack.getItem()).toString();String svframe=ItemCodec.read(stack).map(ItemInstance::definitionId).orElse(null);slots.add(new RecipePlanner.StackView(vanilla,svframe,stack.getCount()));}return RecipePlanner.plan(slots,recipe);}
}
