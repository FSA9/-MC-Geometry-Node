package com.mine.geometry_node.core.engine.blueprint.spatial.forceField;

import org.jetbrains.annotations.Nullable;

public enum ForceFieldMode {
    ATTRACT("attract", 1.0D),
    REPEL("repel", -1.0D);

    public static final String[] OPTIONS = {ATTRACT.id, REPEL.id};

    private final String id;
    private final double directionSign;

    ForceFieldMode(String id, double directionSign) {
        this.id = id;
        this.directionSign = directionSign;
    }

    public String id() {
        return id;
    }

    public double directionSign() {
        return directionSign;
    }

    public static ForceFieldMode fromId(@Nullable String id) {
        if (id != null) {
            for (ForceFieldMode mode : values()) {
                if (mode.id.equalsIgnoreCase(id)) return mode;
            }
        }
        return ATTRACT;
    }
}
