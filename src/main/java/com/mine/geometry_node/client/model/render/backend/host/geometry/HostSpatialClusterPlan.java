package com.mine.geometry_node.client.model.render.backend.host.geometry;

import com.mine.geometry_node.core.engine.system.model.domain.ModelBounds;
import com.mine.geometry_node.core.engine.system.model.domain.ModelVector3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Immutable, asset-level spatial partition of one projected primitive. */
public final class HostSpatialClusterPlan {
    public static final int MIN_CLUSTERED_TRIANGLES = 2048;
    public static final int TARGET_TRIANGLES_PER_LEAF = 512;
    public static final int HIERARCHY_FANOUT = 4;
    public static final long MAX_METADATA_BYTES = 16L << 20;

    private static final long PERMUTATION_BYTES_PER_TRIANGLE = Integer.BYTES;
    private static final long LEAF_METADATA_BYTES = 32;
    private static final long NODE_METADATA_BYTES = 40;

    private final int triangleCount;
    private final int[] trianglePermutation;
    private final List<Leaf> leaves;
    private final List<Node> nodes;
    private final int rootNode;
    private final Mode mode;
    private final long metadataBytes;

    private HostSpatialClusterPlan(int triangleCount, int[] trianglePermutation, List<Leaf> leaves,
                                   List<Node> nodes, int rootNode, Mode mode, long metadataBytes) {
        this.triangleCount = triangleCount;
        this.trianglePermutation = trianglePermutation;
        this.leaves = List.copyOf(leaves);
        this.nodes = List.copyOf(nodes);
        this.rootNode = rootNode;
        this.mode = mode;
        this.metadataBytes = metadataBytes;
    }

    static HostSpatialClusterPlan build(float[] vertices) {
        if (vertices.length % 36 != 0) {
            throw new IllegalArgumentException("HOST geometry does not contain complete triangles");
        }
        int triangles = vertices.length / 36;
        if (triangles == 0) return empty();
        ModelBounds primitiveBounds = bounds(vertices, 0, triangles, null);
        if (triangles < MIN_CLUSTERED_TRIANGLES) {
            return single(triangles, primitiveBounds, Mode.SINGLE_SMALL);
        }

        int leafCount = divideRoundUp(triangles, TARGET_TRIANGLES_PER_LEAF);
        int nodeCount = estimatedNodeCount(leafCount);
        long estimatedBytes = estimatedMetadataBytes(triangles, leafCount, nodeCount);
        if (estimatedBytes > MAX_METADATA_BYTES) {
            return single(triangles, primitiveBounds, Mode.SINGLE_METADATA_BUDGET);
        }

        long[] spatialOrder = new long[triangles];
        float minX = primitiveBounds.min().x(), minY = primitiveBounds.min().y(), minZ = primitiveBounds.min().z();
        float rangeX = primitiveBounds.max().x() - minX;
        float rangeY = primitiveBounds.max().y() - minY;
        float rangeZ = primitiveBounds.max().z() - minZ;
        for (int triangle = 0; triangle < triangles; triangle++) {
            int base = triangle * 36;
            float x = (vertices[base] + vertices[base + 12] + vertices[base + 24]) / 3.0F;
            float y = (vertices[base + 1] + vertices[base + 13] + vertices[base + 25]) / 3.0F;
            float z = (vertices[base + 2] + vertices[base + 14] + vertices[base + 26]) / 3.0F;
            int morton = morton10(quantize(x, minX, rangeX), quantize(y, minY, rangeY),
                    quantize(z, minZ, rangeZ));
            spatialOrder[triangle] = ((long) morton << 32) | Integer.toUnsignedLong(triangle);
        }
        Arrays.sort(spatialOrder);
        int[] permutation = new int[triangles];
        for (int index = 0; index < triangles; index++) permutation[index] = (int) spatialOrder[index];

        List<Leaf> leaves = new ArrayList<>(leafCount);
        List<Node> nodes = new ArrayList<>(nodeCount);
        int baseSize = triangles / leafCount;
        int largerLeaves = triangles % leafCount;
        int firstTriangle = 0;
        for (int leaf = 0; leaf < leafCount; leaf++) {
            int count = baseSize + (leaf < largerLeaves ? 1 : 0);
            ModelBounds bounds = bounds(vertices, firstTriangle, count, permutation);
            leaves.add(new Leaf(firstTriangle, count, bounds));
            nodes.add(Node.leaf(bounds, leaf));
            firstTriangle += count;
        }
        int levelStart = 0;
        int levelCount = leafCount;
        while (levelCount > 1) {
            int parentStart = nodes.size();
            for (int offset = 0; offset < levelCount; offset += HIERARCHY_FANOUT) {
                int children = Math.min(HIERARCHY_FANOUT, levelCount - offset);
                int firstChild = levelStart + offset;
                ModelBounds parentBounds = nodes.get(firstChild).bounds();
                for (int child = 1; child < children; child++) {
                    parentBounds = union(parentBounds, nodes.get(firstChild + child).bounds());
                }
                nodes.add(Node.branch(parentBounds, firstChild, children));
            }
            levelStart = parentStart;
            levelCount = nodes.size() - parentStart;
        }
        return new HostSpatialClusterPlan(triangles, permutation, leaves, nodes, nodes.size() - 1,
                Mode.HIERARCHICAL, estimatedBytes);
    }

    private static HostSpatialClusterPlan empty() {
        return new HostSpatialClusterPlan(0, null, List.of(), List.of(), -1, Mode.EMPTY, 0);
    }

