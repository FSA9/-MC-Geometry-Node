package com.mine.geometry_node.core.engine.system.data.library;

import java.util.LinkedHashSet;
import java.util.List;

/** Canonical tag representation shared by persistence and editor models. */
final class DataLibraryTags {
    private DataLibraryTags() {}

    static List<String> normalize(List<String> tags) {
        if (tags == null || tags.isEmpty()) return List.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String value = tag == null ? "" : tag.trim();
            if (value.isEmpty()) throw new IllegalArgumentException("Data Library tag must not be blank");
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }
}
