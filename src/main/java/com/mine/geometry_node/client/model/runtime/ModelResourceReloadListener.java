package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.client.model.render.backend.standalone.StandaloneModelRenderer;
import com.mine.geometry_node.client.model.render.integration.ModelIntegrationController;
import com.mine.geometry_node.client.model.render.backend.host.entity.HostNativeRenderer;
import com.mine.geometry_node.client.model.render.backend.host.light.integration.HostLightingEnvironment;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class ModelResourceReloadListener implements PreparableReloadListener {
    private static long reloadGeneration;

    @Override
    public CompletableFuture<Void> reload(SharedState state, Executor preparationExecutor,
                                          PreparationBarrier barrier, Executor gameExecutor) {
        return barrier.wait(null).thenRunAsync(ModelResourceReloadListener::reloadBindings, gameExecutor);
    }

    /** Rebuilds only model render bindings; authoritative instances and uploaded geometry remain resident. */
    public static long reloadBindings() {
        StandaloneModelRenderer.clear();
        HostNativeRenderer.clear();
        ModelIntegrationController.reset();
        long generation = ++reloadGeneration;
        HostLightingEnvironment.invalidate(generation);
        return generation;
    }

    public static long reloadGeneration() { return reloadGeneration; }

    @Override public String getName() { return "GeometryNode model material and shader bindings reload"; }
}
