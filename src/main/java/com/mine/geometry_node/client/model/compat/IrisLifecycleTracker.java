package com.mine.geometry_node.client.model.compat;

/** Pure state observer: object identity also catches a recreated pipeline with an unchanged layout. */
final class IrisLifecycleTracker {
    private boolean initialized, active;
    private Object manager, pipeline;
    private int reloadVersion;
    private long generation;

    boolean observe(boolean active, Object manager, Object pipeline, int reloadVersion) {
        boolean changed = initialized && (this.active != active || this.manager != manager
                || this.pipeline != pipeline || this.reloadVersion != reloadVersion);
        this.initialized = true; this.active = active; this.manager = manager;
        this.pipeline = pipeline; this.reloadVersion = reloadVersion;
        if (changed) generation++;
        return changed;
    }
    long generation() { return generation; }
}
