package com.mine.geometry_node.client.model.render.lod.cluster;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable CPU cluster hierarchy sidecar shared by main and shadow traversal. */
public final class ModelCpuClusterHierarchy {
    public enum Status { READY, DISABLED, FAILED }

    private final int primitiveId;
    private final GltfAsset.Material material;
    private final GltfAsset.AlphaMode alphaMode;
    private final List<ModelCpuClusterNode> nodes;
    private final int rootNode;
    private final int sourceTriangles;
    private final int leafTriangles;
    private final Status status;
    private final String reason;
    private final long cpuBytes;

    private ModelCpuClusterHierarchy(int primitiveId, GltfAsset.Material material, GltfAsset.AlphaMode alphaMode,
                                     List<ModelCpuClusterNode> nodes, int rootNode, int sourceTriangles,
                                     int leafTriangles, Status status, String reason) {
        this.primitiveId = primitiveId;
        this.material = Objects.requireNonNull(material);
        this.alphaMode = Objects.requireNonNull(alphaMode);
        this.nodes = List.copyOf(nodes);
        this.rootNode = rootNode;
        this.sourceTriangles = sourceTriangles;
        this.leafTriangles = leafTriangles;
        this.status = Objects.requireNonNull(status);
        this.reason = reason == null ? "" : reason;
        this.cpuBytes = estimateBytes(this.nodes);
        validate();
    }

    public static ModelCpuClusterHierarchy ready(int primitiveId, GltfAsset.Material material,
                                                 List<ModelCpuClusterNode> nodes, int rootNode,
                                                 int sourceTriangles, int leafTriangles) {
        return new ModelCpuClusterHierarchy(primitiveId, material, material.alphaMode(), nodes, rootNode,
                sourceTriangles, leafTriangles, Status.READY, "");
    }

    public static ModelCpuClusterHierarchy disabled(int primitiveId, GltfAsset.Material material,
                                                    int sourceTriangles, String reason) {
        return new ModelCpuClusterHierarchy(primitiveId, material, material.alphaMode(), List.of(), -1,
                Math.max(0, sourceTriangles), 0, Status.DISABLED, reason);
    }

    public static ModelCpuClusterHierarchy failed(int primitiveId, GltfAsset.Material material,
                                                  int sourceTriangles, String reason) {
        return new ModelCpuClusterHierarchy(primitiveId, material, material.alphaMode(), List.of(), -1,
                Math.max(0, sourceTriangles), 0, Status.FAILED, reason);
    }

    public int primitiveId() { return primitiveId; }
    public GltfAsset.Material material() { return material; }
    public GltfAsset.AlphaMode alphaMode() { return alphaMode; }
    public List<ModelCpuClusterNode> nodes() { return nodes; }
    public int nodeCount() { return nodes.size(); }
    public int rootNode() { return rootNode; }
    public ModelCpuClusterNode root() { return rootNode < 0 ? null : nodes.get(rootNode); }
    public int sourceTriangles() { return sourceTriangles; }
    public int leafTriangles() { return leafTriangles; }
    public Status status() { return status; }
    public String reason() { return reason; }
    public boolean available() { return status == Status.READY && rootNode >= 0; }
    public long cpuBytes() { return cpuBytes; }

    public ModelCpuClusterNode node(int nodeId) {
        if (nodeId < 0 || nodeId >= nodes.size()) throw new IndexOutOfBoundsException("nodeId=" + nodeId);
        return nodes.get(nodeId);
    }

    public List<Integer> leafNodeIds() {
        if (!available()) return List.of();
        List<Integer> result = new ArrayList<>();
        for (ModelCpuClusterNode node : nodes) if (node.leaf()) result.add(node.nodeId());
        return List.copyOf(result);
    }

    public int leafCount() {
        int count = 0;
        for (ModelCpuClusterNode node : nodes) if (node.leaf()) count++;
        return count;
    }

    /** Validates a visible hierarchy frontier and returns its geometry triangle count. */
    public int validateFrontier(List<Integer> nodeIds) {
        Objects.requireNonNull(nodeIds, "nodeIds");
        if (!available()) {
            if (!nodeIds.isEmpty()) throw new IllegalArgumentException("unavailable hierarchy cannot have a frontier");
            return 0;
        }
        Set<Integer> selected = new HashSet<>();
        long triangles = 0;
        long represented = 0;
        for (int nodeId : nodeIds) {
            if (nodeId < 0 || nodeId >= nodes.size()) throw new IllegalArgumentException("frontier node id out of range");
            if (!selected.add(nodeId)) throw new IllegalArgumentException("frontier contains duplicate node " + nodeId);
            ModelCpuClusterNode node = nodes.get(nodeId);
            triangles = Math.addExact(triangles, node.triangleCount());
            represented = Math.addExact(represented, node.sourceTriangles());
            if (triangles > sourceTriangles || represented > sourceTriangles)
                throw new IllegalArgumentException("frontier exceeds source triangle coverage");
        }
        for (int nodeId : selected) {
            int parent = nodes.get(nodeId).parentId();
            while (parent >= 0) {
                if (selected.contains(parent))
                    throw new IllegalArgumentException("frontier contains ancestor and descendant");
                parent = nodes.get(parent).parentId();
            }
        }
        return Math.toIntExact(triangles);
    }

