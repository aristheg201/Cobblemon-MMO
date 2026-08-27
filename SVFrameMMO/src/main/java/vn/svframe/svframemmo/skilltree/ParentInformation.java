package vn.svframe.svframemmo.skilltree;

import java.util.Objects;

public final class ParentInformation {
    private final SkillTreeNode child;
    private final SkillTreeNode parent;
    private final ParentType type;
    private final boolean reciprocal;
    private final int minLevel;

    public ParentInformation(SkillTreeNode child, SkillTreeNode parent, ParentType type, boolean reciprocal, int minLevel) {
        this.child = Objects.requireNonNull(child, "child");
        this.parent = Objects.requireNonNull(parent, "parent");
        this.type = Objects.requireNonNull(type, "type");
        this.reciprocal = reciprocal;
        this.minLevel = Math.max(1, minLevel);
    }
    public SkillTreeNode getChild() { return child; }
    public SkillTreeNode getParent() { return parent; }
    public ParentType getType() { return type; }
    public boolean isSymmetrical() { return reciprocal; }
    public int getLevel() { return minLevel; }
}
