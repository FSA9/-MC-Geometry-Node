package com.mine.geometry_node.client.ui.workspace.state;

import com.google.gson.JsonElement;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** A namespaced editor codec. Increment version when the payload format changes. */
public record UiStateKey<T>(String id, int version, UiStateLifetime lifetime, Class<T> type,
                            Supplier<T> defaults, UnaryOperator<T> sanitize, Migration migration) {
    /** Converts a supported older payload directly to this key's current version.
     * Return null for unsupported versions. The input is detached from stored state.
     */
    @FunctionalInterface
    public interface Migration {
        JsonElement migrate(int sourceVersion, JsonElement payload);
    }

    public UiStateKey(String id, int version, UiStateLifetime lifetime, Class<T> type,
                      Supplier<T> defaults, UnaryOperator<T> sanitize) {
        this(id, version, lifetime, type, defaults, sanitize, (sourceVersion, payload) -> null);
    }

    public UiStateKey {
        if (id == null || id.length() > 128 || !id.matches("[a-zA-Z0-9_-]+[.:/][a-zA-Z0-9_./:-]+"))
            throw new IllegalArgumentException("State key must be editor namespaced");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(lifetime);
        Objects.requireNonNull(type);
        Objects.requireNonNull(defaults);
        Objects.requireNonNull(sanitize);
        Objects.requireNonNull(migration);
    }
}
