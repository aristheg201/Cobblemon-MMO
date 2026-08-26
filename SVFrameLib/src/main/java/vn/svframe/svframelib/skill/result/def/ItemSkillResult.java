package vn.svframe.svframelib.skill.result.def;
import vn.svframe.svframelib.skill.SkillMetadata; import net.minecraft.item.Item; import net.minecraft.item.ItemStack;
public class ItemSkillResult extends VectorSkillResult { private final ItemStack item; public ItemSkillResult(SkillMetadata m){super(m);this.item=ItemStack.EMPTY;} public ItemSkillResult(SkillMetadata m,Item item){super(m);this.item=item==null?ItemStack.EMPTY:new ItemStack(item);} public ItemStack getItem(){return item.copy();} public boolean isSuccessful(){return !item.isEmpty()||super.isSuccessful();} }
