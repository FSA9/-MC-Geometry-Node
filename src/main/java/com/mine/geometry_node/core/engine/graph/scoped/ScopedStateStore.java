package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeContext;
import org.jetbrains.annotations.Nullable;

/** Runtime-neutral access contract for persistent scoped state. */
public interface ScopedStateStore {
    ScopedStateStore NOOP = new ScopedStateStore() {
        @Override public void set(GraphRuntimeContext context, ScopedStateNamespace namespace,
                                  ScopedStateTarget target, String name, Object value) { }
        @Override public @Nullable Object get(GraphRuntimeContext context,
                                              ScopedStateNamespace namespace,
                                              ScopedStateTarget target, String name) { return null; }
        @Override public boolean has(GraphRuntimeContext context,
                                     ScopedStateNamespace namespace,
                                     ScopedStateTarget target, String name) { return false; }
        @Override public boolean clear(GraphRuntimeContext context,
                                       ScopedStateNamespace namespace,
                                       ScopedStateTarget target, String name) { return false; }
    };

    default void set(GraphRuntimeContext context, ScopedStateTarget target,
                     String name, Object value) {
        set(context, ScopedStateNamespace.PUBLIC, target, name, value);
    }

    void set(GraphRuntimeContext context, ScopedStateNamespace namespace,
             ScopedStateTarget target, String name, Object value);

    default @Nullable Object get(GraphRuntimeContext context, ScopedStateTarget target,
                                 String name) {
        return get(context, ScopedStateNamespace.PUBLIC, target, name);
    }

    @Nullable Object get(GraphRuntimeContext context, ScopedStateNamespace namespace,
                         ScopedStateTarget target, String name);

    default boolean has(GraphRuntimeContext context, ScopedStateTarget target, String name) {
        return has(context, ScopedStateNamespace.PUBLIC, target, name);
    }

    boolean has(GraphRuntimeContext context, ScopedStateNamespace namespace,
                ScopedStateTarget target, String name);

    default boolean clear(GraphRuntimeContext context, ScopedStateTarget target, String name) {
        return clear(context, ScopedStateNamespace.PUBLIC, target, name);
    }

    boolean clear(GraphRuntimeContext context, ScopedStateNamespace namespace,
                  ScopedStateTarget target, String name);
}
