package com.mine.geometry_node.core.engine.graph.scoped;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import com.mine.geometry_node.core.metadata.MetadataStore;
import com.mine.geometry_node.core.metadata.DataAccess;
import com.mine.geometry_node.core.metadata.DataValuePolicy;
import com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy;
import com.mine.geometry_node.core.engine.metadata.ScopedMetadataTargets;

/** Storage provider for one resolved scoped-state identity. */
public interface ScopedStateProvider extends MetadataStore<Map<String, Object>> {
    ScopedStateScope scope();
    default String identity() { return scope().name(); }
    default boolean available() { return true; }
    /** Returns a detached value that callers may retain without observing later store mutations. */
    @Nullable ScopedStateEntry get(String name);
    void put(String name, Object value);
    boolean remove(String name);
    boolean hasRecord(String name);
    default Map<String, ScopedStateEntry> entries() { return entries(Integer.MAX_VALUE); }
    Map<String, ScopedStateEntry> entries(int limit);
    long revision();
    int size();
    default DataAccess data() { return ScopedMetadataTargets.data(this); }
    default DataValuePolicy valuePolicy() { return GraphMetadataValuePolicy.runtime(); }
}
