package com.mine.geometry_node.client.model.render.backend.host.light.capture;

/** Render-thread frame quota for incrementally capturing immutable world-light snapshots. */
public final class HostWorldLightCaptureBudget {
    private final int cellsPerFrame;
    private long frame = -1;
    private int remaining;

    public HostWorldLightCaptureBudget(int cellsPerFrame) {
        if (cellsPerFrame < 1) throw new IllegalArgumentException("cellsPerFrame must be positive");
        this.cellsPerFrame = cellsPerFrame;
    }

    public void beginFrame(long frame) {
        if (frame < this.frame) throw new IllegalArgumentException("frame must be monotonic");
        if (frame != this.frame) {
            this.frame = frame;
            remaining = cellsPerFrame;
        }
    }

    /** Returns the admitted prefix; callers continue the same capture in a later frame. */
    public int claim(int requestedCells) {
        if (requestedCells < 0) throw new IllegalArgumentException("requestedCells must not be negative");
        int claimed = Math.min(requestedCells, remaining);
        remaining -= claimed;
        return claimed;
    }

    public int cellsPerFrame() { return cellsPerFrame; }
    public int remaining() { return remaining; }
}
