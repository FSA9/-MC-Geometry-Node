package com.mine.geometry_node.core.engine.system.worldquery.model;

import java.util.Collection;
import java.util.Locale;

/** Both predicates must match the same bound graph. Empty fields are unrestricted. */
public record WorldObjectGraphFilter(String type, String name) {
    public WorldObjectGraphFilter {
        for (String text : new String[]{type, name})
            if (text == null || text.length() > WorldObjectQuery.MAX_FILTER_TEXT)
                throw new IllegalArgumentException("World query graph filter too long");
    }

    public static WorldObjectGraphFilter empty() { return new WorldObjectGraphFilter("", ""); }
    public boolean active() { return !type.isEmpty() || !name.isEmpty(); }
    public boolean matches(Collection<WorldObjectGraph> graphs) {
        if (!active()) return true;
        String keyword = name.toLowerCase(Locale.ROOT);
        for (var graph : graphs)
            if ((type.isEmpty() || type.equals(graph.type()))
                    && (name.isEmpty() || (graph.name().isEmpty() ? graph.id() : graph.name())
                        .toLowerCase(Locale.ROOT).contains(keyword))) return true;
        return false;
    }
}
