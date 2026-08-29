package vn.svframe.svframemmo.gui.skilltree;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.gui.Navigator;
import vn.svframe.svframelib.gui.PluginInventory;
import vn.svframe.svframelib.gui.editable.EditableInventory;
import vn.svframe.svframelib.gui.editable.GeneratedInventory;
import vn.svframe.svframelib.gui.editable.item.InventoryItem;
import vn.svframe.svframelib.gui.editable.item.ItemOptions;
import vn.svframe.svframelib.gui.editable.item.PhysicalItem;
import vn.svframe.svframelib.gui.editable.item.SimpleItem;
import vn.svframe.svframelib.gui.editable.placeholder.Placeholders;
import vn.svframe.svframelib.gui.util.IconOptions;
import vn.svframe.svframelib.module.MMOPlugin;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.skilltree.IntCoords;
import vn.svframe.svframemmo.skilltree.NodeIncrementResult;
import vn.svframe.svframemmo.skilltree.NodeState;
import vn.svframe.svframemmo.skilltree.ParentInformation;
import vn.svframe.svframemmo.skilltree.ParentType;
import vn.svframe.svframemmo.skilltree.SkillTree;
import vn.svframe.svframemmo.skilltree.SkillTreeNode;
import vn.svframe.svframemmo.skilltree.display.DisplayMap;
import vn.svframe.svframemmo.skilltree.display.NodeDisplayInfo;
import vn.svframe.svframemmo.skilltree.display.PathDisplayInfo;

import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Exact MMOCore-style skill-tree inventory: tree selector, pan controls, paths, states and node upgrades. */
public final class SkillTreeViewer extends EditableInventory {
    private DisplayMap icons = DisplayMap.EMPTY;
    private final Map<NodeState, String> statusNames = new HashMap<>();
    private final SkillTree defaultSkillTree;

    public SkillTreeViewer() { super("skill-tree"); defaultSkillTree = null; }
    public SkillTreeViewer(SkillTree initialSkillTree, boolean isDefault) {
        super("specific-skill-tree-" + (isDefault ? "default" : normalize(initialSkillTree.getId())));
        defaultSkillTree = initialSkillTree;
    }

    @Override public void reload(MMOPlugin plugin, Map<String, ?> config) {
        super.reload(plugin, config);
        statusNames.clear();
        Map<String, Object> names = map(config.get("status-names"));
        for (NodeState state : NodeState.values()) {
            Object found = get(names, key(state));
            statusNames.put(state, found == null ? state.name() : String.valueOf(found));
        }
        icons = DisplayMap.from(config.get("display"));
    }

    @Override public InventoryItem<?> resolveItem(String function, Map<String, Object> config) {
        return switch (function) {
            case "skill-tree" -> new SkillTreeItem(config);
            case "up" -> new DirectionItem(config, 0, -1);
            case "down" -> new DirectionItem(config, 0, 1);
            case "left" -> new DirectionItem(config, -1, 0);
            case "right" -> new DirectionItem(config, 1, 0);
            case "reallocation" -> new ReallocateButton(config);
            case "skill-tree-node" -> new SkillTreeNodeItem(config);
            case "next-tree-list-page" -> new TreePageItem(config, 1);
            case "previous-tree-list-page" -> new TreePageItem(config, -1);
            default -> null;
        };
    }

    public SkillTreeInventory newInventory(PlayerData data) { return new SkillTreeInventory(data, defaultSkillTree); }

    private final class ReallocateButton extends PhysicalItem<SkillTreeInventory> {
        ReallocateButton(Map<String, ?> config) { super(config); }
        @Override public Placeholders getPlaceholders(SkillTreeInventory inv, int n) { return inv.treePlaceholders(); }
        @Override public void onClick(SkillTreeInventory inv, PluginInventory.Click click) {
            int spent = inv.playerData.getSkillTrees().getPointsSpent(inv.skillTree);
            if (spent < 1) { action(inv, "&cYou have not spent any points in this skill tree."); return; }
            if (inv.playerData.getSkillTreeReallocationPoints() <= 0) { action(inv, "&cYou need a skill-tree reallocation point."); return; }
            if (!inv.playerData.getSkillTrees().reallocate(inv.skillTree)) return;
            inv.skillTree.resolveStates(inv.playerData);
            action(inv, "&aReallocated &6" + inv.skillTree.getName() + "&a. Available points: &6" + inv.playerData.getSkillTrees().getPoints(inv.skillTree.getId()));
            inv.open();
        }
    }

