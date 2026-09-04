package vn.svframe.svframemmo.gui;

import vn.svframe.svframelib.gui.Navigator;
import vn.svframe.svframelib.gui.PluginInventory;
import vn.svframe.svframelib.gui.editable.EditableInventory;
import vn.svframe.svframelib.gui.editable.GeneratedInventory;
import vn.svframe.svframelib.gui.editable.item.InventoryItem;
import vn.svframe.svframelib.gui.editable.item.ItemOptions;
import vn.svframe.svframelib.gui.editable.item.PhysicalItem;
import vn.svframe.svframelib.gui.editable.item.builtin.NextPageItem;
import vn.svframe.svframelib.gui.editable.item.builtin.PreviousPageItem;
import vn.svframe.svframelib.gui.editable.placeholder.Placeholders;
import vn.svframe.svframelib.gui.util.IconOptions;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.PlayerClass;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** MMOCore-style skill viewer retaining the unified class + integration catalog. */
public final class SkillList extends EditableInventory {
    public SkillList() { super("skill-list"); }

    @Override public InventoryItem<?> resolveItem(String function, Map<String, Object> config) {
        return switch (function) {
            case "skill" -> new SkillItem(config);
            case "level" -> new LevelItem(config);
            case "upgrade" -> new UpgradeItem(config);
            case "reallocation" -> new ReallocationItem(config);
            case "slot" -> new SlotItem(config);
            case "previous" -> new PreviousPageItem<>(config);
            case "next" -> new NextPageItem<>(config);
            case "selected" -> new SelectedItem(config);
            default -> null;
        };
    }

    public GeneratedInventory newInventory(PlayerData data) { return new SkillViewerInventory(data); }

    private final class ReallocationItem extends PhysicalItem<SkillViewerInventory> {
        ReallocationItem(Map<String, ?> config) { super(config); }
        @Override public boolean hasDifferentDisplay() { return true; }
        @Override public Placeholders getPlaceholders(SkillViewerInventory inv, int i) {
            return GuiSupport.placeholders("skill_points", inv.playerData.getSkillPoints(),
                    "points", inv.playerData.getSkillReallocationPoints(), "total", PlayerSkillCatalog.spentPoints(inv.playerData));
        }
        @Override public void onClick(SkillViewerInventory inv, PluginInventory.Click click) {
            int spent = PlayerSkillCatalog.spentPoints(inv.playerData);
            if (spent < 1) { GuiSupport.action(inv.getPlayer(), "&cYou have not spent any skill points."); return; }
            if (inv.playerData.getSkillReallocationPoints() < 1) { GuiSupport.action(inv.getPlayer(), "&cYou need a skill reallocation point."); return; }
            PlayerSkillCatalog.resetSpentLevels(inv.playerData);
            inv.playerData.giveSkillPoints(spent);
            inv.playerData.giveSkillReallocationPoints(-1);
            GuiSupport.action(inv.getPlayer(), "&aSkill points reallocated. Available: &6" + inv.playerData.getSkillPoints());
            inv.refreshCatalog();
            inv.open();
        }
    }

    private final class SelectedItem extends PhysicalItem<SkillViewerInventory> {
        SelectedItem(Map<String, ?> config) { super(config); }
        @Override public void preprocessLore(SkillViewerInventory inv, int index, List<String> lore) {
            if (inv.selected == null) return;
            boolean unlocked = inv.selected.learned();
            int loreIndex = lore.indexOf("{lore}");
            if (loreIndex >= 0) { lore.remove(loreIndex); lore.addAll(loreIndex, calculateLore(inv, inv.selected, inv.selected.level())); }
            conditionLore(lore, unlocked, isMax(inv.selected));
        }
        @Override public net.minecraft.item.ItemStack getDisplayedItem(SkillViewerInventory inv, int n) {
            if (inv.selected == null) return net.minecraft.item.ItemStack.EMPTY;
            return getDisplayedItem(inv, new ItemOptions(n, inv.selected.skill().getSkill().getIcon()));
        }
        @Override public Placeholders getPlaceholders(SkillViewerInventory inv, int n) {
            if (inv.selected == null) return structuralPlaceholders();
            return skillPlaceholders(inv.selected, inv.selected.level());
        }
    }

