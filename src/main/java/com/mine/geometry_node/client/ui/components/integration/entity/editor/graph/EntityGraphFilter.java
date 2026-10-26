package com.mine.geometry_node.client.ui.components.integration.entity.editor.graph;

import com.mine.geometry_node.core.metadata.TagPolicy;
import com.mine.geometry_node.core.metadata.TagSet;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Immutable submitted criteria; editing controls never mutates the current result set. */
record EntityGraphFilter(String name, String type, Set<String> included, Set<String> excluded) {
    static final EntityGraphFilter EMPTY = new EntityGraphFilter("", "", Set.of(), Set.of());

    EntityGraphFilter {
        name = Objects.requireNonNullElse(name, "").trim().toLowerCase(Locale.ROOT);
        type = Objects.requireNonNullElse(type, "");
        included = Collections.unmodifiableSet(new LinkedHashSet<>(TagSet.normalize(included, TagPolicy.EXACT)));
        excluded = Collections.unmodifiableSet(new LinkedHashSet<>(TagSet.normalize(excluded, TagPolicy.EXACT)));
    }

    boolean matches(String displayName, EntityGraphBindings.Presentation value) {
        return displayName.toLowerCase(Locale.ROOT).contains(name)
                && (type.isEmpty() || type.equals(value.type()))
                && value.tags().containsAll(included)
                && Collections.disjoint(value.tags(), excluded);
    }
}
