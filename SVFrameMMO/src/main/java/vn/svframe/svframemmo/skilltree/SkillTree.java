package vn.svframe.svframemmo.skilltree;

import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class SkillTree {
    private final String id;
    private final String name;
    private final String type;
    private final int maxPointSpent;
    private final Map<String, SkillTreeNode> nodes = new LinkedHashMap<>();
    private final Map<IntCoords, SkillTreeNode> byCoordinates = new LinkedHashMap<>();

    public SkillTree(Map<String, Object> config) {
        this.id = normalize(String.valueOf(Objects.requireNonNull(config.get("id"), "Missing skill tree id")));
        this.name = String.valueOf(Objects.requireNonNull(config.get("name"), "Missing skill tree name"));
        this.type = String.valueOf(config.getOrDefault("type", "CUSTOM")).trim().toUpperCase(Locale.ROOT);
        this.maxPointSpent = integer(config.get("max-point-spent"), Integer.MAX_VALUE);
        Map<String, Object> rawNodes = map(config.get("nodes"));
        if (rawNodes.isEmpty()) throw new IllegalArgumentException("Skill tree has no nodes: " + id);
        for (var entry : rawNodes.entrySet()) {
            SkillTreeNode node = new SkillTreeNode(this, entry.getKey(), map(entry.getValue()));
            if (nodes.putIfAbsent(node.getId(), node) != null) throw new IllegalArgumentException("Duplicate node: " + node.getId());
            if (byCoordinates.putIfAbsent(node.getCoordinates(), node) != null) throw new IllegalArgumentException("Duplicate node coordinates: " + node.getCoordinates());
        }
        for (var entry : rawNodes.entrySet()) loadRelations(nodes.get(normalize(entry.getKey())), map(entry.getValue()));
        if (type.equals("PROXIMITY")) loadProximityRelations();
        else if (!type.equals("CUSTOM")) throw new IllegalArgumentException("Unknown skill tree type: " + type);
        if (type.equals("CUSTOM")) for (SkillTreeNode node : nodes.values()) if (node.getParents().isEmpty()) node.setRoot();
    }

    private void loadRelations(SkillTreeNode node, Map<String, Object> config) {
        loadSide(node, map(config.get("parents")), false);
        loadSide(node, map(config.get("children")), true);
    }
    private void loadSide(SkillTreeNode node, Map<String, Object> section, boolean nodeIsParent) {
        for (var typeEntry : section.entrySet()) {
            ParentType type = ParentType.valueOf(typeEntry.getKey().trim().toUpperCase(Locale.ROOT));
            for (var relativeEntry : map(typeEntry.getValue()).entrySet()) {
                SkillTreeNode relative = getNode(relativeEntry.getKey());
                SkillTreeNode child = nodeIsParent ? relative : node;
                SkillTreeNode parent = nodeIsParent ? node : relative;
                int level = relationLevel(relativeEntry.getValue());
                if (!hasEdge(child, parent, type)) child.addParent(new ParentInformation(child, parent, type, false, level));
            }
        }
    }
    private void loadProximityRelations() {
        IntCoords[] offsets = { new IntCoords(1,0), new IntCoords(-1,0), new IntCoords(0,1), new IntCoords(0,-1) };
        for (SkillTreeNode node : nodes.values()) for (IntCoords offset : offsets) {
            SkillTreeNode neighbor = byCoordinates.get(node.getCoordinates().add(offset));
            if (neighbor != null && !hasEdge(node, neighbor, ParentType.SOFT))
                node.addParent(new ParentInformation(node, neighbor, ParentType.SOFT, true, 1));
        }
    }
    private boolean hasEdge(SkillTreeNode child, SkillTreeNode parent, ParentType type) {
        for (ParentInformation edge : child.getParents()) if (edge.getParent() == parent && edge.getType() == type) return true;
        return false;
    }
    private static int relationLevel(Object raw) {
        if (raw instanceof Number n) return Math.max(1, n.intValue());
        if (raw instanceof Map<?, ?> map) return Math.max(1, integer(map.get("level"), 1));
        if (raw instanceof List<?>) return 1;
        throw new IllegalArgumentException("Invalid skill tree relation: " + raw);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getMaxPointSpent() { return maxPointSpent; }
    public Collection<SkillTreeNode> getNodes() { return List.copyOf(nodes.values()); }
    public SkillTreeNode getNode(String id) {
        SkillTreeNode node = nodes.get(normalize(id));
        if (node == null) throw new IllegalArgumentException("Unknown node '" + id + "' in tree '" + this.id + "'");
        return node;
    }
    public SkillTreeNode getNode(IntCoords coordinates) { return byCoordinates.get(coordinates); }
    public void resolveStates(PlayerData data) { data.getSkillTrees().resolveStates(this); }

    private static String normalize(String v) { return v.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-'); }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return Map.of();
        LinkedHashMap<String,Object> out=new LinkedHashMap<>(); m.forEach((k,v)->out.put(String.valueOf(k),v)); return out;
    }
    private static int integer(Object v,int fallback){try{return v instanceof Number n?n.intValue():v==null?fallback:Integer.parseInt(String.valueOf(v));}catch(RuntimeException e){return fallback;}}
}
