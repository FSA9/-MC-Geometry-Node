package com.mine.geometry_node.core.engine.system.model.domain;

import java.util.Objects;

/** Canonical vertex attribute semantic, including the set index for indexed semantics. */
public record ModelAttributeSemantic(Kind kind, int setIndex) implements Comparable<ModelAttributeSemantic> {
    public enum Kind { POSITION, NORMAL, TANGENT, TEXCOORD, COLOR, JOINTS, WEIGHTS }

    public static final ModelAttributeSemantic POSITION = unindexed(Kind.POSITION);
    public static final ModelAttributeSemantic NORMAL = unindexed(Kind.NORMAL);
    public static final ModelAttributeSemantic TANGENT = unindexed(Kind.TANGENT);
    public static final ModelAttributeSemantic TEXCOORD_0 = indexed(Kind.TEXCOORD, 0);
    public static final ModelAttributeSemantic COLOR_0 = indexed(Kind.COLOR, 0);
    public static final ModelAttributeSemantic JOINTS_0 = indexed(Kind.JOINTS, 0);
    public static final ModelAttributeSemantic WEIGHTS_0 = indexed(Kind.WEIGHTS, 0);

    public ModelAttributeSemantic {
        Objects.requireNonNull(kind, "kind");
        boolean indexed = switch (kind) {
            case TEXCOORD, COLOR, JOINTS, WEIGHTS -> true;
            default -> false;
        };
        if (indexed != (setIndex >= 0)) throw new IllegalArgumentException("invalid set index for " + kind);
    }

    public static ModelAttributeSemantic unindexed(Kind kind) { return new ModelAttributeSemantic(kind, -1); }
    public static ModelAttributeSemantic indexed(Kind kind, int setIndex) {
        if (setIndex < 0) throw new IllegalArgumentException("setIndex must not be negative");
        return new ModelAttributeSemantic(kind, setIndex);
    }

    public boolean is(Kind expected) { return kind == expected; }

    @Override public int compareTo(ModelAttributeSemantic other) {
        int kindOrder = Integer.compare(kind.ordinal(), other.kind.ordinal());
        return kindOrder != 0 ? kindOrder : Integer.compare(setIndex, other.setIndex);
    }

    @Override public String toString() { return setIndex < 0 ? kind.name() : kind.name() + "_" + setIndex; }
}
