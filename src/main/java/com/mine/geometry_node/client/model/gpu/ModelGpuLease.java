package com.mine.geometry_node.client.model.gpu;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ModelGpuLease implements AutoCloseable {
    private final ModelGpuRepository owner;
    private final ModelGpuRepository.Entry entry;
    private final ModelGpuResource resource;
    private final AtomicBoolean closed = new AtomicBoolean();

    ModelGpuLease(ModelGpuRepository owner, ModelGpuRepository.Entry entry, ModelGpuResource resource) {
        this.owner = owner;
        this.entry = entry;
        this.resource = resource;
    }

    public ModelGpuResource resource() {
        if (closed.get()) throw new IllegalStateException("GPU model lease is closed");
        return resource;
    }

    public boolean isClosed() { return closed.get(); }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) owner.release(entry);
    }
}