    private final class LevelItem extends PhysicalItem<SkillViewerInventory> {
        private final int offset;
        LevelItem(Map<String, ?> config) { super(config); offset = GuiSupport.integer(config, "offset", 0); }
        @Override public boolean hasDifferentDisplay() { return true; }
        @Override public void preprocessLore(SkillViewerInventory inv, int n, List<String> lore) {
            if (inv.selected == null) return;
            int level = inv.selected.level() + n - offset;
            if (level < 1) return;
            int index = lore.indexOf("{lore}");
            if (index >= 0) { lore.remove(index); lore.addAll(index, calculateLore(inv, inv.selected, level)); }
        }
        @Override public net.minecraft.item.ItemStack getDisplayedItem(SkillViewerInventory inv, int n) {
            if (inv.selected == null || inv.selected.level() + n - offset < 1) return net.minecraft.item.ItemStack.EMPTY;
            return super.getDisplayedItem(inv, n);
        }
        @Override public Placeholders getPlaceholders(SkillViewerInventory inv, int n) {
            if (inv.selected == null) return structuralPlaceholders();
            int level = inv.selected.level() + n - offset;
            return skillPlaceholders(inv.selected, level);
        }
    }

    private final class SlotItem extends PhysicalItem<SkillViewerInventory> {
        private final String none;
        private final IconOptions filledIcon;
        SlotItem(Map<String, ?> config) {
            super(config);
            none = GuiSupport.colors(GuiSupport.string(config, "no-skill", "&cNone"));
            String filled = GuiSupport.string(config, "filled-item", "");
            filledIcon = filled.isBlank() ? null : IconOptions.from(Map.of("item", filled, "custom-model-data", GuiSupport.integer(config, "filled-custom-model-data", 0)));
        }
        @Override public void preprocessLore(SkillViewerInventory inv, int n, List<String> lore) {
            int slot = inv.slotNumber(n);
            PlayerClass.SkillSlotDefinition definition = inv.playerData.getProfess().getSkillSlot(slot);
            int index = lore.indexOf("{slot-lore}");
            if (index >= 0) {
                lore.remove(index);
                if (definition != null) lore.addAll(index, definition.lore());
            }
            index = lore.indexOf("{skill-lore}");
            if (index >= 0) {
                lore.remove(index);
                PlayerSkillCatalog.Entry bound = PlayerSkillCatalog.bindings(inv.playerData).get(slot);
                if (bound != null) lore.addAll(index, calculateLore(inv, bound, bound.level()));
            }
        }
        @Override public String preprocessName(SkillViewerInventory inv, int n, String name) {
            int slot = inv.slotNumber(n);
            PlayerClass.SkillSlotDefinition definition = inv.playerData.getProfess().getSkillSlot(slot);
            return definition == null ? name.replace("{slot}", String.valueOf(slot)) : definition.name();
        }
        @Override public net.minecraft.item.ItemStack getDisplayedItem(SkillViewerInventory inv, int n) {
            int slot = inv.slotNumber(n);
            if (slot <= 0) return net.minecraft.item.ItemStack.EMPTY;
            PlayerClass.SkillSlotDefinition definition = inv.playerData.getProfess().getSkillSlot(slot);
            if (definition != null && !inv.playerData.hasUnlocked("slot:" + slot)) return net.minecraft.item.ItemStack.EMPTY;
            PlayerSkillCatalog.Entry bound = PlayerSkillCatalog.bindings(inv.playerData).get(slot);
            ItemOptions options = bound == null ? ItemOptions.index(n)
                    : filledIcon == null ? new ItemOptions(n, bound.skill().getSkill().getIcon()) : new ItemOptions(n, filledIcon);
            return super.getDisplayedItem(inv, options);
        }
        @Override public Placeholders getPlaceholders(SkillViewerInventory inv, int n) {
            int slot = inv.slotNumber(n);
            PlayerClass.SkillSlotDefinition definition = inv.playerData.getProfess().getSkillSlot(slot);
            PlayerSkillCatalog.Entry bound = PlayerSkillCatalog.bindings(inv.playerData).get(slot);
            Placeholders placeholders = GuiSupport.placeholders(
                    "slot", definition == null ? "Skill Slot " + slot : definition.name(),
                    "selected", inv.selected == null ? none : inv.selected.skill().getSkill().getName(),
                    "skill", bound == null ? none : bound.skill().getSkill().getName(),
                    "slot_lore", "{slot-lore}", "skill_lore", "{skill-lore}");
            return placeholders;
        }
        @Override public boolean hasDifferentDisplay() { return true; }
        @Override public void onClick(SkillViewerInventory inv, PluginInventory.Click click) {
            int n = inv.slotSlots == null ? -1 : inv.slotSlots.indexOf(click.slot());
            if (n < 0) return;
            int slot = inv.slotNumber(n);
            PlayerClass.SkillSlotDefinition definition = inv.playerData.getProfess().getSkillSlot(slot);
            if (definition != null && !inv.playerData.hasUnlocked("slot:" + slot)) return;
            if (definition != null && definition.hardset() != null) {
                GuiSupport.action(inv.getPlayer(), "&cThis skill slot is hard-bound and cannot be edited.");
                return;
            }
            Map<Integer, PlayerSkillCatalog.Entry> bindings = PlayerSkillCatalog.bindings(inv.playerData);
            if (GuiSupport.shiftLeft(click)) {
                PlayerSkillCatalog.Entry bound = bindings.get(slot);
                if (bound != null) { inv.selected = bound; inv.open(); }
                return;
            }
            if (GuiSupport.right(click)) {
                if (bindings.get(slot) == null) { GuiSupport.action(inv.getPlayer(), "&cNo skill is bound to this slot."); return; }
                if (definition != null && !definition.canManuallyBind()) { GuiSupport.action(inv.getPlayer(), "&cThis slot cannot be manually edited."); return; }
                String removed = PlayerSkillCatalog.unbind(inv.playerData, slot);
                GuiSupport.action(inv.getPlayer(), "&aUnbound &6" + removed + "&a from slot &6" + slot);
                inv.open();
                return;
            }
            if (inv.selected == null) return;
            if (!inv.selected.learned()) { GuiSupport.action(inv.getPlayer(), "&cThis skill is locked."); return; }
            if (!inv.selected.bindable()) { GuiSupport.action(inv.getPlayer(), "&cPassive/permanent skills cannot be bound."); return; }
            if (definition != null && !definition.canManuallyBind()) { GuiSupport.action(inv.getPlayer(), "&cThis slot cannot be manually edited."); return; }
            try {
                PlayerSkillCatalog.bind(inv.playerData, slot, inv.selected.id());
                GuiSupport.action(inv.getPlayer(), "&aBound &6" + inv.selected.skill().getSkill().getName() + "&a to slot &6" + slot);
            } catch (RuntimeException exception) {
                GuiSupport.action(inv.getPlayer(), "&c" + (exception.getMessage() == null ? "Could not bind skill." : exception.getMessage()));
            }
            inv.open();
        }
    }