    private final class DirectionItem extends SimpleItem<SkillTreeInventory> {
        private final int dx, dy;
        DirectionItem(Map<String, ?> config, int dx, int dy) { super(config); this.dx = dx; this.dy = dy; }
        @Override public void onClick(SkillTreeInventory inv, PluginInventory.Click click) {
            inv.x += dx * SVFrameMMO.config().skillTreeScrollStepX();
            inv.y += dy * SVFrameMMO.config().skillTreeScrollStepY();
            inv.open();
        }
    }

    private final class TreePageItem extends SimpleItem<SkillTreeInventory> {
        private final int delta;
        TreePageItem(Map<String, ?> config, int delta) { super(config); this.delta = delta; }
        @Override public boolean isDisplayed(SkillTreeInventory inv) { return delta > 0 ? inv.treeListPage < inv.maxTreeListPage : inv.treeListPage > 0; }
        @Override public void onClick(SkillTreeInventory inv, PluginInventory.Click click) {
            inv.treeListPage = Math.max(0, Math.min(inv.maxTreeListPage, inv.treeListPage + delta));
            inv.open();
        }
    }

    private final class SkillTreeItem extends PhysicalItem<SkillTreeInventory> {
        SkillTreeItem(Map<String, ?> config) { super(config); }
        @Override public boolean hasDifferentDisplay() { return true; }
        private SkillTree at(SkillTreeInventory inv, int n) {
            int index = getSlots().size() * inv.treeListPage + n;
            return index >= 0 && index < inv.skillTrees.size() ? inv.skillTrees.get(index) : null;
        }
        @Override public void preprocessLore(SkillTreeInventory inv, int n, List<String> lore) {
            SkillTree tree = at(inv, n); if (tree == null) return;
            int index = lore.indexOf("{tree-lore}");
            if (index >= 0) { lore.remove(index); lore.addAll(index, tree.getLore()); }
        }
        @Override public net.minecraft.item.ItemStack getDisplayedItem(SkillTreeInventory inv, int n) {
            SkillTree tree = at(inv, n); if (tree == null) return null;
            return super.getDisplayedItem(inv, new ItemOptions(n, tree.getIcon()));
        }
        @Override public Placeholders getPlaceholders(SkillTreeInventory inv, int n) {
            SkillTree tree = at(inv, n); if (tree == null) return new Placeholders();
            Placeholders holders = inv.treePlaceholders(tree);
            holders.register("name", tree.getName()); holders.register("id", tree.getId()); holders.register("skill-tree-node", tree.getName());
            return holders;
        }
        @Override public void onClick(SkillTreeInventory inv, PluginInventory.Click click) {
            int n = getSlots().indexOf(click.slot()); SkillTree tree = at(inv, n); if (tree == null) return;
            inv.skillTree = tree; inv.skillTree.resolveStates(inv.playerData); action(inv, "&eCurrent skill tree: &6" + tree.getName()); inv.open();
        }
    }

    private final class SkillTreeNodeItem extends PhysicalItem<SkillTreeInventory> {
        private final List<String> pathLore;
        SkillTreeNodeItem(Map<String, ?> config) { super(config); pathLore = strings(config.get("path-lore")); }
        @Override public boolean hasDifferentDisplay() { return true; }

        @Override public String preprocessName(SkillTreeInventory inv, int n, String name) {
            return inv.skillTree.isNode(inv.getCoordinates(n)) ? name : " ";
        }

        @Override public void preprocessLore(SkillTreeInventory inv, int n, List<String> lore) {
            IntCoords coordinates = inv.getCoordinates(n);
            if (!inv.skillTree.isNode(coordinates)) { lore.clear(); lore.addAll(pathLore); return; }
            SkillTreeNode node = inv.skillTree.getNode(coordinates);
            for (int index = 0; index < lore.size();) {
                String line = lore.get(index);
                if (line.contains("{node-lore}")) {
                    lore.remove(index); List<String> inserted = node.getLore(inv.playerData); AtomicInteger cursor = new AtomicInteger(index);
                    inserted.forEach(value -> lore.add(cursor.getAndIncrement(), line.replace("{node-lore}", value))); index += inserted.size();
                } else if (line.contains("{strong-parents}")) index = replaceParents(inv, node, ParentType.STRONG, lore, index);
                else if (line.contains("{soft-parents}")) index = replaceParents(inv, node, ParentType.SOFT, lore, index);
                else if (line.contains("{incompatible-parents}")) index = replaceParents(inv, node, ParentType.INCOMPATIBLE, lore, index);
                else index++;
            }
        }

        private int replaceParents(SkillTreeInventory inv, SkillTreeNode node, ParentType type, List<String> lore, int index) {
            String template = lore.remove(index); List<String> inserted = parentsLore(inv, node, node.getParents(type));
            for (int i = 0; i < inserted.size(); i++) lore.add(index + i, template.replace("{" + key(type) + "-parents}", inserted.get(i)));
            return index + inserted.size();
        }

