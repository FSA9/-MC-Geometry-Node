package com.mine.geometry_node.client.model.render.backend.host.light.contract;

import java.util.Objects;

/** Immutable light source selected for one resolved HOST draw. */
public sealed interface HostLightBinding
        permits HostLightBinding.Constant, HostLightBinding.Field, HostLightBinding.FullBright {
    int FULL_BRIGHT_PACKED = 0x00F000F0;
    Identity identity();

    int packedLight(int vertexOccurrence);

    static HostLightBinding constant(int packedLight) {
        return new Constant(packedLight);
    }

    static HostLightBinding field(HostLightFieldId id, HostVertexLightView samples) {
        return new Field(id, samples);
    }

    static HostLightBinding fullBright() {
        return FullBright.INSTANCE;
    }

    enum Mode { CONSTANT, FIELD, FULL_BRIGHT }

    record Identity(Mode mode, int constantPackedLight, HostLightFieldId fieldId) {
        public Identity {
            Objects.requireNonNull(mode, "mode");
            if (mode == Mode.FIELD) Objects.requireNonNull(fieldId, "fieldId");
            else if (fieldId != null) throw new IllegalArgumentException("only FIELD may carry a field id");
        }
    }

    record Constant(int packedLight) implements HostLightBinding {
        @Override public Identity identity() {
            return new Identity(Mode.CONSTANT, packedLight, null);
        }

        @Override public int packedLight(int vertexOccurrence) {
            requireVertex(vertexOccurrence);
            return packedLight;
        }
    }

    final class Field implements HostLightBinding {
        private final HostLightFieldId id;
        private final HostVertexLightView samples;

        private Field(HostLightFieldId id, HostVertexLightView samples) {
            this.id = Objects.requireNonNull(id, "id");
            this.samples = Objects.requireNonNull(samples, "samples");
        }

        @Override public Identity identity() {
            return new Identity(Mode.FIELD, 0, id);
        }

        @Override public int packedLight(int vertexOccurrence) {
            requireVertex(vertexOccurrence);
            return samples.packedLight(vertexOccurrence);
        }
    }

    enum FullBright implements HostLightBinding {
        INSTANCE;

        @Override public Identity identity() {
            return new Identity(Mode.FULL_BRIGHT, FULL_BRIGHT_PACKED, null);
        }

        @Override public int packedLight(int vertexOccurrence) {
            requireVertex(vertexOccurrence);
            return FULL_BRIGHT_PACKED;
        }
    }

    private static void requireVertex(int vertexOccurrence) {
        if (vertexOccurrence < 0) throw new IllegalArgumentException("vertexOccurrence must not be negative");
    }
}
