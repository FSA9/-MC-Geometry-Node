package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.domain.ModelMatrix4;

public record ModelGpuDrawState(
        ModelMatrix4 instanceTransform,
        int packedLight,
        float red,
        float green,
        float blue,
        float alpha
) {
    public ModelGpuDrawState {
        if (instanceTransform == null) throw new IllegalArgumentException("instanceTransform must not be null");
        red = unit(red, "red");
        green = unit(green, "green");
        blue = unit(blue, "blue");
        alpha = unit(alpha, "alpha");
    }

    private static float unit(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
            throw new IllegalArgumentException(name + " must be finite and within [0, 1]");
        }
        return value;
    }
}
