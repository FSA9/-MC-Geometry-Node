package com.mine.geometry_node.core.engine.system.model.runtime;

/**
 * Lifetime of a logical model instance.
 *
 * <p>P3-L owns the client-local temporary lifetime. {@link #PERSISTENT} is part of
 * the shared contract so the later SavedData/network layer can add persistence
 * without changing the instance API; the local service deliberately rejects
 * it until that layer exists.</p>
 */
public enum ModelInstanceLifetime {
    /** A temporary debug/command instance that is cleared on world teardown. */
    TEMPORARY(false),
    /** A world-owned instance; implemented by the later P3-N phase. */
    PERSISTENT(true);

    private final boolean persistent;

    ModelInstanceLifetime(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean persistent() {
        return persistent;
    }
}
