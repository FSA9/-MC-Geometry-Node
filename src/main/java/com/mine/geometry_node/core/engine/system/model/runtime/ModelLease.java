package com.mine.geometry_node.core.engine.system.model.runtime;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** A per-consumer reference to one shared asset load. Closing one lease never cancels other consumers. */
public final class ModelLease<A> implements AutoCloseable {
    interface Releaser { void release(); }
    private final long generation;
    private final CompletionStage<A> completion;
    private final Releaser releaser;
    private final AtomicBoolean closed = new AtomicBoolean();
    ModelLease(long generation, CompletionStage<A> completion, Releaser releaser) {
        this.generation = generation;
        this.completion = Objects.requireNonNull(completion);
        this.releaser = Objects.requireNonNull(releaser);
    }
    public long generation() { return generation; }
    public CompletionStage<A> completion() { return completion; }
    public boolean isClosed() { return closed.get(); }
    @Override public void close() {
        if (closed.compareAndSet(false, true)) releaser.release();
    }
}
