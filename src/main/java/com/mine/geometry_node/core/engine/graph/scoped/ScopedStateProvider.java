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
    default Map<String, ScopedStateEntry> entries() { return entries(Integer.MAX_VALUE); }
    Map<String, ScopedStateEntry> entries(int limit);
    long revision();
    int size();
}
