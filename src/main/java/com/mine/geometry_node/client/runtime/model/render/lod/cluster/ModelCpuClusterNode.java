package com.mine.geometry_node.client.runtime.model.render.lod.cluster;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;

import java.util.Arrays;
import java.util.Objects;

/** Immutable node in a CPU cluster hierarchy. Nodes are indexed by the hierarchy. */
public final class ModelCpuClusterNode {
    private final int nodeId;
    private final int parentId;
    private final int[] children;
    private final int depth;
    private final GltfAsset.Bounds bounds;
    private final float geometricErrorWorld;
    private final int sourceTriangles;
    private final ModelCpuClusterGeometry geometry;

    public ModelCpuClusterNode(int nodeId, int parentId, int[] children, int depth,
                               GltfAsset.Bounds bounds, float geometricErrorWorld,
                               int sourceTriangles, ModelCpuClusterGeometry geometry) {
        this.nodeId = nodeId;
        this.parentId = parentId;
        this.children = Objects.requireNonNull(children).clone();
        this.depth = depth;
        this.bounds = Objects.requireNonNull(bounds);
        this.geometricErrorWorld = geometricErrorWorld;
        this.sourceTriangles = sourceTriangles;
        this.geometry = Objects.requireNonNull(geometry);
        validate();
    }

    public int nodeId() { return nodeId; }
    public int parentId() { return parentId; }
    public int[] children() { return children.clone(); }
    public int firstChild() { return children.length == 0 ? -1 : children[0]; }
    public int childCount() { return children.length; }
    /** Allocation-free child access for the per-frame CPUC selector. */
    public int childAt(int index) { return children[index]; }
    public int depth() { return depth; }
    public GltfAsset.Bounds bounds() { return bounds; }
    public float geometricErrorWorld() { return geometricErrorWorld; }
    public int sourceTriangles() { return sourceTriangles; }
    public ModelCpuClusterGeometry geometry() { return geometry; }
    public int triangleCount() { return geometry.triangleCount(); }
    public boolean leaf() { return children.length == 0; }
    public boolean proxy() { return geometry.proxy(); }

    private void validate() {
        if (nodeId < 0) throw new IllegalArgumentException("nodeId must be nonnegative");
        if (parentId < -1) throw new IllegalArgumentException("parentId must be >= -1");
        if (depth < 0) throw new IllegalArgumentException("depth must be nonnegative");
        if (!Float.isFinite(geometricErrorWorld) || geometricErrorWorld < 0)
            throw new IllegalArgumentException("geometricErrorWorld must be finite and nonnegative");
        if (sourceTriangles <= 0) throw new IllegalArgumentException("sourceTriangles must be positive");
        if (geometry.triangleCount() > sourceTriangles)
            throw new IllegalArgumentException("node geometry cannot exceed its source triangle coverage");
        if (leaf() && geometry.triangleCount() != sourceTriangles)
            throw new IllegalArgumentException("leaf geometry must cover exactly its source triangles");
        if (bounds.isEmpty()) throw new IllegalArgumentException("node bounds must be nonempty");
        int previous = -1;
        for (int child : children) {
            if (child < 0 || child == nodeId || child <= previous)
                throw new IllegalArgumentException("children must be sorted node ids distinct from node");
            previous = child;
        }
        if (leaf() && geometry.proxy()) throw new IllegalArgumentException("leaf geometry cannot be proxy geometry");
        if (!leaf() && !geometry.proxy()) throw new IllegalArgumentException("internal geometry must be proxy geometry");
    }

    @Override
    public String toString() {
        return "ModelCpuClusterNode[id=" + nodeId + ", parent=" + parentId + ", children="
                + Arrays.toString(children) + ", triangles=" + triangleCount() + ", sourceTriangles="
                + sourceTriangles + ", error=" + geometricErrorWorld + "]";
    }
}