    private final class SkillItem extends PhysicalItem<SkillViewerInventory> {
        private final boolean upgradeOnClick, disableClick;
        SkillItem(Map<String, ?> config) {
            super(config);
            disableClick = GuiSupport.bool(config, "disable_click", GuiSupport.bool(config, "disable-click", false));
            upgradeOnClick = GuiSupport.bool(config, "upgrade_on_click", GuiSupport.bool(config, "upgrade-on-click", false));
        }
        @Override public boolean hasDifferentDisplay() { return true; }
        @Override public void preprocessLore(SkillViewerInventory inv, int n, List<String> lore) {
            int index = inv.getPageIndex(n);
            if (index < 0 || index >= inv.skills.size()) return;
            PlayerSkillCatalog.Entry entry = inv.skills.get(index);
            int loreIndex = lore.indexOf("{lore}");
            if (loreIndex >= 0) { lore.remove(loreIndex); lore.addAll(loreIndex, calculateLore(inv, entry, Math.max(1, entry.level()))); }
            conditionLore(lore, entry.learned(), isMax(entry));
            if (entry.origin() == PlayerSkillCatalog.Origin.EXTERNAL)
                lore.add(0, "§8Integration / Pokémon skill");
        }
        @Override public net.minecraft.item.ItemStack getDisplayedItem(SkillViewerInventory inv, int n) {
            int index = inv.getPageIndex(n);
            if (index < 0 || index >= inv.skills.size()) return net.minecraft.item.ItemStack.EMPTY;
            return getDisplayedItem(inv, new ItemOptions(n, inv.skills.get(index).skill().getSkill().getIcon()));
        }
        @Override public Placeholders getPlaceholders(SkillViewerInventory inv, int n) {
            int index = inv.getPageIndex(n);
            return index < 0 || index >= inv.skills.size() ? structuralPlaceholders() : skillPlaceholders(inv.skills.get(index), inv.skills.get(index).level());
        }
        @Override public void onClick(SkillViewerInventory inv, PluginInventory.Click click) {
            if (disableClick) return;
            int visual = inv.skillSlots.indexOf(click.slot());
            int index = visual < 0 ? -1 : inv.getPageIndex(visual);
            if (index < 0 || index >= inv.skills.size()) return;
            PlayerSkillCatalog.Entry focus = inv.skills.get(index);
            if (upgradeOnClick) { inv.tryUpgrade(focus, click, 0); return; }
            inv.selected = focus;
            GuiSupport.action(inv.getPlayer(), "&aSelected skill: &6" + focus.skill().getSkill().getName());
            inv.open();
        }
    }

