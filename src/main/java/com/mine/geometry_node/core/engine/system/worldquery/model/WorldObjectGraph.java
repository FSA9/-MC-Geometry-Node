package com.mine.geometry_node.core.engine.system.worldquery.model;

import com.mine.geometry_node.core.utils.ColumnFilters;

/** Bounded presentation metadata for one existing host binding; no graph contents or tags. */
public record WorldObjectGraph(String id, String type, String name) {
    public WorldObjectGraph {
        WorldObjectEntry.validateText(id);
        WorldObjectEntry.validateText(type);
        WorldObjectEntry.validateText(name);
        if (id.isEmpty() || type.isEmpty()) throw new IllegalArgumentException("Missing bound graph identity");
    }

    public int estimatedBytes() {
        return ColumnFilters.wireTextBytes(id) + ColumnFilters.wireTextBytes(type) + ColumnFilters.wireTextBytes(name);
    }
}
