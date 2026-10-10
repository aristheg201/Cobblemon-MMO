package vn.svframe.svframemmo.gui;

import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.gui.Navigator;
import vn.svframe.svframelib.gui.editable.EditableInventory;
import vn.svframe.svframelib.gui.editable.GeneratedInventory;
import vn.svframe.svframelib.gui.editable.item.ItemOptions;
import vn.svframe.svframelib.gui.editable.item.SimpleItem;
import vn.svframe.svframelib.gui.editable.placeholder.Placeholders;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.PlayerClass;

import java.util.List;
import java.util.Map;

abstract class AbstractClassSelect extends EditableInventory {
    AbstractClassSelect(String id) { super(id); }

    abstract class AbstractClassItem<T extends GeneratedInventory> extends SimpleItem<T> {
        protected final PlayerClass playerClass;

        AbstractClassItem(Map<String, ?> config, int substringIndex) {
            super(config);
            String function = GuiSupport.string(config, "function", "");
            if (function.length() <= substringIndex) throw new IllegalArgumentException("Couldn't find the class associated to: " + function);
            String classId = UtilityMethods.enumName(function.substring(substringIndex));
            this.playerClass = SVFrameMMO.classes().getOrThrow(classId);
        }

        @Override public boolean hasDifferentDisplay() { return true; }

        @Override public void preprocessLore(T inv, int n, List<String> lore) {
            int index = lore.indexOf("{lore}");
            if (index >= 0) { lore.remove(index); lore.addAll(index, playerClass.getDescription()); }
            index = lore.indexOf("{attribute-lore}");
            if (index >= 0) { lore.remove(index); lore.addAll(index, playerClass.getAttributeDescription()); }
        }

        @Override public net.minecraft.item.ItemStack getDisplayedItem(T inv, int n) {
            ItemOptions options = n == 0 ? new ItemOptions(n, playerClass.getRawIcon()) : ItemOptions.index(n);
            return super.getDisplayedItem(inv, options);
        }

        @Override public Placeholders getPlaceholders(T inv, int n) {
            Placeholders placeholders = super.getPlaceholders(inv, n);
            placeholders.register("name", playerClass.getName());
            placeholders.register("class", playerClass.getName());
            return placeholders;
        }
    }

    abstract class AbstractClassGeneratedInventory extends GeneratedInventory {
        final PlayerData playerData;
        AbstractClassGeneratedInventory(Navigator navigator, PlayerData playerData) {
            super(navigator, AbstractClassSelect.this);
            this.playerData = playerData;
        }
    }
}
