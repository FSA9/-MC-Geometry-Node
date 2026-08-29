package com.mine.geometry_node.core.engine.graph.scoped;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** Common state scopes. INSTANCE is reserved for a behavior runtime frame. */
public enum ScopedStateScope {
    /** Behavior-tree-only, in-memory state owned by one running behavior instance. */
    INSTANCE(false),
    OWNER(true),
    SHARED(true),
    GROUP(true),
    WORLD(true);

    public static final Set<ScopedStateScope> ALL = Set.of(values());
    public static final Set<ScopedStateScope> PERSISTENT = Set.of(OWNER, SHARED, GROUP, WORLD);

    private final boolean persistent;

    ScopedStateScope(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static ScopedStateScope parse(@Nullable Object value) {
        if (value instanceof ScopedStateScope scope) return scope;
        if (!(value instanceof String text) || text.isBlank()) return null;
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static ScopedStateScope resolve(@Nullable Object value, ScopedStateScope defaultScope,
                                           Set<ScopedStateScope> allowed) {
        if (value == null || value instanceof String text && text.isBlank()) {
            if (allowed.contains(defaultScope)) return defaultScope;
            throw new IllegalArgumentException("Default scoped-state scope is not allowed: " + defaultScope);
        }
        ScopedStateScope parsed = parse(value);
        if (parsed == null || !allowed.contains(parsed)) {
            throw new IllegalArgumentException("Unsupported scoped-state scope: " + value);
        }
        return parsed;
    }

    public static String[] optionIds(Set<ScopedStateScope> allowed) {
        return Arrays.stream(values()).filter(allowed::contains)
                .map(ScopedStateScope::id).toArray(String[]::new);
    }
}
