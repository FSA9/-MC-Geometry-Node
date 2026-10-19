package com.mine.geometry_node.core.metadata;

import java.util.Locale;

/** Explicit compatibility policies; custom object tags use EXACT. */
public enum TagPolicy {
    EXACT,
    GRAPH_LEGACY,
    /** Existing document command convention, retained for programmatic edits. */
    GRAPH_EDIT_LEGACY,
    /** Existing library data may contain control characters; do not discard it on read. */
    DATA_LIBRARY_LEGACY;

    public String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (this == GRAPH_LEGACY) {
            value = value.replaceAll("\\p{Cntrl}", "").trim();
            while (value.startsWith("#")) value = value.substring(1).trim();
            value = value.toLowerCase(Locale.ROOT);
        }
        if (this == EXACT && (value.length() > TagSet.MAX_TAG_LENGTH || value.codePoints().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("Invalid tag");
        if (value.isEmpty() && (this == EXACT || this == DATA_LIBRARY_LEGACY)) throw new IllegalArgumentException("Tag must not be blank");
        return value;
    }
}
