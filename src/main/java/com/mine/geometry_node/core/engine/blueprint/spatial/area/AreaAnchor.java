package com.mine.geometry_node.core.engine.blueprint.spatial.area;

import org.jetbrains.annotations.Nullable;

public enum AreaAnchor {
    WORLD("world"),
    OWNER("owner");

    public static final String[] OPTIONS = {WORLD.id, OWNER.id};

    private final String id;

    AreaAnchor(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static AreaAnchor fromId(@Nullable String id) {
        if (id != null) {
            for (AreaAnchor anchor : values()) {
                if (anchor.id.equalsIgnoreCase(id)) {
                    return anchor;
                }
            }
        }
        return WORLD;
    }
}
