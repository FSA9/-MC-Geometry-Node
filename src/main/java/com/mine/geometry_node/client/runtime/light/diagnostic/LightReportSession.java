package com.mine.geometry_node.client.runtime.light.diagnostic;

import java.util.Objects;

/** Render-thread lifecycle only; the supplied cleanup executes inline, never on the IO worker. */
final class LightReportSession {
    private enum Phase { IDLE, QUEUED, CAPTURING, DRAINING, SAVING }

    private final Runnable releaseQueries;
    private Phase phase = Phase.IDLE;
    private Object world;
    private boolean ownsQueries;
    private long generation;

    LightReportSession(Runnable releaseQueries) {
        this.releaseQueries = Objects.requireNonNull(releaseQueries);
    }

    boolean busy() { return phase != Phase.IDLE; }
    boolean capturing() { return phase == Phase.CAPTURING; }
    boolean worldChanged(Object current) { return world != null && world != current; }

    void queue(Object world) {
        require(Phase.IDLE);
        this.world = Objects.requireNonNull(world);
        generation++;
        phase = Phase.QUEUED;
    }

    /** Claim before initializing either query collector so partial initialization is cleaned up. */
    void start() {
        require(Phase.QUEUED);
        ownsQueries = true;
        phase = Phase.CAPTURING;
    }

    void drain() {
        require(Phase.CAPTURING);
        phase = Phase.DRAINING;
    }

    long save() {
        require(Phase.DRAINING);
        world = null;
        release();
        phase = Phase.SAVING;
        return generation;
    }

    /** Only the matching queued client-thread completion may publish feedback or clear busy. */
    boolean complete(long expectedGeneration) {
        if (generation != expectedGeneration || phase != Phase.SAVING) return false;
        phase = Phase.IDLE;
        return true;
    }

    void cancel() {
        generation++;
        world = null;
        phase = Phase.IDLE;
        release();
    }

    private void release() {
        if (!ownsQueries) return;
        ownsQueries = false;
        releaseQueries.run();
    }

    private void require(Phase expected) {
        if (phase != expected) throw new IllegalStateException("Light report phase: " + phase + ", expected " + expected);
    }
}
