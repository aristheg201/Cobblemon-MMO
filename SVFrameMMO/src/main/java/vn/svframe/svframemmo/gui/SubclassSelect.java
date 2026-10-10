package vn.svframe.svframemmo.gui;

import vn.svframe.svframelib.gui.PluginInventory;
import vn.svframe.svframelib.gui.editable.GeneratedInventory;
import vn.svframe.svframelib.gui.editable.item.InventoryItem;
import vn.svframe.svframelib.gui.editable.item.builtin.GoBackItem;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.ClassOption;

import java.util.Locale;
import java.util.Map;

public final class SubclassSelect extends AbstractClassSelect {
    public SubclassSelect() { super("subclass-select"); }

    @Override public InventoryItem<?> resolveItem(String function, Map<String, Object> config) {
        if (function.startsWith("sub-class-")) return new ClassItem(config);
        if (function.equalsIgnoreCase("back")) return new GoBackItem<>(config);
        return null;
    }

    public GeneratedInventory newInventory(PlayerData data) {
        ClassSelect.ProfessSelectionInventory previous = SVFrameMMO.gui().classSelect().newInventory(data);
        return new SubclassSelectionInventory(previous, data);
    }

    private final class ClassItem extends AbstractClassItem<SubclassSelectionInventory> {
        ClassItem(Map<String, ?> config) { super(config, "sub-class-".length()); }

        @Override public boolean isDisplayed(SubclassSelectionInventory inv) {
            return inv.playerData.getProfess().getSubclasses().stream().anyMatch(subclass ->
                    subclass.getLevel() <= inv.playerData.getLevel() && subclass.getProfess().getId().equals(playerClass.getId()));
        }

        @Override public void onClick(SubclassSelectionInventory inv, PluginInventory.Click click) {
            if (inv.playerData.getClassPoints() < 1) {
                GuiSupport.action(inv.getPlayer(), "&cYou do not have any class points.");
                return;
            }
            if (playerClass.hasOption(ClassOption.NEEDS_PERMISSION)) {
                String id = playerClass.getId().toLowerCase(Locale.ROOT);
                boolean allowed = SVFrameMMO.permissions().has(inv.getPlayer(), "svframemmo.class." + id);
                if (!allowed) {
                    GuiSupport.action(inv.getPlayer(), "&cYou do not have permission to choose " + playerClass.getName());
                    return;
                }
            }
            SVFrameMMO.gui().confirmation(playerClass).newInventory(inv, true).open();
        }
    }

    public final class SubclassSelectionInventory extends AbstractClassGeneratedInventory {
        SubclassSelectionInventory(ClassSelect.ProfessSelectionInventory previous, PlayerData data) {
            super(previous.getNavigator(), data);
        }
    }
}
