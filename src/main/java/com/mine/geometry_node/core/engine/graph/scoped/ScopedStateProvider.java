package com.mine.geometry_node.core.engine.graph.scoped;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Storage provider for one resolved scoped-state identity. */
public interface ScopedStateProvider {
    ScopedStateScope scope();
    default String identity() { return scope().name(); }
    default boolean available() { return true; }
    /** Returns a detached value that callers may retain without observing later store mutations. */
    @Nullable ScopedStateEntry get(String name);
    ScopedStateEntry put(String name, Object value);
    boolean remove(String name);
    boolean hasRecord(String name);
    Map<String, ScopedStateEntry> entries();
    long revision();
    int size();
}
