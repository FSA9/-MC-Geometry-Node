package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.gpu.ModelGpuBuffer;

import java.util.Objects;

/** Artifact-owned GPU geometry and the budget reservation backing it. */
public final class HostStaticGeometryVariant implements AutoCloseable {
    private final ModelGpuBuffer vertexBuffer;
    private final ModelGpuBuffer indexBuffer;
    private final int vertexCount;
    private final int indexCount;
    private final HostStaticVariantBudget.Reservation reservation;
    private boolean closed;

    public HostStaticGeometryVariant(ModelGpuBuffer vertexBuffer, ModelGpuBuffer indexBuffer,
                                     int vertexCount, int indexCount,
                                     HostStaticVariantBudget.Reservation reservation) {
        this.vertexBuffer = Objects.requireNonNull(vertexBuffer, "vertexBuffer");
        if (vertexCount < 0 || indexCount < 0) throw new IllegalArgumentException("negative static geometry count");
        this.indexBuffer = indexBuffer;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.reservation = Objects.requireNonNull(reservation, "reservation");
        long bufferBytes = Math.addExact((long) vertexBuffer.byteSize(),
                indexBuffer == null ? 0L : indexBuffer.byteSize());
        if (bufferBytes != reservation.bytes()) {
            throw new IllegalArgumentException("static geometry reservation does not match GPU buffers");
        }
    }

    public ModelGpuBuffer vertexBuffer() { return vertexBuffer; }
    public ModelGpuBuffer indexBuffer() { return indexBuffer; }
    public int vertexCount() { return vertexCount; }
    public int indexCount() { return indexCount; }
    public long byteSize() { return reservation.bytes(); }
    public boolean isClosed() { return closed; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try {
            vertexBuffer.close();
        } finally {
            try {
                if (indexBuffer != null) indexBuffer.close();
            } finally {
                reservation.close();
            }
        }
    }
}
