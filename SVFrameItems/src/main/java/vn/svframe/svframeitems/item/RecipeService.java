package vn.svframe.svframeitems.item;

import net.minecraft.entity.player.PlayerInventory;
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
    public boolean canCraft(PlayerInventory inventory,String recipeId){RecipeDefinition recipe=registry.recipe(recipeId);return recipe!=null&&plan(inventory,recipe)!=null;}
    public Result craft(ServerPlayerEntity player,String recipeId){RecipeDefinition recipe=registry.recipe(recipeId);if(recipe==null)return new Result(Status.UNKNOWN_RECIPE,ItemStack.EMPTY);List<Consumption> plan=plan(player.getInventory(),recipe);if(plan==null)return new Result(Status.MISSING_INGREDIENTS,ItemStack.EMPTY);for(Consumption c:plan)player.getInventory().getStack(c.slot()).decrement(c.count());ItemStack output=generator.generate(recipe.outputItemId(),recipe.outputLevel());output.setCount(recipe.outputAmount());return new Result(Status.SUCCESS,output);}
    private static List<Consumption> plan(PlayerInventory inventory,RecipeDefinition recipe){List<Consumption> all=new ArrayList<>();Map<Integer,Integer> reserved=new HashMap<>();for(RecipeDefinition.Ingredient ingredient:recipe.ingredients()){int remaining=ingredient.count();for(int slot=0;slot<inventory.size()&&remaining>0;slot++){ItemStack stack=inventory.getStack(slot);if(stack.isEmpty()||!matches(stack,ingredient))continue;int available=stack.getCount()-reserved.getOrDefault(slot,0);if(available<=0)continue;int take=Math.min(available,remaining);reserved.merge(slot,take,Integer::sum);remaining-=take;}if(remaining>0)return null;}reserved.forEach((slot,count)->all.add(new Consumption(slot,count)));return all;}
    private static boolean matches(ItemStack stack,RecipeDefinition.Ingredient ingredient){if(ingredient.kind()==RecipeDefinition.IngredientKind.SVFRAME_ITEM)return ItemCodec.read(stack).map(item->item.definitionId().equals(ingredient.id())).orElse(false);return Registries.ITEM.getId(stack.getItem()).toString().equals(ingredient.id());}
    private record Consumption(int slot,int count){}
}
