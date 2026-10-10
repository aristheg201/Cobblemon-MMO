package vn.svframe.svframemmo.skilltree;

import vn.svframe.svframemmo.skilltree.display.PathShape;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A typed skill-tree edge plus its optional rendered path elements. */
public final class ParentInformation {
    private final SkillTreeNode child;
    private final SkillTreeNode parent;
    private final ParentType type;
    private final boolean reciprocal;
    private final int minLevel;
    private final Map<IntCoords, PathShape> elements = new LinkedHashMap<>();

    public ParentInformation(SkillTreeNode child, SkillTreeNode parent, ParentType type, boolean reciprocal, int minLevel) {
        this(child, parent, type, reciprocal, minLevel, List.of());
    }

    public ParentInformation(SkillTreeNode child, SkillTreeNode parent, ParentType type,
                             boolean reciprocal, int minLevel, Collection<?> pathElements) {
        this.child = Objects.requireNonNull(child, "child");
        this.parent = Objects.requireNonNull(parent, "parent");
        this.type = Objects.requireNonNull(type, "type");
        this.reciprocal = reciprocal;
        this.minLevel = Math.max(1, minLevel);
        if (pathElements != null) for (Object raw : pathElements) if (raw != null) elements.put(IntCoords.from(raw), null);
        recomputeShapes();
    }

    public SkillTreeNode getChild() { return child; }
    public SkillTreeNode getParent() { return parent; }
    public ParentType getType() { return type; }
    public boolean isSymmetrical() { return reciprocal; }
    public int getLevel() { return minLevel; }
    public Set<IntCoords> getElements() { return Set.copyOf(elements.keySet()); }
    public PathShape getShape(IntCoords coordinates) {
        PathShape shape = elements.get(coordinates);
        if (shape == null) throw new IllegalArgumentException("No path element at " + coordinates + " for " + parent.getId() + " -> " + child.getId());
        return shape;
    }

    public void addElement(IntCoords coordinates) {
        if (elements.putIfAbsent(Objects.requireNonNull(coordinates, "coordinates"), null) != null)
            throw new IllegalArgumentException("Duplicate path element at " + coordinates);
        recomputeShapes();
    }

    private void recomputeShapes() {
        for (IntCoords element : List.copyOf(elements.keySet())) elements.put(element, computePathShape(element));
    }

    private PathShape computePathShape(IntCoords coordinates) {
        IntCoords up = coordinates.offset(0, -1), down = coordinates.offset(0, 1);
        IntCoords right = coordinates.offset(1, 0), left = coordinates.offset(-1, 0);
        boolean hasUp = elements.containsKey(up) || up.equals(parent.getCoordinates()) || up.equals(child.getCoordinates());
        boolean hasDown = elements.containsKey(down) || down.equals(parent.getCoordinates()) || down.equals(child.getCoordinates());
        boolean hasRight = elements.containsKey(right) || right.equals(parent.getCoordinates()) || right.equals(child.getCoordinates());
        boolean hasLeft = elements.containsKey(left) || left.equals(parent.getCoordinates()) || left.equals(child.getCoordinates());
        if ((hasUp || hasDown) && !hasLeft && !hasRight) return PathShape.UP;
        if ((hasRight || hasLeft) && !hasUp && !hasDown) return PathShape.RIGHT;
        if (hasUp && hasRight) return PathShape.UP_RIGHT;
        if (hasUp && hasLeft) return PathShape.UP_LEFT;
        if (hasDown && hasRight) return PathShape.DOWN_RIGHT;
        if (hasDown && hasLeft) return PathShape.DOWN_LEFT;
        return PathShape.DEFAULT;
    }

    @Override public boolean equals(Object object) {
        return object instanceof ParentInformation other && child.equals(other.child) && parent.equals(other.parent) && type == other.type;
    }
    @Override public int hashCode() { return Objects.hash(child, parent, type); }
}
