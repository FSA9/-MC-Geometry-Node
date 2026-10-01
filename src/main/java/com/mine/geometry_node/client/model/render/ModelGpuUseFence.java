package com.mine.geometry_node.client.model.render;

/** A late fence for an older read must never make a buffer writable during a newer GPU use. */
final class ModelGpuUseFence {
    private long lastUse;
    private boolean inFlight, retired;
    boolean writable() { return !inFlight && !retired; }
    long submitted() {
        if (retired) throw new IllegalStateException("retired GPU slot");
        inFlight = true;
        return ++lastUse;
    }
    void completed(long ticket) { if (!retired && ticket == lastUse) inFlight = false; }
    void retire() { retired = true; }
}
