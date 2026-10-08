package com.mine.geometry_node.client.model.render.lod.model;

import com.mine.geometry_node.client.model.render.geometry.ModelRenderAsset.Occurrence;
import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import org.joml.Vector3f;

import java.util.List;

/** One mesh-bearing glTF node, with immutable shared geometry and asset-space source bounds. */
public final class ModelLodObject {
    private final int index, nodeIndex;
    private final String name, id;
    private final GltfAsset.Bounds bounds;
    private final List<List<Occurrence>> levels;
    private final long[] triangles = new long[ModelLodPolicy.LEVEL_COUNT];
    private final long transparentTriangles;

    ModelLodObject(int index, int nodeIndex, String name, List<List<Occurrence>> levels) {
        if (levels.size() != ModelLodPolicy.LEVEL_COUNT) throw new IllegalArgumentException("Four object levels required");
        this.index = index; this.nodeIndex = nodeIndex; this.name = name;
        this.id = nodeIndex >= 0 ? "node_" + nodeIndex : "object_" + index;
        this.levels = levels.stream().map(List::copyOf).toList();
        long transparent = 0;
        GltfAsset.Bounds sourceBounds = GltfAsset.Bounds.empty();
        Vector3f corner = new Vector3f();
        for (int level = 0; level < levels.size(); level++) {
            for (var occurrence : levels.get(level)) for (var primitive : occurrence.primitives()) {
                if (primitive.material.alphaMode() != GltfAsset.AlphaMode.BLEND)
                    triangles[level] += primitive.indices.length / 3;
                else if (level == 0) transparent += primitive.indices.length / 3;
                if (level != 0 || primitive.bounds.isEmpty()) continue;
                var b = primitive.bounds;
                for (int c = 0; c < 8; c++) {
                    occurrence.transform().transformPosition((c & 1) == 0 ? b.minX() : b.maxX(),
                            (c & 2) == 0 ? b.minY() : b.maxY(), (c & 4) == 0 ? b.minZ() : b.maxZ(), corner);
                    sourceBounds = sourceBounds.include(corner.x, corner.y, corner.z);
                }
            }
        }
        bounds = sourceBounds;
        transparentTriangles = transparent;
    }

    public int index() { return index; }
    public int nodeIndex() { return nodeIndex; }
    public String id() { return id; }
    public String name() { return name; }
    public GltfAsset.Bounds bounds() { return bounds; }
    public List<Occurrence> occurrences(int level) { ModelLodPolicy.requireLevel(level); return levels.get(level); }
    /** OPAQUE and MASK triangles, matching the main/shadow LOD submission domain. */
    public long triangles(int level) { ModelLodPolicy.requireLevel(level); return triangles[level]; }
    public long transparentTriangles() { return transparentTriangles; }
    public boolean hasOpaque() { return triangles[0] > 0; }
    public boolean hasBlend() { return transparentTriangles > 0; }

}
