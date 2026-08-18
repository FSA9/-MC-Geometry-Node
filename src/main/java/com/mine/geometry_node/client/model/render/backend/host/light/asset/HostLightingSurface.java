package com.mine.geometry_node.client.model.render.backend.host.light.asset;

import com.mine.geometry_node.client.model.render.backend.host.geometry.HostCanonicalPrimitive;

import java.util.Arrays;
import java.util.Objects;

/** One transformed canonical primitive occurrence used by receiver and occluder preparation. */
public final class HostLightingSurface {
    private final Identity identity;
    private final HostLightingMaterial material;
    private final float[] positions;
    private final float[] shadingNormals;
    private final float[] geometricNormals;
    private final int[] indices;

    HostLightingSurface(Identity identity, HostLightingMaterial material, float[] positions,
                        float[] shadingNormals, float[] geometricNormals, int[] indices) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.material = Objects.requireNonNull(material, "material");
        this.positions = positions;
        this.shadingNormals = shadingNormals;
        this.geometricNormals = geometricNormals;
        this.indices = indices;
    }

    public Identity identity() { return identity; }
    public HostLightingMaterial material() { return material; }
    public int vertexCount() { return positions.length / 3; }
    public int triangleCount() { return indices.length / 3; }
    public float position(int vertex, int component) { return positions[vertex * 3 + component]; }
    public float shadingNormal(int vertex, int component) { return shadingNormals[vertex * 3 + component]; }
    public float geometricNormal(int triangle, int component) { return geometricNormals[triangle * 3 + component]; }
    public int vertexIndex(int triangle, int corner) { return indices[triangle * 3 + corner]; }
    public boolean triangleDegenerate(int triangle) {
        int offset = triangle * 3;
        return geometricNormals[offset] == 0F && geometricNormals[offset + 1] == 0F
                && geometricNormals[offset + 2] == 0F;
    }

    public float[] positions() { return Arrays.copyOf(positions, positions.length); }
    public float[] shadingNormals() { return Arrays.copyOf(shadingNormals, shadingNormals.length); }
    public float[] geometricNormals() { return Arrays.copyOf(geometricNormals, geometricNormals.length); }
    public int[] indices() { return Arrays.copyOf(indices, indices.length); }

    public long retainedBytes() {
        return (long) (positions.length + shadingNormals.length + geometricNormals.length) * Float.BYTES
                + (long) indices.length * Integer.BYTES;
    }

    public record Identity(HostCanonicalPrimitive.Identity primitive, int occurrenceIndex,
                           int nodeIndex, int skinIndex) {
        public Identity {
            Objects.requireNonNull(primitive, "primitive");
            if (occurrenceIndex < 0 || nodeIndex < 0 || skinIndex < -1) {
                throw new IllegalArgumentException("invalid lighting surface identity");
            }
        }
    }
}
