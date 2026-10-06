package com.mine.geometry_node.client.model.render.resource;

/** A late fence for an older read must never make a buffer writable during a newer GPU use. */
public final class ModelGpuUseFence {
    private long lastUse;
    private boolean inFlight, retired;
    public boolean writable() { return !inFlight && !retired; }
    public long submitted() {
        if (retired) throw new IllegalStateException("retired GPU slot");
        inFlight = true;
        return ++lastUse;
    }
    public void completed(long ticket) { if (!retired && ticket == lastUse) inFlight = false; }
    public void retire() { retired = true; }
}