    private static HostSpatialClusterPlan single(int triangles, ModelBounds bounds, Mode mode) {
        Leaf leaf = new Leaf(0, triangles, bounds);
        return new HostSpatialClusterPlan(triangles, null, List.of(leaf), List.of(Node.leaf(bounds, 0)), 0,
                mode, LEAF_METADATA_BYTES + NODE_METADATA_BYTES);
    }

    private static ModelBounds bounds(float[] vertices, int first, int count, int[] permutation) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int index = first; index < first + count; index++) {
            int triangle = permutation == null ? index : permutation[index];
            int base = triangle * 36;
            for (int vertex = 0; vertex < 3; vertex++) {
                int position = base + vertex * 12;
                float x = vertices[position], y = vertices[position + 1], z = vertices[position + 2];
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                    throw new IllegalArgumentException("HOST geometry position must be finite");
                }
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
        }
        return new ModelBounds(new ModelVector3(minX, minY, minZ), new ModelVector3(maxX, maxY, maxZ));
    }

    private static ModelBounds union(ModelBounds left, ModelBounds right) {
        return new ModelBounds(new ModelVector3(
                Math.min(left.min().x(), right.min().x()),
                Math.min(left.min().y(), right.min().y()),
                Math.min(left.min().z(), right.min().z())), new ModelVector3(
                Math.max(left.max().x(), right.max().x()),
                Math.max(left.max().y(), right.max().y()),
                Math.max(left.max().z(), right.max().z())));
    }

    private static int quantize(float value, float minimum, float range) {
        if (range <= 0) return 0;
        return Math.max(0, Math.min(1023, Math.round((value - minimum) / range * 1023.0F)));
    }

    private static int morton10(int x, int y, int z) {
        return spread10(x) | spread10(y) << 1 | spread10(z) << 2;
    }

    private static int spread10(int value) {
        value &= 0x3ff;
        value = (value | value << 16) & 0x030000FF;
        value = (value | value << 8) & 0x0300F00F;
        value = (value | value << 4) & 0x030C30C3;
        value = (value | value << 2) & 0x09249249;
        return value;
    }

    private static int divideRoundUp(int value, int divisor) {
        return value == 0 ? 0 : 1 + (value - 1) / divisor;
    }

    private static int estimatedNodeCount(int leaves) {
        int nodes = leaves;
        int level = leaves;
        while (level > 1) {
            level = divideRoundUp(level, HIERARCHY_FANOUT);
            nodes = Math.addExact(nodes, level);
        }
        return nodes;
    }

    public static long estimatedMetadataBytes(int triangles) {
        if (triangles < 0) throw new IllegalArgumentException("negative HOST triangle count");
        if (triangles < MIN_CLUSTERED_TRIANGLES) return triangles == 0 ? 0 : LEAF_METADATA_BYTES + NODE_METADATA_BYTES;
        int leaves = divideRoundUp(triangles, TARGET_TRIANGLES_PER_LEAF);
        return estimatedMetadataBytes(triangles, leaves, estimatedNodeCount(leaves));
    }

    public static long retainedMetadataBytes(int triangles) {
        long estimated = estimatedMetadataBytes(triangles);
        return estimated > MAX_METADATA_BYTES ? LEAF_METADATA_BYTES + NODE_METADATA_BYTES : estimated;
    }

    private static long estimatedMetadataBytes(int triangles, int leaves, int nodes) {
        return Math.addExact(Math.multiplyExact((long) triangles, PERMUTATION_BYTES_PER_TRIANGLE),
                Math.addExact(Math.multiplyExact((long) leaves, LEAF_METADATA_BYTES),
                        Math.multiplyExact((long) nodes, NODE_METADATA_BYTES)));
    }

    public int triangleCount() { return triangleCount; }
    public int sourceTriangle(int orderedTriangle) {
        if (orderedTriangle < 0 || orderedTriangle >= triangleCount) throw new IndexOutOfBoundsException();
        return trianglePermutation == null ? orderedTriangle : trianglePermutation[orderedTriangle];
    }
    public List<Leaf> leaves() { return leaves; }
    public List<Node> nodes() { return nodes; }
    public int rootNode() { return rootNode; }
    public Mode mode() { return mode; }
    public boolean hierarchical() { return mode == Mode.HIERARCHICAL; }
    public long metadataBytes() { return metadataBytes; }

    public enum Mode { EMPTY, SINGLE_SMALL, SINGLE_METADATA_BUDGET, HIERARCHICAL }

    public record Leaf(int firstTriangle, int triangleCount, ModelBounds bounds) {
        public Leaf {
            if (firstTriangle < 0 || triangleCount < 1 || bounds == null) {
                throw new IllegalArgumentException("invalid HOST cluster leaf");
            }
        }
    }

    public record Node(ModelBounds bounds, int firstChild, int childCount, int leafIndex) {
        private static Node leaf(ModelBounds bounds, int leaf) { return new Node(bounds, -1, 0, leaf); }
        private static Node branch(ModelBounds bounds, int first, int count) { return new Node(bounds, first, count, -1); }
        public Node {
            if (bounds == null || (leafIndex >= 0) == (childCount > 0) || childCount < 0) {
                throw new IllegalArgumentException("invalid HOST cluster node");
            }
            if (childCount > 0 && firstChild < 0) throw new IllegalArgumentException("invalid HOST branch range");
        }
        public boolean leaf() { return leafIndex >= 0; }
    }
}
