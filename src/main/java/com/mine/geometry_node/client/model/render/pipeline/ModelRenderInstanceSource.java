package com.mine.geometry_node.client.model.render.pipeline;

import com.mine.geometry_node.client.model.render.geometry.ModelRenderInstance;
import com.mine.geometry_node.client.model.render.lod.model.ModelLodMetrics;

import java.util.List;

/** Render-thread access wired by the client lifecycle; main-pass instances remain explicit arguments. */
public interface ModelRenderInstanceSource {
    ModelRenderInstanceSource EMPTY = new ModelRenderInstanceSource() {
        public boolean hasVisibleInstances() { return false; }
        public ModelLodMetrics.LoadedCounts loadedCounts() { return new ModelLodMetrics.LoadedCounts(0, 0); }
        public List<ModelRenderInstance> shadowInstances() { return List.of(); }
        public void invalidateRenderResources(String reason) {}
    };

    boolean hasVisibleInstances();
    /** Includes hidden and GPU-deferred instances, without preparing GPU assets. */
    ModelLodMetrics.LoadedCounts loadedCounts();
    /** Fetch at the shadow callback, after lifecycle invalidation; never main-camera culled or cached. */
    List<ModelRenderInstance> shadowInstances();
    void invalidateRenderResources(String reason);
}
