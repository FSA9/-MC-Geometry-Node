package com.mine.geometry_node.client.model.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientModelResourceLease implements AutoCloseable {
    private final ModelResourceCoordinator owner;
    private final ModelResourceCoordinator.Entry entry;
    private final CompletableFuture<LoadedModelResource> resourceView;
    private final AtomicBoolean closed = new AtomicBoolean();

    ClientModelResourceLease(ModelResourceCoordinator owner, ModelResourceCoordinator.Entry entry) {
        this.owner = owner;
        this.entry = entry;
        this.resourceView = entry.resource().thenApply(resource -> resource);
    }

    public CompletableFuture<LoadedModelResource> resource() { return resourceView; }
    public boolean isClosed() { return closed.get(); }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) owner.release(entry);
    }
}
