package com.mine.geometry_node.client.model.render.backend.host.entity;

/** Prevents HOST_NATIVE immediate geometry from overflowing or monopolizing the shared entity buffer. */
final class HostVertexBudget {
    static final long MAX_VERTICES_PER_FRAME = 1_000_000L;

    private long submitted;

    boolean reserve(long vertices) {
        if (vertices < 0 || vertices > MAX_VERTICES_PER_FRAME - submitted) return false;
        submitted += vertices;
        return true;
    }

    long submitted() {
        return submitted;
    }
}
