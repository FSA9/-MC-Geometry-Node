package com.mine.geometry_node.core.engine.system.model.collision;

/** World-block units, independent of rendering quality. */
public record ModelCollisionSettings(double mergeScale, double fitError) {
    public static final ModelCollisionSettings DEFAULT = new ModelCollisionSettings(0.5, 0.01);

    public ModelCollisionSettings {
        if (!Double.isFinite(mergeScale) || mergeScale < 0 || mergeScale > 16)
            throw new IllegalArgumentException("collision merge scale must be in [0,16] blocks");
        if (!Double.isFinite(fitError) || fitError < 0.0001 || fitError > 1)
            throw new IllegalArgumentException("collision fitting error must be in [0.0001,1] blocks");
    }
}
