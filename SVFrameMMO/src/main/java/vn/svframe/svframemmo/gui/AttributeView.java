package vn.svframe.svframemmo.gui;

import vn.svframe.svframelib.gui.Navigator;
import vn.svframe.svframelib.gui.PluginInventory;
import vn.svframe.svframelib.gui.editable.EditableInventory;
import vn.svframe.svframelib.gui.editable.GeneratedInventory;
import vn.svframe.svframelib.gui.editable.item.InventoryItem;
import vn.svframe.svframelib.gui.editable.item.PhysicalItem;
import vn.svframe.svframelib.gui.editable.placeholder.Placeholders;
import vn.svframe.svframelib.manager.StatManager;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.attribute.PlayerAttribute;

import java.util.Map;

public final class AttributeView extends EditableInventory {
    public AttributeView() { super("attribute-view"); }

    @Override public InventoryItem<?> resolveItem(String function, Map<String, Object> config) {
        if (function.equalsIgnoreCase("reallocation")) return new ReallocateButton(config);
        if (function.startsWith("attribute_")) return new AttributeItem(function, config);
        return null;
    }

    public AttrInventory newInventory(PlayerData data) { return new AttrInventory(data); }

    private final class ReallocateButton extends PhysicalItem<AttrInventory> {
        ReallocateButton(Map<String, ?> config) { super(config); }
        @Override public Placeholders getPlaceholders(AttrInventory inv, int n) {
            return GuiSupport.placeholders(
                    "attribute_points", inv.playerData.getAttributePoints(),
                    "points", inv.playerData.getAttributeReallocationPoints(),
                    "total", inv.playerData.getAttributes().countPoints());
        }
        @Override public void onClick(AttrInventory inv, PluginInventory.Click click) {
            int spent = inv.playerData.getAttributes().countPoints();
            if (spent < 1) { GuiSupport.action(inv.getPlayer(), "&cYou have not spent any attribute points."); return; }
            if (inv.playerData.getAttributeReallocationPoints() < 1) { GuiSupport.action(inv.getPlayer(), "&cYou need an attribute reallocation point."); return; }
            if (!inv.playerData.reallocateAttributes()) return;
            GuiSupport.action(inv.getPlayer(), "&aAttribute points reallocated. Available: &6" + inv.playerData.getAttributePoints());
            inv.open();
        }
    }

    private static final class AttributeItem extends PhysicalItem<AttrInventory> {
        private final PlayerAttribute attribute;
        private final int shiftCost;

        AttributeItem(String function, Map<String, ?> config) {
            super(config);
            String id = function.substring("attribute_".length()).toLowerCase(java.util.Locale.ROOT).replace('_', '-').replace(' ', '-');
            attribute = SVFrameMMO.attributes().get(id);
            if (attribute == null) throw new IllegalArgumentException("Could not find attribute with ID '" + id + "'");
            shiftCost = Math.max(1, GuiSupport.integer(config, "shift-cost", 1));
        }

        @Override public Placeholders getPlaceholders(AttrInventory inv, int n) {
            var instance = inv.playerData.getAttributes().getInstance(attribute);
            int total = instance.getTotal();
            Placeholders holders = GuiSupport.placeholders(
                    "name", attribute.getName(), "buffs", attribute.getBuffs().size(),
                    "spent", instance.getBase(), "total", total,
                    "max", attribute.hasMax() ? attribute.getMax() : "∞",
                    "current", total, "attribute_points", inv.playerData.getAttributePoints(),
                    "shift_points", shiftCost);
            for (PlayerAttribute.Buff buff : attribute.getBuffs()) {
                String stat = buff.stat();
                holders.register("buff_" + stat.toLowerCase(java.util.Locale.ROOT), StatManager.format(stat, buff.value()));
                holders.register("total_" + stat.toLowerCase(java.util.Locale.ROOT), StatManager.format(stat, buff.value() * total));
            }
            return holders;
        }

        @Override public void onClick(AttrInventory inv, PluginInventory.Click click) {
            if (inv.playerData.getAttributePoints() < 1) {
                GuiSupport.action(inv.getPlayer(), "&cYou do not have any attribute points.");
                return;
            }
            var instance = inv.playerData.getAttributes().getInstance(attribute);
            if (attribute.hasMax() && instance.getBase() >= attribute.getMax()) {
                GuiSupport.action(inv.getPlayer(), "&cThis attribute is already maxed.");
                return;
            }
            boolean shift = GuiSupport.shift(click);
            int requested = shift ? shiftCost : 1;
            if (attribute.hasMax()) requested = Math.min(requested, attribute.getMax() - instance.getBase());
            if (shift && inv.playerData.getAttributePoints() < requested) {
                GuiSupport.action(inv.getPlayer(), "&cNot enough attribute points. Required: " + requested);
                return;
            }
            if (!inv.playerData.spendAttributePoints(attribute.getId(), requested)) return;
            GuiSupport.action(inv.getPlayer(), "&a" + attribute.getName() + " increased to &6" + instance.getBase());
            inv.open();
        }
    }

    public final class AttrInventory extends GeneratedInventory {
        private final PlayerData playerData;
        AttrInventory(PlayerData data) { super(new Navigator(data.getMMOPlayerData()), AttributeView.this); this.playerData = data; }
    }
}
