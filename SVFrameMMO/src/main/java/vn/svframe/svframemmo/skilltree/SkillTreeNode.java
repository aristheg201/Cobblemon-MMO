package vn.svframe.svframemmo.skilltree;

import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.manager.ExperienceTableManager;

import java.util.ArrayList;
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
    private final List<ParentInformation> parents = new ArrayList<>();
    private final List<ParentInformation> children = new ArrayList<>();
    private boolean root;

    SkillTreeNode(SkillTree tree, String id, Map<String, Object> config) {
        this.tree = Objects.requireNonNull(tree, "tree");
        this.id = normalize(id);
        this.name = String.valueOf(Objects.requireNonNull(config.get("name"), "Missing node name for " + id));
        this.root = bool(config.get("root"), bool(config.get("is-root"), false));
        this.pointConsumption = integer(config.get("point-consumed"), 1);
        if (pointConsumption <= 0) throw new IllegalArgumentException("Node point consumption must be positive: " + getFullId());
        this.permissionRequired = config.get("permission-required") == null ? null : String.valueOf(config.get("permission-required"));
        this.maxLevel = integer(config.get("max-level"), 1);
        if (maxLevel <= 0) throw new IllegalArgumentException("Node max-level must be positive: " + getFullId());
        this.maxChildren = integer(config.get("max-children"), 0);
        if (maxChildren < 0) throw new IllegalArgumentException("Node max-children must be non-negative: " + getFullId());
        this.coordinates = IntCoords.from(config.get("coordinates"));
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
    public ExperienceTableManager.ExperienceTable getExperienceTable() { return experienceTable; }
    public boolean hasPermissionRequirement(PlayerData data) { return permissionRequired == null || SVFrameMMO.permissions().has(data.getPlayer(), permissionRequired); }

    void addParent(ParentInformation edge) {
        if (edge.getChild() != this) throw new IllegalArgumentException("Parent edge child mismatch");
        parents.add(edge);
        edge.getParent().children.add(edge);
    }

    private static int integer(Object v, int fallback) {
        try { return v instanceof Number n ? n.intValue() : v == null ? fallback : Integer.parseInt(String.valueOf(v)); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean bool(Object v, boolean fallback) { return v == null ? fallback : v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v)); }
    private static String normalize(String v) { return v.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-'); }
}
