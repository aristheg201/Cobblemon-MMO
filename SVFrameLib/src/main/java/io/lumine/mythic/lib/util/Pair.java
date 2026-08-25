package io.lumine.mythic.lib.util;

import java.util.Objects;

public final class Pair<L,R> {
    private final L left;
    private final R right;
    private Pair(L left,R right){this.left=left;this.right=right;}
    public L getLeft(){return left;}
    public R getRight(){return right;}
    public L getKey(){return left;}
    public R getValue(){return right;}
    public static <L,R> Pair<L,R> of(L left,R right){return new Pair<>(left,right);}
    @Override public boolean equals(Object o){return o instanceof Pair<?,?> p&&Objects.equals(left,p.left)&&Objects.equals(right,p.right);}
    @Override public int hashCode(){return Objects.hash(left,right);}
    @Override public String toString(){return "("+left+","+right+")";}
}
