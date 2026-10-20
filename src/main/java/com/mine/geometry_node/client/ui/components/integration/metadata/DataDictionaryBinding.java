package com.mine.geometry_node.client.ui.components.integration.metadata;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Asynchronous custom-data capability. Values are detached snapshots; never vanilla NBT. */
public interface DataDictionaryBinding extends AutoCloseable {
    /** available=false/loading=false means unsupported; loading=true means not yet loaded. */
    record State(Map<String, Object> values, boolean loading, boolean available,
                 boolean editable, boolean saving, String message) {
        public State {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
            message = message == null ? "" : message;
        }
    }

    State snapshot();
    /** Ensure a readable snapshot; owners may reuse a previously loaded value. */
    void refresh();

    /** Explicit user reload after any discard confirmation; refresh the authoritative source. */
    default void reload() { refresh(); }

    /** Atomic compare-and-replace. Null/empty completion means success; errors keep the draft. */
    void apply(Map<String, Object> expected, Map<String, Object> replacement, Consumer<String> completion);
    void addChangeListener(Runnable listener);
    void removeChangeListener(Runnable listener);

    /** Only the owning editor closes a shared session; child views remove their own listeners. */
    @Override default void close() {}
}
