package com.mine.geometry_node.core.engine.graph.expression;

import java.util.Locale;
import java.util.UUID;

/**
 * Typed source for a named expression variable. Supported sources are explicit records rather
 * than runtime references encoded into strings.
 */
public sealed interface ExpressionBinding permits ExpressionBinding.Constant, ExpressionBinding.EntityProperty {
    double fallbackValue();

    record Constant(double value) implements ExpressionBinding {
        @Override
        public double fallbackValue() {
            return Double.isFinite(value) ? value : 0.0;
        }
    }

    record EntityProperty(String dimensionId, UUID entityUuid, int runtimeEntityId,
                          Property property, double fallbackValue) implements ExpressionBinding {
        public EntityProperty {
            dimensionId = dimensionId == null ? "" : dimensionId;
            if (entityUuid == null) {
                throw new IllegalArgumentException("entityUuid cannot be null");
            }
            if (property == null) {
                throw new IllegalArgumentException("property cannot be null");
            }
            fallbackValue = Double.isFinite(fallbackValue) ? fallbackValue : 0.0;
        }
    }

    enum Property {
        VELOCITY,
        VELOCITY_X,
        VELOCITY_Y,
        VELOCITY_Z,
        POS_X,
        POS_Y,
        POS_Z,
        ROTATION_X,
        ROTATION_Y,
        ROTATION_Z,
        PITCH,
        YAW,
        YAW_HEAD;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Property fromId(String id) {
            if (id == null || id.isBlank()) return null;
            try {
                return valueOf(id.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