        @Override public net.minecraft.item.ItemStack getDisplayedItem(SkillTreeInventory inv, int n) {
            IconOptions icon = inv.computeIcon(inv.getCoordinates(n));
            return icon == null ? null : super.getDisplayedItem(inv, new ItemOptions(n, icon));
        }

        @Override public Placeholders getPlaceholders(SkillTreeInventory inv, int n) {
            Placeholders holders = inv.treePlaceholders();
            IntCoords coordinates = inv.getCoordinates(n);
            if (inv.skillTree.isNode(coordinates)) {
                SkillTreeNode node = inv.skillTree.getNode(coordinates); NodeState state = inv.playerData.getSkillTrees().getNodeState(node);
                holders.register("current-level", inv.playerData.getSkillTrees().getNodeLevel(node));
                holders.register("current-state", statusNames.getOrDefault(state, state == null ? NodeState.LOCKED.name() : state.name()));
                holders.register("max-level", node.getMaxLevel()); holders.register("name", node.getName());
                holders.register("max-children", node.getMaxChildren()); holders.register("point-consumed", node.getPointConsumption());
                holders.register("display-type", inv.skillTree.getNodeShape(node).name());
            } else {
                ParentInformation path = inv.skillTree.getPath(coordinates);
                if (path != null) holders.register("display-type", path.getShape(coordinates).name());
            }
            return holders;
        }

        @Override public void onClick(SkillTreeInventory inv, PluginInventory.Click click) {
            if (!(click.actionType() == SlotActionType.PICKUP && click.button() == 0)) return;
            int n = getSlots().indexOf(click.slot()); if (n < 0) return;
            IntCoords coordinates = inv.getCoordinates(n); if (!inv.skillTree.isNode(coordinates)) return;
            SkillTreeNode node = inv.skillTree.getNode(coordinates);
            if (inv.playerData.getSkillTrees().getPointsSpent(inv.skillTree) >= inv.skillTree.getMaxPointSpent()) {
                action(inv, "&cYou have reached the maximum number of points for this skill tree."); return;
            }
            NodeIncrementResult result = inv.playerData.getSkillTrees().increment(node);
            switch (result) {
                case SUCCESS -> { action(inv, "&aUpgraded &6" + node.getName() + "&a to level &6" + inv.playerData.getSkillTrees().getNodeLevel(node)); inv.open(); }
                case PERMISSION_DENIED -> action(inv, "&cYou do not have permission to unlock this node.");
                case LOCKED_NODE -> action(inv, "&cThis skill-tree node is locked.");
                case MAX_LEVEL_REACHED -> action(inv, "&cThis skill-tree node is already maxed out.");
                case NOT_ENOUGH_POINTS -> action(inv, "&cNot enough skill-tree points. Required: " + node.getPointConsumption());
            }
        }
    }

    public final class SkillTreeInventory extends GeneratedInventory {
        private final List<SkillTree> skillTrees;
        private final List<Integer> boardSlots;
        private final PlayerData playerData;
        private final int minSlot, width, height, maxTreeListPage;
        private SkillTree skillTree;
        private int treeListPage;
        private int x, y;

        SkillTreeInventory(PlayerData data, SkillTree initial) {
            super(new Navigator(data.getMMOPlayerData()), SkillTreeViewer.this);
            playerData = data;
            ArrayList<SkillTree> available = new ArrayList<>();
            for (String id : data.getProfess().getSkillTreeIds()) { SkillTree tree = SVFrameMMO.skillTrees().get(id); if (tree != null) available.add(tree); }
            if (initial != null && available.stream().noneMatch(tree -> tree.equals(initial))) available.add(0, initial);
            skillTrees = List.copyOf(available);
            if (skillTrees.isEmpty()) throw new IllegalStateException("Class '" + data.getClassId() + "' has no skill trees");
            skillTree = initial == null ? skillTrees.getFirst() : initial;
            skillTree.resolveStates(data);
            InventoryItem<?> selector = SkillTreeViewer.this.getByFunction("skill-tree");
            maxTreeListPage = selector == null || selector.getSlots().isEmpty() ? 0 : Math.max(0, (skillTrees.size() - 1) / selector.getSlots().size());
            InventoryItem<?> board = SkillTreeViewer.this.getByFunction("skill-tree-node");
            if (board == null || board.getSlots().isEmpty()) throw new IllegalStateException("Skill-tree GUI has no board slots");
            boardSlots = List.copyOf(board.getSlots());
            int min = boardSlots.stream().min(Integer::compareTo).orElse(0), max = boardSlots.stream().max(Integer::compareTo).orElse(min);
            minSlot = min; width = (max - min) % 9; height = (max - min) / 9; x = -width / 2; y = -height / 2;
        }

