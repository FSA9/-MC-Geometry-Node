package com.mine.geometry_node.client.model.gpu;

public interface RenderThreadDispatcher {
    boolean isRenderThread();

    void execute(Runnable task);

    default void assertRenderThread() {
        if (!isRenderThread()) throw new IllegalStateException("GPU operation must run on the render thread");
    }
}
