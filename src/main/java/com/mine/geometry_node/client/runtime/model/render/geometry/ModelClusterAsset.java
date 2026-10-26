package com.mine.geometry_node.client.runtime.model.render.geometry;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;

import java.util.List;
import java.util.Objects;

/** Immutable, source-indexed cluster metadata for P7 visibility and light culling.
 *
 * Clusters reference the original primitive index range. They do not own copied or
 * simplified geometry, so building this metadata cannot change rendered vertices.
 */
public final class ModelClusterAsset {
    public static final int DEFAULT_TRIANGLES_PER_CLUSTER = 256;

    private ModelClusterAsset() {
    }

    public record Cluster(
            int primitiveId,
            int clusterIndex,
            int firstIndex,
            int indexCount,
            int triangleCount,
            GltfAsset.Bounds bounds,
            GltfAsset.Material material,
            GltfAsset.AlphaMode alphaMode,
            boolean doubleSided,
            boolean castsShadow,
            boolean unlit) {
        public Cluster {
            if (primitiveId < -1) throw new IllegalArgumentException("primitiveId must be >= -1");
            if (clusterIndex < 0) throw new IllegalArgumentException("clusterIndex must be nonnegative");
            if (firstIndex < 0 || indexCount <= 0 || (firstIndex % 3) != 0 || (indexCount % 3) != 0)
                throw new IllegalArgumentException("cluster index range must contain triangles");
            if (triangleCount != indexCount / 3 || triangleCount <= 0)
                throw new IllegalArgumentException("cluster triangle count does not match index count");
            bounds = Objects.requireNonNull(bounds);
            if (bounds.isEmpty()) throw new IllegalArgumentException("cluster bounds must be nonempty");
            material = Objects.requireNonNull(material);
            alphaMode = Objects.requireNonNull(alphaMode);
        }

        public boolean opaqueOrMask() {
            return alphaMode != GltfAsset.AlphaMode.BLEND;
        }

        public boolean transparent() {
            return alphaMode == GltfAsset.AlphaMode.BLEND;
        }

        public boolean receivesLight() {
            return !unlit;
        }

        public float centerX() { return (bounds.minX() + bounds.maxX()) * .5f; }
        public float centerY() { return (bounds.minY() + bounds.maxY()) * .5f; }
        public float centerZ() { return (bounds.minZ() + bounds.maxZ()) * .5f; }

        public float radiusSquared() {
            float x = bounds.maxX() - bounds.minX();
            float y = bounds.maxY() - bounds.minY();
            float z = bounds.maxZ() - bounds.minZ();
            return .25f * (x * x + y * y + z * z);
        }
    }

    /** Read-only convenience wrapper used by prepared assets and diagnostics. */
    public record PrimitiveClusters(int primitiveId, int triangleCount, GltfAsset.Bounds bounds,
                                    GltfAsset.Material material, List<Cluster> clusters) {
        public PrimitiveClusters {
            if (primitiveId < -1 || triangleCount < 0) throw new IllegalArgumentException("invalid primitive cluster metadata");
            bounds = Objects.requireNonNull(bounds);
            material = Objects.requireNonNull(material);
            clusters = List.copyOf(clusters);
        }

        public int clusterCount() { return clusters.size(); }
    }
}