    private final class UpgradeItem extends PhysicalItem<SkillViewerInventory> {
        private final int shiftCost;
        UpgradeItem(Map<String, ?> config) { super(config); shiftCost = Math.max(1, GuiSupport.integer(config, "shift-cost", 5)); }
        @Override public Placeholders getPlaceholders(SkillViewerInventory inv, int n) {
            if (inv.selected == null) return structuralPlaceholders();
            String name = inv.selected.skill().getSkill().getName();
            Placeholders placeholders = skillPlaceholders(inv.selected, inv.selected.level());
            placeholders.register("skill_caps", name.toUpperCase(java.util.Locale.ROOT));
            placeholders.register("skill_points", inv.playerData.getSkillPoints());
            placeholders.register("shift_points", shiftCost);
            return placeholders;
        }
        @Override public boolean isDisplayed(SkillViewerInventory inv) {
            return inv.selected != null && inv.selected.learned() && inv.selected.skill().isUpgradable();
        }
        @Override public void onClick(SkillViewerInventory inv, PluginInventory.Click click) { if (inv.selected != null) inv.tryUpgrade(inv.selected, click, shiftCost); }
    }

    public final class SkillViewerInventory extends GeneratedInventory {
        private List<PlayerSkillCatalog.Entry> skills;
        private final List<Integer> skillSlots;
        private final List<Integer> slotSlots;
        private PlayerSkillCatalog.Entry selected;
        private final PlayerData playerData;

        SkillViewerInventory(PlayerData playerData) {
            super(new Navigator(playerData.getMMOPlayerData()), SkillList.this);
            this.playerData = playerData;
            InventoryItem<?> skillItem = getEditable().getByFunction("skill");
            if (skillItem == null || skillItem.getSlots().isEmpty()) throw new IllegalStateException("skill-list.yml must define skill slots");
            skillSlots = skillItem.getSlots();
            InventoryItem<?> slotItem = getEditable().getByFunction("slot");
            slotSlots = slotItem == null ? null : slotItem.getSlots();
            refreshCatalog();
            enablePagination(skillSlots.size());
        }

        private void refreshCatalog() {
            ArrayList<PlayerSkillCatalog.Entry> list = new ArrayList<>();
            for (PlayerSkillCatalog.Entry entry : PlayerSkillCatalog.entries(playerData)) {
                if (entry.origin() == PlayerSkillCatalog.Origin.EXTERNAL
                        || playerData.hasUnlocked(entry.skill().getUnlockNamespacedKey())) list.add(entry);
            }
            list.sort(Comparator.comparing((PlayerSkillCatalog.Entry e) -> e.origin() == PlayerSkillCatalog.Origin.CLASS ? 0 : 1)
                    .thenComparingInt(e -> e.skill().getUnlockLevel())
                    .thenComparing(e -> e.skill().getSkill().getName(), String.CASE_INSENSITIVE_ORDER));
            skills = List.copyOf(list);
            if (selected == null || skills.stream().noneMatch(e -> e.id().equals(selected.id()))) selected = skills.isEmpty() ? null : skills.getFirst();
            else selected = skills.stream().filter(e -> e.id().equals(selected.id())).findFirst().orElse(selected);
        }

        @Override public void onOpen() {
            refreshCatalog();
            if (skillSlots.size() == 1 && !skills.isEmpty()) {
                int index = getPageIndex(0);
                if (index < skills.size()) selected = skills.get(index);
            }
        }

