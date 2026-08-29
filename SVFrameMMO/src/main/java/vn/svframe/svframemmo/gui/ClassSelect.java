package vn.svframe.svframemmo.gui;

import vn.svframe.svframelib.gui.Navigator;
import vn.svframe.svframelib.gui.PluginInventory;
import vn.svframe.svframelib.gui.editable.item.InventoryItem;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.ClassOption;
import vn.svframe.svframemmo.api.player.profess.PlayerClass;

import java.util.Map;

public final class ClassSelect extends AbstractClassSelect {
    public ClassSelect() { super("class-select"); }

    @Override public InventoryItem<?> resolveItem(String function, Map<String, Object> config) {
        if (function.startsWith("class-")) return new ClassItem(config);
        return null;
    }

    public ProfessSelectionInventory newInventory(PlayerData data) { return new ProfessSelectionInventory(data, false); }
    public ProfessSelectionInventory newInventory(PlayerData data, boolean forced) { return new ProfessSelectionInventory(data, forced); }

    public final class ClassItem extends AbstractClassItem<ProfessSelectionInventory> {
        ClassItem(Map<String, ?> config) { super(config, "class-".length()); }

        @Override public void onClick(ProfessSelectionInventory inv, PluginInventory.Click click) {
            if (!inv.forced && inv.playerData.getClassPoints() < 1) {
                GuiSupport.action(inv.getPlayer(), "&cYou do not have any class points.");
                return;
            }
            if (playerClass.hasOption(ClassOption.NEEDS_PERMISSION) && !hasClassPermission(inv, playerClass)) {
                GuiSupport.action(inv.getPlayer(), "&cYou do not have permission to choose " + playerClass.getName());
                return;
            }
            if (playerClass.equals(inv.playerData.getProfess())) {
                GuiSupport.action(inv.getPlayer(), "&cYou are already " + playerClass.getName());
                return;
            }
            if (!inv.forced) inv.getNavigator().unblockClosing();
            PlayerClass deepest = findDeepestSubclass(inv.playerData, playerClass);
            SVFrameMMO.gui().confirmation(deepest).newInventory(inv, inv.forced).open();
        }
    }

    public final class ProfessSelectionInventory extends AbstractClassGeneratedInventory {
        private final boolean forced;
        ProfessSelectionInventory(PlayerData data, boolean forced) {
            super(new Navigator(data.getMMOPlayerData()), data);
            this.forced = forced;
            if (forced) getNavigator().blockClosing();
        }
    }

    private static boolean hasClassPermission(ProfessSelectionInventory inv, PlayerClass playerClass) {
        String id = playerClass.getId().toLowerCase(java.util.Locale.ROOT);
        return SVFrameMMO.permissions().has(inv.getPlayer(), "svframemmo.class." + id);
    }

    private static PlayerClass findDeepestSubclass(PlayerData player, PlayerClass root) {
        for (String checkedName : player.getClassSlots().keySet()) {
            PlayerClass checked = SVFrameMMO.classes().get(checkedName);
            if (checked != null && root.hasSubclass(checked)) return checked;
        }
        return root;
    }
}
