package com.mine.geometry_node.core.engine.graph.scoped;

import java.util.Optional;

/** Isolates public graph state from private subsystem data. */
public enum ScopedStateNamespace {
    PUBLIC("public", "geometry_node.scoped_state.namespace.public"),
    SHOP("shop", "geometry_node.scoped_state.namespace.shop");

    private final String serializedName;
    private final String translationKey;

    ScopedStateNamespace(String serializedName, String translationKey) {
        this.serializedName = serializedName;
        this.translationKey = translationKey;
    }

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return translationKey;
    }

    /** Parses a persisted namespace without mapping unknown values into another namespace. */
    public static Optional<ScopedStateNamespace> fromSerializedName(String value) {
        for (ScopedStateNamespace namespace : values()) {
            if (namespace.serializedName.equalsIgnoreCase(value)) return Optional.of(namespace);
        }
        return Optional.empty();
    }
}
