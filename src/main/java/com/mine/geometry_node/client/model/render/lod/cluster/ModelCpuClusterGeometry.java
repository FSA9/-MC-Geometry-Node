package com.mine.geometry_node.client.model.render.lod.cluster;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable render geometry owned by one CPU cluster node.
 *
 * <p>Leaf geometry contains a compact copy of its source triangles. Internal-node
 * geometry is a small proxy made from representative child triangles. Keeping
 * the geometry self-contained lets the renderer upload a node without walking
 * the source primitive.</p>
 */
public final class ModelCpuClusterGeometry {
    private final float[] positions;
    private final float[] normals;
    private final float[] tangents;
    private final float[] baseUv;
    private final float[] emissionUv;
    private final float[] uv0;
    private final float[] uv1;
    private final int[] colors;
    private final int[] indices;
    private final GltfAsset.Bounds bounds;
    private final boolean proxy;
    private final int sourceTriangles;

    public ModelCpuClusterGeometry(float[] positions, float[] normals, float[] tangents,
                                   float[] baseUv, float[] emissionUv, float[] uv0, float[] uv1,
                                   int[] colors, int[] indices, GltfAsset.Bounds bounds,
                                   boolean proxy, int sourceTriangles) {
        this(positions, normals, tangents, baseUv, emissionUv, uv0, uv1, colors, indices,
                bounds, proxy, sourceTriangles, true);
    }

    /** Builder-only ownership transfer; the supplied arrays must not be mutated afterwards. */
    static ModelCpuClusterGeometry owned(float[] positions, float[] normals, float[] tangents,
                                         float[] baseUv, float[] emissionUv, float[] uv0, float[] uv1,
                                         int[] colors, int[] indices, GltfAsset.Bounds bounds,
                                         boolean proxy, int sourceTriangles) {
        return new ModelCpuClusterGeometry(positions, normals, tangents, baseUv, emissionUv, uv0, uv1,
                colors, indices, bounds, proxy, sourceTriangles, false);
    }

    private ModelCpuClusterGeometry(float[] positions, float[] normals, float[] tangents,
                                    float[] baseUv, float[] emissionUv, float[] uv0, float[] uv1,
                                    int[] colors, int[] indices, GltfAsset.Bounds bounds,
                                    boolean proxy, int sourceTriangles, boolean copyArrays) {
        this.positions = copyArrays ? copy(Objects.requireNonNull(positions)) : Objects.requireNonNull(positions);
        this.normals = copyArrays ? copy(Objects.requireNonNull(normals)) : Objects.requireNonNull(normals);
        this.tangents = copyArrays ? copy(Objects.requireNonNull(tangents)) : Objects.requireNonNull(tangents);
        this.baseUv = copyArrays ? copy(Objects.requireNonNull(baseUv)) : Objects.requireNonNull(baseUv);
        this.emissionUv = copyArrays ? copy(Objects.requireNonNull(emissionUv)) : Objects.requireNonNull(emissionUv);
        this.uv0 = copyArrays ? copy(Objects.requireNonNull(uv0)) : Objects.requireNonNull(uv0);
        this.uv1 = copyArrays ? copy(Objects.requireNonNull(uv1)) : Objects.requireNonNull(uv1);
        this.colors = copyArrays ? Objects.requireNonNull(colors).clone() : Objects.requireNonNull(colors);
        this.indices = copyArrays ? Objects.requireNonNull(indices).clone() : Objects.requireNonNull(indices);
        this.bounds = Objects.requireNonNull(bounds);
        this.proxy = proxy;
        this.sourceTriangles = sourceTriangles;
        validate();
    }

    public float[] positions() { return positions.clone(); }
    public float[] normals() { return normals.clone(); }
    public float[] tangents() { return tangents.clone(); }
    public float[] baseUv() { return baseUv.clone(); }
    public float[] emissionUv() { return emissionUv.clone(); }
    public float[] uv0() { return uv0.clone(); }
    public float[] uv1() { return uv1.clone(); }
    public int[] colors() { return colors.clone(); }
    public int[] indices() { return indices.clone(); }

    // Package-private read-only views used by the hierarchy builder. The builder never mutates them.
    float[] rawPositions() { return positions; }
    float[] rawNormals() { return normals; }
    float[] rawTangents() { return tangents; }
    float[] rawBaseUv() { return baseUv; }
    float[] rawEmissionUv() { return emissionUv; }
    float[] rawUv0() { return uv0; }
    float[] rawUv1() { return uv1; }
    int[] rawColors() { return colors; }
    int[] rawIndices() { return indices; }

    public GltfAsset.Bounds bounds() { return bounds; }
    public boolean proxy() { return proxy; }
    public int sourceTriangles() { return sourceTriangles; }
    public int vertexCount() { return positions.length / 3; }
    public int indexCount() { return indices.length; }
    public int triangleCount() { return indices.length / 3; }

    /** Conservative Java-side memory estimate, including index and attribute arrays. */
    public long cpuBytes() {
        long bytes = (long) (positions.length + normals.length + tangents.length + baseUv.length
                + emissionUv.length + uv0.length + uv1.length) * Float.BYTES;
        bytes = Math.addExact(bytes, (long) (colors.length + indices.length) * Integer.BYTES);
        return Math.addExact(bytes, 64L);
    }

    private void validate() {
        if (positions.length == 0 || positions.length % 3 != 0)
            throw new IllegalArgumentException("cluster positions must contain xyz triples");
        if (indices.length == 0 || indices.length % 3 != 0)
            throw new IllegalArgumentException("cluster indices must contain triangles");
        int vertices = vertexCount();
        if (normals.length != 0 && normals.length != positions.length)
            throw new IllegalArgumentException("cluster normals must match positions");
        if (tangents.length != 0 && tangents.length != vertices * 4)
            throw new IllegalArgumentException("cluster tangents must contain four values per vertex");
        if (baseUv.length != 0 && baseUv.length != vertices * 2)
            throw new IllegalArgumentException("cluster base UV must contain two values per vertex");
        if (emissionUv.length != 0 && emissionUv.length != vertices * 2)
            throw new IllegalArgumentException("cluster emission UV must contain two values per vertex");
        if (uv0.length != 0 && uv0.length != vertices * 2)
            throw new IllegalArgumentException("cluster UV0 must contain two values per vertex");
        if (uv1.length != 0 && uv1.length != vertices * 2)
            throw new IllegalArgumentException("cluster UV1 must contain two values per vertex");
        if (colors.length != 0 && colors.length != vertices)
            throw new IllegalArgumentException("cluster colors must contain one value per vertex");
        for (int index : indices) {
            if (index < 0 || index >= vertices) throw new IllegalArgumentException("cluster index out of range");
        }
        if (sourceTriangles < 0) throw new IllegalArgumentException("sourceTriangles must be nonnegative");
        if (bounds.isEmpty()) throw new IllegalArgumentException("cluster geometry bounds must be nonempty");
    }

    private static float[] copy(float[] values) { return values.clone(); }

    @Override
    public String toString() {
        return "ModelCpuClusterGeometry[triangles=" + triangleCount() + ", sourceTriangles="
                + sourceTriangles + ", proxy=" + proxy + "]";
    }
}
