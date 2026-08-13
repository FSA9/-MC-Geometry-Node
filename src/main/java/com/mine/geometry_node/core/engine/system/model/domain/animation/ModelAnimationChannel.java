package com.mine.geometry_node.core.engine.system.model.domain.animation;

public record ModelAnimationChannel(int nodeIndex, ModelAnimationPath path, int samplerIndex) {
    public ModelAnimationChannel {
        if (nodeIndex < 0 || samplerIndex < 0) throw new IllegalArgumentException("animation indices must not be negative");
        if (path == null) throw new IllegalArgumentException("animation path must not be null");
    }
}
