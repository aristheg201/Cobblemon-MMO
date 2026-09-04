package vn.svframe.svframemmo.skilltree;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.gui.util.IconOptions;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.skilltree.display.DisplayMap;
import vn.svframe.svframemmo.skilltree.display.NodeShape;
import vn.svframe.svframemmo.skilltree.display.PathState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Native MMOCore-compatible passive skill tree including render geometry and display fallbacks. */
public final class SkillTree {
    private final String id;
    private final String name;
    private final String type;
    private final int maxPointSpent;
    private final List<String> lore;
    private final IconOptions icon;
    private final DisplayMap icons;
    private final Map<String, SkillTreeNode> nodes = new LinkedHashMap<>();
    private final Map<IntCoords, SkillTreeNode> byCoordinates = new LinkedHashMap<>();
    private final Map<IntCoords, ParentInformation> pathByCoordinates = new LinkedHashMap<>();
    private final Map<SkillTreeNode, NodeShape> nodeShapes = new LinkedHashMap<>();
    private final Set<SkillTreeNode> roots = new LinkedHashSet<>();

    public SkillTree(Map<String, Object> config) {
        this.id = normalize(String.valueOf(Objects.requireNonNull(config.get("id"), "Missing skill tree id")));
        this.name = colors(String.valueOf(Objects.requireNonNull(config.get("name"), "Missing skill tree name")));
        this.type = String.valueOf(config.getOrDefault("type", "CUSTOM")).trim().toUpperCase(Locale.ROOT);
        this.maxPointSpent = integer(config.get("max-point-spent"), Integer.MAX_VALUE);
        this.lore = coloredList(config.get("lore"));
        this.icon = IconOptions.from(config.containsKey("icon") ? config.get("icon") : config);
        this.icons = DisplayMap.from(config.get("display"));

        Map<String, Object> rawNodes = map(config.get("nodes"));
        if (rawNodes.isEmpty()) throw new IllegalArgumentException("Skill tree has no nodes: " + id);
        for (Map.Entry<String, Object> entry : rawNodes.entrySet()) {
            SkillTreeNode node = new SkillTreeNode(this, entry.getKey(), map(entry.getValue()));
            if (nodes.putIfAbsent(node.getId(), node) != null) throw new IllegalArgumentException("Duplicate node: " + node.getId());
            if (byCoordinates.putIfAbsent(node.getCoordinates(), node) != null) throw new IllegalArgumentException("Duplicate node coordinates: " + node.getCoordinates());
            if (node.isRoot()) roots.add(node);
        }
        for (Map.Entry<String, Object> entry : rawNodes.entrySet()) loadRelations(nodes.get(normalize(entry.getKey())), map(entry.getValue()));
        if (type.equals("PROXIMITY")) loadProximityRelations();
        else if (!type.equals("CUSTOM")) throw new IllegalArgumentException("Unknown skill tree type: " + type);
        if (type.equals("CUSTOM")) for (SkillTreeNode node : nodes.values()) if (node.getParents().isEmpty()) { node.setRoot(); roots.add(node); }

        for (SkillTreeNode node : nodes.values()) for (ParentInformation edge : node.getParents())
            for (IntCoords coordinates : edge.getElements()) {
                ParentInformation previous = pathByCoordinates.putIfAbsent(coordinates, edge);
                if (previous != null && previous != edge) throw new IllegalArgumentException("Overlapping skill tree paths at " + coordinates + " in " + id);
            }
        for (SkillTreeNode node : nodes.values()) nodeShapes.put(node, resolveNodeShape(node));
    }

    private void loadRelations(SkillTreeNode node, Map<String, Object> config) {
        loadSide(node, map(config.get("parents")), false);
        loadSide(node, map(config.get("children")), true);
    }

    private void loadSide(SkillTreeNode node, Map<String, Object> section, boolean nodeIsParent) {
        for (Map.Entry<String, Object> typeEntry : section.entrySet()) {
            ParentType type = ParentType.valueOf(enumName(typeEntry.getKey()));
            for (Map.Entry<String, Object> relativeEntry : map(typeEntry.getValue()).entrySet()) {
                SkillTreeNode relative = getNode(relativeEntry.getKey());
                SkillTreeNode child = nodeIsParent ? relative : node;
                SkillTreeNode parent = nodeIsParent ? node : relative;
                ParentInformation existing = findEdge(child, parent, type);
                Relation relation = relation(relativeEntry.getValue());
                if (existing == null) child.addParent(new ParentInformation(child, parent, type, false, relation.level(), relation.paths()));
                else for (Object path : relation.paths()) {
                    IntCoords coords = IntCoords.from(path);
                    if (!existing.getElements().contains(coords)) existing.addElement(coords);
                }
            }
        }
    }

    private void loadProximityRelations() {
        IntCoords[] offsets = { new IntCoords(1,0), new IntCoords(-1,0), new IntCoords(0,1), new IntCoords(0,-1) };
        for (SkillTreeNode node : nodes.values()) for (IntCoords offset : offsets) {
            SkillTreeNode neighbor = byCoordinates.get(node.getCoordinates().add(offset));
            if (neighbor != null && findEdge(node, neighbor, ParentType.SOFT) == null)
                node.addParent(new ParentInformation(node, neighbor, ParentType.SOFT, true, 1));
        }
    }

    private ParentInformation findEdge(SkillTreeNode child, SkillTreeNode parent, ParentType type) {
        for (ParentInformation edge : child.getParents()) if (edge.getParent() == parent && edge.getType() == type) return edge;
        return null;
    }

