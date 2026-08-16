package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.identity.*;

import java.util.List;

public final class TestModelGpuLeaseFactory {
    private TestModelGpuLeaseFactory() {}

    public static ModelGpuLease create(ModelAssetReference asset) {
        ModelGpuRepository repository = new ModelGpuRepository(new EmptyDevice(), new ImmediateRenderThread());
        return repository.acquire(new ModelGpuUploadPlan(asset, List.of(), List.of(), List.of())).join();
    }

    private static final class EmptyDevice implements ModelGpuDevice {
        @Override public ModelGpuBuffer createBuffer(String label, ModelGpuBufferKind kind, byte[] data) { throw new AssertionError(); }
        @Override public ModelGpuTexture createTexture(String label, ModelGpuImagePlan image) { throw new AssertionError(); }
    }

    private static final class ImmediateRenderThread implements RenderThreadDispatcher {
        private boolean rendering;
        @Override public boolean isRenderThread() { return rendering; }
        @Override public void execute(Runnable task) {
            boolean previous = rendering;
            rendering = true;
            try { task.run(); } finally { rendering = previous; }
        }
    }
}
