package vn.svframe.svframemmo.skilltree;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.gui.editable.placeholder.Placeholders;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.manager.ExperienceTableManager;
import vn.svframe.svframemmo.skilltree.display.DisplayMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class SkillTreeNode {
    public static final String KEY_PREFIX = "node";
    private final SkillTree tree;
    private final String id;
    private final String name;
    private final String permissionRequired;
    private final int pointConsumption;
    private final IntCoords coordinates;
    private final int maxLevel;
    private final int maxChildren;
    private final ExperienceTableManager.ExperienceTable experienceTable;
    private final DisplayMap icons;
    private final Map<Integer, List<String>> lores = new LinkedHashMap<>();
    private final List<ParentInformation> parents = new ArrayList<>();
    private final List<ParentInformation> children = new ArrayList<>();
    private boolean root;

    SkillTreeNode(SkillTree tree, String id, Map<String, Object> config) {
        this.tree = Objects.requireNonNull(tree, "tree");
        this.id = normalize(id);
        this.name = colors(String.valueOf(Objects.requireNonNull(config.get("name"), "Missing node name for " + id)));
        this.root = bool(config.get("root"), bool(config.get("is-root"), false));
        this.pointConsumption = integer(config.get("point-consumed"), 1);
        if (pointConsumption <= 0) throw new IllegalArgumentException("Node point consumption must be positive: " + getFullId());
        this.permissionRequired = config.get("permission-required") == null ? null : String.valueOf(config.get("permission-required"));
        this.maxLevel = integer(config.get("max-level"), 1);
        if (maxLevel <= 0) throw new IllegalArgumentException("Node max-level must be positive: " + getFullId());
        this.maxChildren = integer(config.get("max-children"), 0);
        if (maxChildren < 0) throw new IllegalArgumentException("Node max-children must be non-negative: " + getFullId());
        this.coordinates = IntCoords.from(config.get("coordinates"));
        this.icons = DisplayMap.from(config.get("display"));
        Map<String, Object> rawLores = map(config.get("lores"));
        for (Map.Entry<String, Object> entry : rawLores.entrySet()) {
            int level;
            try { level = Integer.parseInt(entry.getKey()); }
            catch (NumberFormatException exception) { throw new IllegalArgumentException("Node lore keys must be integers: " + getFullId()); }
            lores.put(level, coloredList(entry.getValue()));
        }
        if (!config.containsKey("experience-table")) throw new IllegalArgumentException("Missing experience-table for " + getFullId());
        this.experienceTable = SVFrameMMO.experienceTables().parseInline(getFullId(), config.get("experience-table"));
    }

    public SkillTree getTree() { return tree; }
    public String getId() { return id; }
    public String getFullId() { return tree.getId() + "_" + id; }
    public String getKey() { return KEY_PREFIX + ":" + getFullId().replace('-', '_'); }
    public String getName() { return name; }
    public boolean isRoot() { return root; }
    void setRoot() { root = true; }
    public int getPointConsumption() { return pointConsumption; }
    public IntCoords getCoordinates() { return coordinates; }
    public int getMaxLevel() { return maxLevel; }
    public int getMaxChildren() { return maxChildren; }
    public List<ParentInformation> getParents() { return List.copyOf(parents); }
    public List<ParentInformation> getChildren() { return List.copyOf(children); }
    public List<SkillTreeNode> getParents(ParentType type) { return parents.stream().filter(edge -> edge.getType() == type).map(ParentInformation::getParent).toList(); }
    public ExperienceTableManager.ExperienceTable getExperienceTable() { return experienceTable; }
    public DisplayMap getIcons() { return icons; }
    public boolean hasPermissionRequirement(PlayerData data) { return permissionRequired == null || SVFrameMMO.permissions().has(data.getPlayer(), permissionRequired); }
    public int getParentNeededLevel(SkillTreeNode parent) {
        for (ParentInformation edge : parents) if (edge.getParent().equals(parent)) return edge.getLevel();
        throw new IllegalArgumentException("Node " + parent.getId() + " is not a parent of " + id);
    }

    public List<String> getLore(PlayerData data) {
        int level = data.getSkillTrees().getNodeLevel(this);
        for (int candidate = level; candidate >= 0; candidate--) {
            List<String> found = lores.get(candidate);
            if (found == null) continue;
            Placeholders placeholders = new Placeholders();
            placeholders.register("name", getName());
            placeholders.register("node-state", data.getSkillTrees().getNodeState(this));
            placeholders.register("level", level);
            placeholders.register("max-level", getMaxLevel());
            placeholders.register("max-children", getMaxChildren());
            ArrayList<String> out = new ArrayList<>(found.size());
            for (String line : found) out.add(colors(placeholders.apply(data.getPlayer(), line)));
            return List.copyOf(out);
        }
        return List.of();
    }

    void addParent(ParentInformation edge) {
        if (edge.getChild() != this) throw new IllegalArgumentException("Parent edge child mismatch");
        parents.add(edge);
        edge.getParent().children.add(edge);
    }

    private static int integer(Object value, int fallback) {
        try { return value instanceof Number number ? number.intValue() : value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean bool(Object value, boolean fallback) { return value == null ? fallback : value instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(value)); }
    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-'); }
    private static String colors(String value) { return SVFrameLib.inst().parseColors(value); }
    private static List<String> coloredList(Object raw) {
        if (!(raw instanceof List<?> list)) return raw == null ? List.of() : List.of(colors(String.valueOf(raw)));
        ArrayList<String> out = new ArrayList<>(list.size()); for (Object line : list) out.add(colors(String.valueOf(line))); return List.copyOf(out);
    }
    private static Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) return Map.of(); LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        source.forEach((key,value) -> out.put(String.valueOf(key), value)); return out;
    }

    @Override public boolean equals(Object object) { return object instanceof SkillTreeNode other && tree.equals(other.tree) && id.equals(other.id); }
    @Override public int hashCode() { return Objects.hash(tree, id); }
}
