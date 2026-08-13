package com.mine.geometry_node.core.engine.system.model.importer;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ModelCancellationSource {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public ModelCancellationToken token() { return cancelled::get; }
    public boolean cancel() { return cancelled.compareAndSet(false, true); }
    public boolean isCancelled() { return cancelled.get(); }
}