        @Override public String getRawName() {
            String name = selected == null ? "None" : selected.skill().getSkill().getName();
            return guiName.replace("{skill}", name).replace("{selected}", name);
        }
        /** Resolve the selected-skill placeholder here as well so title correctness does not depend on base GUI bake order. */
        @Override public String getTitle() { return GuiSupport.colors(getRawName()); }
        @Override public int getMaxPage() { return computeMaxPage(skills.size()); }

        int slotNumber(int visualIndex) {
            List<Integer> slots = PlayerSkillCatalog.slots(playerData);
            return visualIndex >= 0 && visualIndex < slots.size() ? slots.get(visualIndex) : visualIndex + 1;
        }

        void tryUpgrade(PlayerSkillCatalog.Entry entry, PluginInventory.Click click, int shiftCost) {
            if (!entry.learned()) { GuiSupport.action(getPlayer(), "&cThis skill is locked."); return; }
            if (!entry.skill().isUpgradable()) { GuiSupport.action(getPlayer(), "&cThis skill cannot be upgraded."); return; }
            int current = entry.level();
            if (entry.skill().hasMaxLevel() && current >= entry.skill().getMaxLevel()) { GuiSupport.action(getPlayer(), "&cSkill is already maxed out."); return; }
            int requested = shiftCost > 0 && GuiSupport.shift(click) ? shiftCost : 1;
            if (entry.skill().hasMaxLevel()) requested = Math.min(requested, entry.skill().getMaxLevel() - current);
            if (requested <= 0) { GuiSupport.action(getPlayer(), "&cSkill is already maxed out."); return; }
            if (playerData.getSkillPoints() < requested) { GuiSupport.action(getPlayer(), "&cNot enough skill points. Required: " + requested); return; }
            int purchased = PlayerSkillCatalog.upgrade(playerData, entry, requested);
            if (purchased < 1) { GuiSupport.action(getPlayer(), "&cSkill progression request was rejected."); return; }
            refreshCatalog();
            selected = skills.stream().filter(e -> e.id().equals(entry.id())).findFirst().orElse(selected);
            GuiSupport.action(getPlayer(), "&aUpgraded &6" + entry.skill().getSkill().getName() + "&a to level &6" + selected.level());
            open();
        }
    }

    private static boolean isMax(PlayerSkillCatalog.Entry entry) {
        return entry.skill().hasMaxLevel() && entry.level() >= entry.skill().getMaxLevel();
    }

    private static Placeholders structuralPlaceholders() {
        return GuiSupport.placeholders("unlocked", "{unlocked}", "locked", "{locked}",
                "max_level", "{max_level}", "lore", "{lore}");
    }

    private static Placeholders skillPlaceholders(PlayerSkillCatalog.Entry entry, int level) {
        Placeholders placeholders = structuralPlaceholders();
        placeholders.register("selected", entry.skill().getSkill().getName());
        placeholders.register("skill", entry.skill().getSkill().getName());
        placeholders.register("unlock", entry.skill().getUnlockLevel());
        placeholders.register("level", level);
        placeholders.register("roman", GuiSupport.roman(level));
        placeholders.register("source", entry.origin() == PlayerSkillCatalog.Origin.CLASS ? "Class" : "Integration");
        return placeholders;
    }

    private static List<String> calculateLore(SkillViewerInventory inv, PlayerSkillCatalog.Entry entry, int level) {
        List<String> lore = entry.skill().calculateLore(inv.playerData, Math.max(1, level));
        return lore.isEmpty() && entry.origin() == PlayerSkillCatalog.Origin.EXTERNAL
                ? List.of("§7External skill provided by integration.") : lore;
    }

    private static void conditionLore(List<String> lore, boolean unlocked, boolean maxed) {
        for (int i = 0; i < lore.size();) {
            String line = lore.get(i);
            if (line.startsWith("{unlocked}")) { if (!unlocked) lore.remove(i); else { lore.set(i, line.substring(10)); i++; } }
            else if (line.startsWith("{locked}")) { if (unlocked) lore.remove(i); else { lore.set(i, line.substring(8)); i++; } }
            else if (line.startsWith("{max_level}")) { if (!maxed) lore.remove(i); else { lore.set(i, line.substring(11)); i++; } }
            else i++;
        }
    }
}
