package com.mine.geometry_node.client.model.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exactly-once ownership handle for a backend-specific prepared artifact. */
public final class BackendArtifactLease<T> implements AutoCloseable {
    private final T artifact;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    public BackendArtifactLease(T artifact, Runnable release) {
        this.artifact = Objects.requireNonNull(artifact, "artifact");
        this.release = Objects.requireNonNull(release, "release");
    }

    public T artifact() {
        if (closed.get()) throw new IllegalStateException("backend artifact lease is closed");
        return artifact;
    }

    public boolean isClosed() { return closed.get(); }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) release.run();
    }
}
