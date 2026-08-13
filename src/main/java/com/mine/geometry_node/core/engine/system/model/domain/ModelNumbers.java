package com.mine.geometry_node.core.engine.system.model.domain;

final class ModelNumbers {
    private ModelNumbers() {
    }

    static float finite(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }

    static void requireFinite(float[] values, String name) {
        for (float value : values) finite(value, name);
    }
}
