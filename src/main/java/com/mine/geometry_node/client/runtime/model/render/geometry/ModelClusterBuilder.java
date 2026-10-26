package com.mine.geometry_node.client.runtime.model.render.geometry;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Worker-side builder for source-indexed P7 clusters. No geometry simplification or LOD is performed. */
public final class ModelClusterBuilder {
    private ModelClusterBuilder() {
    }

    public static List<ModelClusterAsset.Cluster> build(int primitiveId, float[] positions, int[] indices,
                                                         GltfAsset.Material material, ModelCancellation token) throws IOException {
        return build(primitiveId, positions, indices, material,
                ModelClusterAsset.DEFAULT_TRIANGLES_PER_CLUSTER, token);
    }

    public static List<ModelClusterAsset.Cluster> build(int primitiveId, float[] positions, int[] indices,
                                                         GltfAsset.Material material, int trianglesPerCluster,
                                                         ModelCancellation token) throws IOException {
        Objects.requireNonNull(positions);
        Objects.requireNonNull(indices);
        Objects.requireNonNull(material);
        Objects.requireNonNull(token);
        if (primitiveId < -1) throw new IllegalArgumentException("primitiveId must be >= -1");
        if (positions.length == 0 || positions.length % 3 != 0) throw new IllegalArgumentException("positions must be xyz triples");
        if (indices.length == 0 || indices.length % 3 != 0) throw new IllegalArgumentException("indices must contain triangles");
        if (trianglesPerCluster <= 0) throw new IllegalArgumentException("trianglesPerCluster must be positive");
        int vertexCount = positions.length / 3;
        int clusterIndices = Math.multiplyExact(trianglesPerCluster, 3);
        List<ModelClusterAsset.Cluster> result = new ArrayList<>((int) ((indices.length + (long) clusterIndices - 1) / clusterIndices));
        for (int first = 0, clusterIndex = 0; first < indices.length; clusterIndex++) {
            token.check();
            int indexCount = Math.min(indices.length - first, clusterIndices);
            float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
            float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            for (int offset = 0; offset < indexCount; offset++) {
                if ((offset & 4095) == 0) token.check();
                int vertex = indices[first + offset];
                if (vertex < 0 || vertex >= vertexCount) throw new IllegalArgumentException("cluster index out of range");
                int p = vertex * 3;
                minX = Math.min(minX, positions[p]); minY = Math.min(minY, positions[p + 1]); minZ = Math.min(minZ, positions[p + 2]);
                maxX = Math.max(maxX, positions[p]); maxY = Math.max(maxY, positions[p + 1]); maxZ = Math.max(maxZ, positions[p + 2]);
            }
            GltfAsset.Bounds bounds = new GltfAsset.Bounds(minX, minY, minZ, maxX, maxY, maxZ);
            result.add(new ModelClusterAsset.Cluster(primitiveId, clusterIndex, first, indexCount,
                    indexCount / 3, bounds, material, material.alphaMode(), material.doubleSided(),
                    material.alphaMode() != GltfAsset.AlphaMode.BLEND, material.unlit()));
            first += indexCount;
        }
        return List.copyOf(result);
    }

    public static ModelClusterAsset.PrimitiveClusters primitive(int primitiveId, float[] positions, int[] indices,
                                                                  GltfAsset.Material material, ModelCancellation token) throws IOException {
        List<ModelClusterAsset.Cluster> clusters = build(primitiveId, positions, indices, material, token);
        GltfAsset.Bounds bounds = GltfAsset.Bounds.empty();
        for (int p = 0; p < positions.length; p += 3) bounds = bounds.include(positions[p], positions[p + 1], positions[p + 2]);
        for (ModelClusterAsset.Cluster cluster : clusters) {
            GltfAsset.Bounds b = cluster.bounds();
            bounds = bounds.include(b.minX(), b.minY(), b.minZ()).include(b.maxX(), b.maxY(), b.maxZ());
        }
        return new ModelClusterAsset.PrimitiveClusters(primitiveId, indices.length / 3, bounds, material, clusters);
    }
}
