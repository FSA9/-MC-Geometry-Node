package com.mine.geometry_node.client.model.render.compat;

import java.util.Set;

public record ModelCompatibilityProjection(boolean selectable, ModelCompatibilityProfile profile,
                                           int projectedUvSet,
                                           boolean baseColor, boolean alphaMode, boolean doubleSided,
                                           boolean nodeAnimation, boolean gpuSkinning,
                                           Set<ModelCompatibilityLoss> losses) {
    public ModelCompatibilityProjection {
        if (profile == null) throw new IllegalArgumentException("profile must not be null");
        if (projectedUvSet < -1) throw new IllegalArgumentException("projectedUvSet must be -1 or greater");
        losses = losses == null ? Set.of() : Set.copyOf(losses);
        if (selectable && losses.contains(ModelCompatibilityLoss.GPU_SKINNING_UNREPRESENTABLE)) {
            throw new IllegalArgumentException("a backend cannot select a draw whose GPU skinning is unrepresentable");
        }
    }
}
