package com.mine.geometry_node.core.engine.system.model.io;
import java.util.concurrent.atomic.AtomicBoolean;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
public final class ModelCancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    public void cancel() { cancelled.set(true); }
    public void check() throws ModelProblem {
        if (cancelled.get() || Thread.currentThread().isInterrupted())
            throw new ModelProblem(ModelProblem.Code.CANCELLED, "source", "request cancelled");
    }
}
