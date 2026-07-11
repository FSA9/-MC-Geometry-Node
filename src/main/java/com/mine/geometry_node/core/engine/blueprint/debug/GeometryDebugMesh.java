package com.mine.geometry_node.core.engine.blueprint.debug;

import net.minecraft.world.phys.Vec3;

public record GeometryDebugMesh(
        String id,
        String graphId,
        Vec3 center,
        float[] vertices,
        int[] edges,
        int[] faces
) {
    public GeometryDebugMesh {
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
