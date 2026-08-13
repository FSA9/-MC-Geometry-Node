package com.mine.geometry_node.core.engine.system.model.domain;

public sealed interface ModelTransform permits ModelTransform.Trs, ModelTransform.Matrix {
    record Trs(ModelVector3 translation, ModelQuaternion rotation, ModelVector3 scale) implements ModelTransform {
        public static final Trs IDENTITY = new Trs(ModelVector3.ZERO, ModelQuaternion.IDENTITY, ModelVector3.ONE);

        public Trs {
            if (translation == null || rotation == null || scale == null) {
                throw new IllegalArgumentException("TRS components must not be null");
            }
        }
    }

    record Matrix(ModelMatrix4 value) implements ModelTransform {
        public Matrix {
            if (value == null) throw new IllegalArgumentException("matrix must not be null");
        }
    }
}
