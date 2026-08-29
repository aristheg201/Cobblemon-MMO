package vn.svframe.svframemmo.gui;

import vn.svframe.svframelib.gui.Navigator;
import vn.svframe.svframelib.gui.PluginInventory;
import vn.svframe.svframelib.gui.editable.GeneratedInventory;
import vn.svframe.svframelib.gui.editable.item.InventoryItem;
import vn.svframe.svframelib.gui.editable.item.PhysicalItem;
import vn.svframe.svframelib.gui.editable.item.builtin.GoBackItem;
import vn.svframe.svframelib.gui.editable.placeholder.Placeholders;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerClassChangeEvent;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.PlayerClass;
import vn.svframe.svframemmo.api.player.profess.SavedClassState;

import java.util.Map;

public final class ClassConfirmation extends AbstractClassSelect {
    private final PlayerClass playerClass;

    public ClassConfirmation(PlayerClass playerClass, boolean isDefault) {
        super("class-confirm-" + (isDefault ? "default" : GuiSupport.normalizeId(playerClass.getId())));
        this.playerClass = playerClass;
    }

    @Override public InventoryItem<?> resolveItem(String function, Map<String, Object> config) {
        if (function.equalsIgnoreCase("yes")) return new YesItem(config);
        if (function.equalsIgnoreCase("back")) return new GoBackItem<>(config);
        return null;
    }

    public GeneratedInventory newInventory(AbstractClassGeneratedInventory prev, boolean forceSetClass) {
        return new ClassConfirmationInventory(prev.getNavigator(), prev.playerData, playerClass, forceSetClass);
    }

    private final class UnlockedItem extends PhysicalItem<ClassConfirmationInventory> {
        UnlockedItem(Map<String, ?> config) { super(config); }

        @Override public Placeholders getPlaceholders(ClassConfirmationInventory inv, int n) {
            SavedClassState state = inv.playerData.getClassSlots().get(inv.profess.getId());
            if (state == null || inv.forceSetClass) state = inv.playerData.captureClassState();
            long next = inv.profess.getExpCurve().getExperience(inv.playerData, state.level());
            return GuiSupport.placeholders(
                    "percent", GuiSupport.percent(state.experience(), next),
                    "progress", GuiSupport.progress(state.experience(), next),
                    "class", inv.profess.getName(),
                    "unlocked_skills", state.skills().size(),
                    "class_skills", inv.profess.getSkills().size(),
                    "next_level", next,
                    "level", state.level(),
                    "exp", state.experience(),
                    "skill_points", state.skillPoints());
        }
    }

    private final class YesItem extends InventoryItem<ClassConfirmationInventory> {
        private final InventoryItem<ClassConfirmationInventory> unlocked;
        private final InventoryItem<ClassConfirmationInventory> locked;

        YesItem(Map<String, ?> config) {
            super(config);
            Map<String, Object> unlockedConfig = GuiSupport.map(config.get("unlocked"));
            Map<String, Object> lockedConfig = GuiSupport.map(config.get("locked"));
            if (unlockedConfig.isEmpty() || lockedConfig.isEmpty()) throw new IllegalArgumentException("Class confirmation requires unlocked and locked item configs");
            unlocked = new UnlockedItem(unlockedConfig);
            locked = new PhysicalItem<>(lockedConfig) {
                @Override public Placeholders getPlaceholders(ClassConfirmationInventory inv, int n) {
                    return GuiSupport.placeholders("class", inv.profess.getName());
                }
            };
        }

        @Override public net.minecraft.item.ItemStack getDisplayedItem(ClassConfirmationInventory inv, int n) {
            return inv.playerData.getClassSlots().containsKey(inv.profess.getId()) ? unlocked.getDisplayedItem(inv, n) : locked.getDisplayedItem(inv, n);
        }

        @Override public void onClick(ClassConfirmationInventory inv, PluginInventory.Click click) {
            if (!inv.forceSetClass && inv.playerData.getClassPoints() < 1) {
                GuiSupport.action(inv.getPlayer(), "&cYou do not have any class points.");
                return;
            }
            if (!inv.playerData.changeClass(inv.profess, PlayerClassChangeEvent.Reason.GUI)) return;
            inv.playerData.giveClassPoints(-1);
            if (inv.forceSetClass) SVFrameMMO.classSelection().markChosen(inv.playerData);
            inv.getNavigator().unblockClosing();
            GuiSupport.action(inv.getPlayer(), "&aSelected class " + inv.profess.getName());
            inv.getPlayer().closeHandledScreen();
        }
    }

    public final class ClassConfirmationInventory extends AbstractClassGeneratedInventory {
        private final PlayerClass profess;
        private final boolean forceSetClass;

        ClassConfirmationInventory(Navigator navigator, PlayerData data, PlayerClass profess, boolean forceSetClass) {
            super(navigator, data);
            this.profess = profess;
            this.forceSetClass = forceSetClass;
        }

        @Override public String getRawName() { return guiName.replace("{class}", profess.getName()); }
    }
}
