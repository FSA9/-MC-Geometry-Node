package com.mine.geometry_node.core.engine.graph;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * High-level graph family. A graph kind selects runtime semantics,
 * not the visual node editor format.
 */
public enum GraphKind {
    BLUEPRINT("blueprint"),
    BEHAVIOR_TREE("behavior_tree"),
    UNKNOWN("unknown");

    private final String id;

    GraphKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static GraphKind fromId(@Nullable String id) {
        if (id == null || id.isBlank()) return UNKNOWN;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (GraphKind kind : values()) {
            if (kind.id.equals(normalized)) {
                return kind;
            }
        }
        return UNKNOWN;
    }
}
