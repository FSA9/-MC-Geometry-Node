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

    /** Open one standalone edit-owned binding. Called only when opening an editor, never by passive
     * previews or embedded children. Resource-backed bindings should return a distinct instance whose
     * close releases its session; the default shared binding must tolerate close (normally a no-op). */
    default DataDictionaryBinding openSession() { return this; }

    State snapshot();
    /** Ensure a readable snapshot; owners may reuse a previously loaded value. */
    void refresh();

    /** Explicit user reload of the authoritative source; unfinished forms stay in the window session. */
    default void reload() { refresh(); }

    /** Atomic compare-and-replace of completed operations only. Null/empty completion means success.
     * Completion may be synchronous or asynchronous and must run exactly once, including after the
     * initiating View detaches/closes. Rejections must describe the error; unknown outcomes must not
     * be reported as success or blindly retried. The logical session retains its binding until completion. */
    void apply(Map<String, Object> expected, Map<String, Object> replacement, Consumer<String> completion);
    void addChangeListener(Runnable listener);
    void removeChangeListener(Runnable listener);

    /** Only the logical owner closes a binding, after in-flight completion; shared embedded children never close it. */
    @Override default void close() {}
}