    private NodeShape resolveNodeShape(SkillTreeNode node) {
        IntCoords coordinates = node.getCoordinates();
        boolean up = occupied(coordinates.offset(0,-1)), down = occupied(coordinates.offset(0,1));
        boolean right = occupied(coordinates.offset(1,0)), left = occupied(coordinates.offset(-1,0));
        if (up && right && down && left) return NodeShape.UP_RIGHT_DOWN_LEFT;
        if (up && right && down) return NodeShape.UP_RIGHT_DOWN;
        if (up && right && left) return NodeShape.UP_RIGHT_LEFT;
        if (up && down && left) return NodeShape.UP_DOWN_LEFT;
        if (down && right && left) return NodeShape.DOWN_RIGHT_LEFT;
        if (up && right) return NodeShape.UP_RIGHT;
        if (up && down) return NodeShape.UP_DOWN;
        if (up && left) return NodeShape.UP_LEFT;
        if (down && right) return NodeShape.DOWN_RIGHT;
        if (down && left) return NodeShape.DOWN_LEFT;
        if (right && left) return NodeShape.RIGHT_LEFT;
        if (right) return NodeShape.RIGHT;
        if (left) return NodeShape.LEFT;
        if (down) return NodeShape.DOWN;
        if (up) return NodeShape.UP;
        return NodeShape.NO_PATH;
    }

    private boolean occupied(IntCoords coordinates) { return byCoordinates.containsKey(coordinates) || pathByCoordinates.containsKey(coordinates); }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public int getMaxPointSpent() { return maxPointSpent; }
    public List<String> getLore() { return lore; }
    public IconOptions getIcon() { return icon; }
    public DisplayMap getIcons() { return icons; }
    public Collection<SkillTreeNode> getNodes() { return List.copyOf(nodes.values()); }
    public List<SkillTreeNode> getRoots() { return List.copyOf(roots); }
    public SkillTreeNode getNode(String id) {
        SkillTreeNode node = nodes.get(normalize(id));
        if (node == null) throw new IllegalArgumentException("Unknown node '" + id + "' in tree '" + this.id + "'");
        return node;
    }
    public SkillTreeNode getNode(IntCoords coordinates) {
        SkillTreeNode node = byCoordinates.get(coordinates);
        if (node == null) throw new IllegalArgumentException("Unknown node coordinates " + coordinates + " in tree '" + id + "'");
        return node;
    }
    public SkillTreeNode getNodeOrNull(IntCoords coordinates) { return byCoordinates.get(coordinates); }
    public boolean isNode(IntCoords coordinates) { return byCoordinates.containsKey(coordinates); }
    public ParentInformation getPath(IntCoords coordinates) { return pathByCoordinates.get(coordinates); }
    public boolean isPath(IntCoords coordinates) { return pathByCoordinates.containsKey(coordinates); }
    public NodeShape getNodeShape(SkillTreeNode node) {
        NodeShape shape = nodeShapes.get(node);
        if (shape == null) throw new IllegalArgumentException("Missing node shape for " + node.getFullId());
        return shape;
    }
    public void resolveStates(PlayerData data) { data.getSkillTrees().resolveStates(this); }

    public PathState resolvePathState(PlayerData data, ParentInformation edge) {
        NodeState from = data.getSkillTrees().getNodeState(edge.getParent());
        NodeState to = data.getSkillTrees().getNodeState(edge.getChild());
        boolean symmetrical = edge.isSymmetrical();
        if (to == NodeState.FULLY_LOCKED || (symmetrical && from == NodeState.FULLY_LOCKED)) return PathState.FULLY_LOCKED;
        if (from.isUnlocked() && to.isUnlocked()) return PathState.UNLOCKED;
        if ((from.isUnlocked() && to == NodeState.UNLOCKABLE) || (symmetrical && to.isUnlocked() && from == NodeState.UNLOCKABLE)) return PathState.UNLOCKABLE;
        return PathState.LOCKED;
    }

    private static Relation relation(Object raw) {
        if (raw instanceof Number number) return new Relation(Math.max(1, number.intValue()), List.of());
        if (raw instanceof List<?> list) return new Relation(1, List.copyOf(list));
        Map<String, Object> map = map(raw);
        if (!map.isEmpty()) return new Relation(integer(map.get("level"), 1), list(map.get("paths")));
        if (raw == null) return new Relation(1, List.of());
        throw new IllegalArgumentException("Invalid skill tree relation: " + raw);
    }
    private record Relation(int level, List<?> paths) { }

    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-'); }
    private static String enumName(String value) { return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }
    private static String colors(String value) { return SVFrameLib.inst().parseColors(value); }
    private static int integer(Object value, int fallback) {
        try { return value instanceof Number number ? number.intValue() : value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static List<?> list(Object raw) { return raw instanceof List<?> list ? List.copyOf(list) : raw == null ? List.of() : List.of(raw); }
    private static List<String> coloredList(Object raw) {
        List<?> input = list(raw); ArrayList<String> out = new ArrayList<>(input.size());
        for (Object line : input) out.add(colors(String.valueOf(line))); return List.copyOf(out);
    }
    private static Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) return Map.of();
        LinkedHashMap<String, Object> out = new LinkedHashMap<>(); source.forEach((key,value) -> out.put(String.valueOf(key), value)); return out;
    }

    @Override public boolean equals(Object object) { return object instanceof SkillTree other && id.equals(other.id); }
    @Override public int hashCode() { return id.hashCode(); }
}
