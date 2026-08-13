package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.client.model.render.ModelWorldRenderer;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class ModelResourceReloadListener implements PreparableReloadListener {
    @Override
    public CompletableFuture<Void> reload(SharedState state, Executor preparationExecutor,
                                          PreparationBarrier barrier, Executor gameExecutor) {
        return barrier.wait(null).thenRunAsync(() -> {
            ModelWorldRenderer.clear();
            ClientModelRuntime.INSTANCE.resetGpuBackend();
        }, gameExecutor);
    }

    @Override public String getName() { return "GeometryNode static model GPU reset"; }
}