    public Map<String, Object> report() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status.name());
        result.put("reason", reason);
        result.put("primitiveId", primitiveId);
        result.put("alphaMode", alphaMode.name());
        result.put("sourceTriangles", sourceTriangles);
        result.put("leafTriangles", leafTriangles);
        result.put("leafCount", leafCount());
        result.put("nodeCount", nodeCount());
        result.put("rootNode", rootNode);
        result.put("cpuBytes", cpuBytes);
        if (available()) {
            int maxDepth = 0;
            long proxyTriangles = 0;
            for (ModelCpuClusterNode node : nodes) {
                maxDepth = Math.max(maxDepth, node.depth());
                if (!node.leaf()) proxyTriangles += node.triangleCount();
            }
            result.put("maxDepth", maxDepth);
            result.put("proxyTriangles", proxyTriangles);
        }
        return Collections.unmodifiableMap(result);
    }

    private void validate() {
        if (primitiveId < -1) throw new IllegalArgumentException("primitiveId must be >= -1");
        if (sourceTriangles < 0) throw new IllegalArgumentException("sourceTriangles must be nonnegative");
        if (leafTriangles < 0) throw new IllegalArgumentException("leafTriangles must be nonnegative");
        if (status == Status.READY) {
            if (alphaMode == GltfAsset.AlphaMode.BLEND)
                throw new IllegalArgumentException("BLEND hierarchy must be disabled");
            if (nodes.isEmpty() || rootNode < 0 || rootNode >= nodes.size())
                throw new IllegalArgumentException("ready hierarchy requires a root");
            if (nodes.get(rootNode).parentId() != -1)
                throw new IllegalArgumentException("root parent must be -1");
            int[] parentReferences = new int[nodes.size()];
            for (int i = 0; i < nodes.size(); i++) {
                ModelCpuClusterNode node = nodes.get(i);
                if (node.nodeId() != i) throw new IllegalArgumentException("node ids must be dense");
                int represented = node.sourceTriangles();
                if (node.geometry().sourceTriangles() != represented)
                    throw new IllegalArgumentException("node/source triangle metadata mismatch");
                if (node.triangleCount() > represented)
                    throw new IllegalArgumentException("cluster geometry exceeds represented source triangles");
                if (node.leaf()) {
                    if (node.triangleCount() != represented)
                        throw new IllegalArgumentException("leaf geometry must preserve source triangles");
                } else {
                    long childTriangles = 0;
                    for (int child : node.children()) {
                        if (child < 0 || child >= nodes.size() || nodes.get(child).parentId() != i)
                            throw new IllegalArgumentException("child parent link mismatch");
                        parentReferences[child]++;
                        childTriangles = Math.addExact(childTriangles, nodes.get(child).sourceTriangles());
                    }
                    if (childTriangles != represented)
                        throw new IllegalArgumentException("internal source triangle partition mismatch");
                }
                if (represented > sourceTriangles)
                    throw new IllegalArgumentException("node source triangles exceed hierarchy source triangles");
            }
            if (parentReferences[rootNode] != 0)
                throw new IllegalArgumentException("root cannot be referenced as a child");
            if (nodes.get(rootNode).sourceTriangles() != sourceTriangles)
                throw new IllegalArgumentException("root source triangles mismatch");
            for (int i = 0; i < nodes.size(); i++) {
                if (i == rootNode) continue;
                if (parentReferences[i] != 1)
                    throw new IllegalArgumentException("every non-root node must have one parent");
            }
            Set<Integer> reachable = new HashSet<>();
            ArrayDeque<Integer> stack = new ArrayDeque<>();
            stack.push(rootNode);
            long leafSource = 0;
            while (!stack.isEmpty()) {
                int nodeId = stack.pop();
                if (!reachable.add(nodeId)) throw new IllegalArgumentException("hierarchy contains a cycle or duplicate path");
                ModelCpuClusterNode node = nodes.get(nodeId);
                if (node.leaf()) leafSource = Math.addExact(leafSource, node.sourceTriangles());
                for (int child : node.children()) stack.push(child);
            }
            if (reachable.size() != nodes.size()) throw new IllegalArgumentException("hierarchy contains unreachable nodes");
            if (leafSource != sourceTriangles) throw new IllegalArgumentException("leaf source coverage mismatch");
        } else if (!nodes.isEmpty() || rootNode != -1) {
            throw new IllegalArgumentException("disabled hierarchy cannot contain nodes");
        }
    }

    private static long estimateBytes(List<ModelCpuClusterNode> nodes) {
        long bytes = 128L;
        for (ModelCpuClusterNode node : nodes) {
            bytes = Math.addExact(bytes, node.geometry().cpuBytes());
            bytes = Math.addExact(bytes, 96L + (long) node.childCount() * Integer.BYTES);
        }
        return bytes;
    }
}
