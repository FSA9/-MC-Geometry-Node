package com.mine.geometry_node.core.engine.blueprint.debug;

import net.minecraft.world.phys.Vec3;

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
        int[] faces
) {
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
