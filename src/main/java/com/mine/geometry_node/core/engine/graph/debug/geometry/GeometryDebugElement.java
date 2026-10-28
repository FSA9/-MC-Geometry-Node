package com.mine.geometry_node.core.engine.graph.debug.geometry;

import net.minecraft.world.phys.Vec3;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import java.util.UUID;

public record GeometryDebugElement(
        String id,
        String graphId,
        GeometryDebugType type,
        int color,
        boolean showPoints,
        Vec3 center,
        Vec3 size,
        Vec3 rotation,
        float[] vertices,
        int[] edges,
        int[] faces,
        GeometryDebugPlacement placement,
        int anchorEntityId
) {
    public GeometryDebugElement(String id, String graphId, GeometryDebugType type, int color,
                                boolean showPoints, Vec3 center, Vec3 size, Vec3 rotation,
                                float[] vertices, int[] edges, int[] faces) {
        this(id, graphId, type, color, showPoints, center, size, rotation, vertices, edges, faces, (GeometryDebugPlacement) null, -1);
    }

    public GeometryDebugElement(String id, String graphId, GeometryDebugType type, int color,
                                boolean showPoints, Vec3 center, Vec3 size, Vec3 rotation,
                                float[] vertices, int[] edges, int[] faces, UUID anchorUuid, int anchorEntityId) {
        this(id, graphId, type, color, showPoints, center, size, rotation, vertices, edges, faces,
                anchorUuid == null ? null : new GeometryDebugPlacement(SpatialAnchor.entity(null, anchorUuid, SpatialAnchor.Origin.BASE), Vec3.ZERO, Vec3.ZERO), anchorEntityId);
    }

    public UUID anchorUuid() { return placement != null && placement.anchor().requiresEntity() ? placement.anchor().entityId() : null; }
    public SpatialAnchor.Origin anchorOrigin() { return placement == null ? SpatialAnchor.Origin.BASE : placement.anchor().origin(); }

    public GeometryDebugElement withAnchor(UUID uuid, int entityId) {
        return new GeometryDebugElement(id, graphId, type, color, showPoints, center, size, rotation,
                vertices, edges, faces, placement == null && uuid != null
                        ? new GeometryDebugPlacement(SpatialAnchor.entity(null, uuid, SpatialAnchor.Origin.BASE), Vec3.ZERO, Vec3.ZERO)
                        : placement, entityId);
    }

    public GeometryDebugElement withAnchor(UUID uuid, int entityId, SpatialAnchor.Origin origin) {
        return withPlacement(new GeometryDebugPlacement(SpatialAnchor.entity(null, uuid, origin), Vec3.ZERO, Vec3.ZERO), entityId);
    }

    public GeometryDebugElement withPlacement(GeometryDebugPlacement placement, int entityId) {
        return new GeometryDebugElement(id, graphId, type, color, showPoints, center, size, rotation,
                vertices, edges, faces, placement, entityId);
    }

    public GeometryDebugElement {
        type = type != null ? type : GeometryDebugType.MESH;
        center = center != null ? center : Vec3.ZERO;
        size = size != null ? size : Vec3.ZERO;
        rotation = rotation != null ? rotation : Vec3.ZERO;
        vertices = vertices != null ? vertices : new float[0];
        edges = edges != null ? edges : new int[0];
        faces = faces != null ? faces : new int[0];
    }

    public int vertexCount() {
        return vertices.length / 3;
    }

    public int edgeCount() {
        return edges.length / 2;
    }

    public int faceCount() {
        return faces.length / 4;
    }
}
