package com.mine.geometry_node.client.runtime.model.render.lod.cluster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable draw plan produced by a future CPU hierarchy selector. */
public final class ModelCpuClusterPlan {
    public enum Pass { MAIN, SHADOW }

    public record DrawItem(int nodeId, ModelCpuClusterGeometry geometry, int triangleCount,
                           int sourceTriangles, boolean proxy) {
        public DrawItem {
            Objects.requireNonNull(geometry);
            if (nodeId < 0 || triangleCount <= 0 || sourceTriangles <= 0)
                throw new IllegalArgumentException("invalid cluster draw item");
            if (triangleCount != geometry.triangleCount() || proxy != geometry.proxy())
                throw new IllegalArgumentException("draw item geometry metadata mismatch");
        }
    }

    private final ModelCpuClusterHierarchy hierarchy;
    private final Pass pass;
    private final List<DrawItem> draws;
    private final int candidateNodes;
    private final int culledNodes;
    private final String fallbackReason;

    public ModelCpuClusterPlan(ModelCpuClusterHierarchy hierarchy, Pass pass, List<DrawItem> draws,
                               int candidateNodes, int culledNodes, String fallbackReason) {
        this.hierarchy = Objects.requireNonNull(hierarchy);
        this.pass = Objects.requireNonNull(pass);
        this.draws = List.copyOf(draws);
        this.candidateNodes = candidateNodes;
        this.culledNodes = culledNodes;
        this.fallbackReason = fallbackReason == null ? "" : fallbackReason;
        if (candidateNodes < 0 || culledNodes < 0 || culledNodes > candidateNodes)
            throw new IllegalArgumentException("invalid cluster plan counters");
        List<Integer> nodeIds = this.draws.stream().map(DrawItem::nodeId).toList();
        int selected = hierarchy.validateFrontier(nodeIds);
        if (selected != selectedTriangles())
            throw new IllegalArgumentException("cluster plan selected triangle count mismatch");
    }

    public static ModelCpuClusterPlan fromLeaves(ModelCpuClusterHierarchy hierarchy, Pass pass) {
        Objects.requireNonNull(hierarchy);
        List<DrawItem> items = new ArrayList<>();
        if (hierarchy.available()) {
            for (int nodeId : hierarchy.leafNodeIds()) {
                ModelCpuClusterNode node = hierarchy.node(nodeId);
                items.add(new DrawItem(nodeId, node.geometry(), node.triangleCount(),
                        node.sourceTriangles(), false));
            }
        }
        return new ModelCpuClusterPlan(hierarchy, pass, items,
                hierarchy.available() ? hierarchy.nodeCount() : 0, 0,
                hierarchy.available() ? "" : hierarchy.reason());
    }

    public static ModelCpuClusterPlan rootFallback(ModelCpuClusterHierarchy hierarchy, Pass pass,
                                                   String reason) {
        Objects.requireNonNull(hierarchy);
        List<DrawItem> items = new ArrayList<>();
        if (hierarchy.available()) {
            ModelCpuClusterNode root = hierarchy.root();
            items.add(new DrawItem(root.nodeId(), root.geometry(), root.triangleCount(),
                    root.sourceTriangles(), root.proxy()));
        }
        return new ModelCpuClusterPlan(hierarchy, pass, items,
                hierarchy.available() ? 1 : 0, 0, reason);
    }

    public ModelCpuClusterHierarchy hierarchy() { return hierarchy; }
    public Pass pass() { return pass; }
    public List<DrawItem> draws() { return draws; }
    public int candidateNodes() { return candidateNodes; }
    public int visibleNodes() { return draws.size(); }
    public int culledNodes() { return culledNodes; }
    public String fallbackReason() { return fallbackReason; }
    public int selectedTriangles() { return draws.stream().mapToInt(DrawItem::triangleCount).sum(); }
    public int representedSourceTriangles() { return draws.stream().mapToInt(DrawItem::sourceTriangles).sum(); }

    public Map<String, Object> report() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", pass.name());
        result.put("candidateNodes", candidateNodes);
        result.put("visibleNodes", visibleNodes());
        result.put("culledNodes", culledNodes);
        result.put("selectedTriangles", selectedTriangles());
        result.put("representedSourceTriangles", representedSourceTriangles());
        result.put("fallbackReason", fallbackReason);
        return Map.copyOf(result);
    }
}
