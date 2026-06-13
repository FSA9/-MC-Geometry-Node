package com.mine.geometry_node.core.engine.blueprint.spatial;

import org.jetbrains.annotations.Nullable;

public enum AreaShape {
    BOX("box"),
    SPHERE("sphere"),
    CYLINDER("cylinder");

    public static final String[] OPTIONS = {BOX.id, SPHERE.id, CYLINDER.id};

    private final String id;

    AreaShape(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static AreaShape fromId(@Nullable String id) {
        if (id != null) {
            for (AreaShape shape : values()) {
                if (shape.id.equalsIgnoreCase(id)) {
                    return shape;
                }
            }
        }
        return BOX;
    }
}
