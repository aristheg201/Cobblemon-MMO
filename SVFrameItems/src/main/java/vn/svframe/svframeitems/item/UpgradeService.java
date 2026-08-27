package vn.svframe.svframeitems.item;

import net.minecraft.item.ItemStack;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class UpgradeService {
    public enum Status { SUCCESS, FAILED, DESTROYED, NOT_AN_ITEM, NOT_UPGRADABLE, MAX_LEVEL, INVALID_DEFINITION }
    public record Result(Status status, ItemStack item, int oldLevel, int newLevel, double successChance) { public boolean success(){return status==Status.SUCCESS;} }
    private final SVFrameItemsRegistry registry; private final ItemGenerator generator;
    public UpgradeService(SVFrameItemsRegistry registry, ItemGenerator generator) { this.registry=Objects.requireNonNull(registry); this.generator=Objects.requireNonNull(generator); }
    public Result attempt(ItemStack stack) { return attempt(stack, ThreadLocalRandom.current()); }
    public Result attempt(ItemStack stack, RandomGenerator random) {
        Optional<ItemInstance> read = ItemCodec.read(stack); if (read.isEmpty()) return new Result(Status.NOT_AN_ITEM, stack.copy(), 0, 0, 0);
        ItemInstance instance=read.get(); ItemDefinition definition=registry.item(instance.definitionId());
        if(definition==null)return new Result(Status.INVALID_DEFINITION,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),0);
        if(definition.upgradeTemplateId()==null)return new Result(Status.NOT_UPGRADABLE,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),0);
        UpgradeTemplate template=registry.upgrade(definition.upgradeTemplateId()); if(template==null)return new Result(Status.INVALID_DEFINITION,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),0);
        if(instance.upgradeLevel()>=template.maxLevel())return new Result(Status.MAX_LEVEL,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),0);
        double chance=template.chanceForNextLevel(instance.upgradeLevel());
        if(random.nextDouble()<chance){int next=instance.upgradeLevel()+1;return new Result(Status.SUCCESS,generator.rebuild(instance.withUpgradeLevel(next)),instance.upgradeLevel(),next,chance);}
        if(template.destroyOnFail())return new Result(Status.DESTROYED,ItemStack.EMPTY,instance.upgradeLevel(),instance.upgradeLevel(),chance);
        return new Result(Status.FAILED,stack.copy(),instance.upgradeLevel(),instance.upgradeLevel(),chance);
    }
    public double statMultiplier(ItemInstance instance) {
        ItemDefinition definition=registry.item(instance.definitionId()); if(definition==null||definition.upgradeTemplateId()==null)return 0d;
        UpgradeTemplate template=registry.upgrade(definition.upgradeTemplateId()); return template==null?0d:template.statMultiplierPerLevel();
    }
    public double statMultiplier(EmbeddedGem gem) { return statMultiplier(gem.toItemInstance()); }
}
