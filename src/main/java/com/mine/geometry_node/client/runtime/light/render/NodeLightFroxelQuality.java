package com.mine.geometry_node.client.runtime.light.render;

import com.mine.geometry_node.core.node.value.light.LightConfig;
import java.util.Map;

/** Maps persisted volume quality to the spatial grid without changing graph or packet schemas. */
final class NodeLightFroxelQuality {
    private NodeLightFroxelQuality() {}

    static int depthSlices(LightConfig.Quality quality) {
        return Math.clamp(quality.volumeSamples() * 2, 32, 128);
    }

    static int width(int viewportWidth, LightConfig.Quality quality) {
        return Math.max(1, (int) Math.ceil(viewportWidth * quality.volumeResolution() / 4));
    }

    static int height(int viewportHeight, LightConfig.Quality quality) {
        return Math.max(1, (int) Math.ceil(viewportHeight * quality.volumeResolution() / 4));
    }

    static int columns(int slices) { return Math.min(8, Math.max(1, slices)); }
    static int rows(int slices) { return (slices + columns(slices) - 1) / columns(slices); }

    static Map<String, Object> report(int viewportWidth, int viewportHeight, LightConfig.Quality quality) {
        int w = width(viewportWidth, quality), h = height(viewportHeight, quality), slices = depthSlices(quality);
        return Map.of("requestedDepthQuality", quality.volumeSamples(),
                "requestedResolution", quality.volumeResolution(), "effectiveResolutionScale", quality.volumeResolution() / 4,
                "gridWidth", w, "gridHeight", h, "depthSlices", slices,
                "froxelCount", (long) w * h * slices,
                "atlasWidth", w * columns(slices), "atlasHeight", h * rows(slices),
                "scope", "Requested grid before runtime allocation limits. XY=ceil(viewport*resolution/4), Z=clamp(depthQuality*2,32,128). Old saved quality fields are retained; these are not per-pixel march steps.");
    }
}
