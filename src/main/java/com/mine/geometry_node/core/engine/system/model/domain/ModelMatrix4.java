package com.mine.geometry_node.core.engine.system.model.domain;

import java.util.Arrays;

public final class ModelMatrix4 {
    private final float[] elements;

    public ModelMatrix4(float[] elements) {
        if (elements == null || elements.length != 16) {
            throw new IllegalArgumentException("matrix must contain 16 elements");
        }
        ModelNumbers.requireFinite(elements, "matrix element");
        this.elements = Arrays.copyOf(elements, elements.length);
    }

    public float element(int index) {
        return elements[index];
    }

    public float[] elements() {
        return Arrays.copyOf(elements, elements.length);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModelMatrix4 matrix && Arrays.equals(elements, matrix.elements);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(elements);
    }
}
