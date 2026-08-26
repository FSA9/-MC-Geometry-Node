package com.mine.geometry_node.client.ui.surface;

import java.util.HashSet;
import java.util.Set;

/** Public UI surface kinds and their unique human-readable prefixes. */
public enum UiSurfaceType {
    VIEWPORT("V"),
    TERMINAL("T"),
    ASSET_BROWSER("A"),
    PERFORMANCE("PF");

    private final String prefix;

    UiSurfaceType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    static {
        Set<String> prefixes = new HashSet<>();
        for (UiSurfaceType type : values()) {
            if (!prefixes.add(type.prefix)) {
                throw new IllegalStateException("Duplicate UI surface prefix: " + type.prefix);
            }
        }
    }
}