        @Override public String getRawName() { return guiName.replace("{skill-tree-name}", skillTree.getName()).replace("{skill-tree-id}", skillTree.getId()); }
        public IntCoords getCoordinates(int n) {
            int slot = boardSlots.get(n), deltaX = (slot - minSlot) % 9, deltaY = (slot - minSlot) / 9;
            return new IntCoords(x + deltaX, y + deltaY);
        }
        public SkillTree getSkillTree() { return skillTree; }
        public PlayerData getPlayerData() { return playerData; }
        public int getTreeListPage() { return treeListPage; }
        public int getMaxTreeListPage() { return maxTreeListPage; }

        IconOptions computeIcon(IntCoords coordinates) {
            SkillTreeNode node = skillTree.getNodeOrNull(coordinates);
            if (node != null) {
                NodeState state = playerData.getSkillTrees().getNodeState(node); if (state == null) { skillTree.resolveStates(playerData); state = playerData.getSkillTrees().getNodeState(node); }
                NodeDisplayInfo info = new NodeDisplayInfo(skillTree.getNodeShape(node), state);
                IconOptions icon = DisplayMap.getIcon(info, node.getIcons(), skillTree.getIcons(), icons);
                if (icon == null && state == NodeState.MAXED_OUT)
                    icon = DisplayMap.getIcon(new NodeDisplayInfo(skillTree.getNodeShape(node), NodeState.UNLOCKED), node.getIcons(), skillTree.getIcons(), icons);
                return icon == null ? DisplayMap.DEFAULT_ICON : icon;
            }
            ParentInformation path = skillTree.getPath(coordinates);
            if (path == null) return null;
            PathDisplayInfo info = new PathDisplayInfo(path.getShape(coordinates), skillTree.resolvePathState(playerData, path));
            IconOptions icon = DisplayMap.getIcon(info, skillTree.getIcons(), icons);
            return icon == null ? DisplayMap.DEFAULT_ICON : icon;
        }

        Placeholders treePlaceholders() { return treePlaceholders(skillTree); }
        Placeholders treePlaceholders(SkillTree tree) {
            Placeholders holders = new Placeholders(); int max = tree.getMaxPointSpent();
            holders.register("skill-tree", tree.getName()); holders.register("skill-tree-name", tree.getName()); holders.register("skill-tree-id", tree.getId());
            holders.register("skill-tree-points", playerData.getSkillTrees().getPoints(tree.getId())); holders.register("global-points", playerData.getSkillTrees().getPoints("global"));
            holders.register("realloc-points", playerData.getSkillTreeReallocationPoints()); holders.register("max-point-spent", max == Integer.MAX_VALUE ? "∞" : max);
            holders.register("point-spent", playerData.getSkillTrees().getPointsSpent(tree)); return holders;
        }
    }

    private static List<String> parentsLore(SkillTreeInventory inv, SkillTreeNode node, Collection<SkillTreeNode> parents) {
        ArrayList<String> result = new ArrayList<>();
        for (SkillTreeNode parent : parents) {
            int required = node.getParentNeededLevel(parent); boolean met = inv.playerData.getSkillTrees().getNodeLevel(parent) >= required;
            result.add("§7◆" + parent.getName() + ": " + (met ? "§a" : "§c") + required);
        }
        return result;
    }
    private static void action(SkillTreeInventory inv, String message) { inv.getPlayer().sendMessage(net.minecraft.text.Text.literal(SVFrameLib.inst().parseColors(message)), true); }
    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT).replace('_','-').replace(' ','-'); }
    private static String key(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT).replace('_','-'); }
    private static Object get(Map<String,Object> map, String key) { for (var entry : map.entrySet()) if (normalize(entry.getKey()).equals(normalize(key))) return entry.getValue(); return null; }
    private static Map<String,Object> map(Object raw) { if (!(raw instanceof Map<?,?> source)) return Map.of(); LinkedHashMap<String,Object> out=new LinkedHashMap<>(); source.forEach((k,v)->out.put(String.valueOf(k),v)); return out; }
    private static List<String> strings(Object raw) { if (!(raw instanceof Iterable<?> values)) return raw==null?List.of():List.of(String.valueOf(raw)); ArrayList<String> out=new ArrayList<>(); for(Object v:values) if(v!=null) out.add(String.valueOf(v)); return List.copyOf(out); }
}
