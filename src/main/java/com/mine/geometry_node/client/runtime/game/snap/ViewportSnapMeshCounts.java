package com.mine.geometry_node.client.runtime.game.snap;

import com.mine.geometry_node.core.node.value.geometry.GeometryValue;

/** Topology metadata used before snap tessellation or world-space conversion. */
public record ViewportSnapMeshCounts(int vertices, int edges, int faces) {
    public static final ViewportSnapMeshCounts BOX = new ViewportSnapMeshCounts(8, 12, 12);

    public static ViewportSnapMeshCounts of(ViewportSnapGeometry.Mesh mesh) {
        return new ViewportSnapMeshCounts(mesh.vertices().size(), mesh.edges().size(), mesh.faces().size());
    }

    public long elements() {
        return (long) vertices + edges + faces;
    }

    public boolean fits(ViewportSnapSources.Collector collector) {
        return collector.canAdd(vertices, edges, faces);
    }

    /** Matches GeometryDebugMeshFactory's clamping and counts triangles after quad splitting. */
    public static ViewportSnapMeshCounts primitive(GeometryValue.Primitive primitive) {
        return switch (primitive.type()) {
            case CUBE -> {
                int x = Math.clamp(primitive.verticesX(), 2, 32);
                int y = Math.clamp(primitive.verticesY(), 2, 32);
                int z = Math.clamp(primitive.verticesZ(), 2, 32);
                int vertices = x * y * z - (x - 2) * (y - 2) * (z - 2);
                int quads = 2 * ((x - 1) * (y - 1) + (x - 1) * (z - 1) + (y - 1) * (z - 1));
                yield new ViewportSnapMeshCounts(vertices, vertices + quads - 2, 2 * quads);
            }
            case CYLINDER -> {
                int radial = Math.clamp(primitive.radialVertices(), 3, 96);
                int sides = Math.clamp(primitive.sideSegments(), 1, 32);
                int fills = Math.clamp(primitive.fillSegments(), 1, 16);
                int vertices = radial * (sides + 1);
                int edges = radial * (2 * sides + 1);
                int faces = 2 * radial * sides;
                if (primitive.fillType() != GeometryValue.CylinderFillType.NONE) {
                    vertices += 2 * (1 + radial * (fills - 1));
                    faces += 2 * radial * (2 * fills - 1);
                    int capEdges = primitive.fillType() == GeometryValue.CylinderFillType.TRIANGLE
                            ? radial * (2 * fills - 1) : radial * (fills - 1);
                    edges += 2 * capEdges;
                }
                yield new ViewportSnapMeshCounts(vertices, edges, faces);
            }
            case UV_SPHERE -> {
                int segments = Math.clamp(primitive.sphereSegments(), 3, 128);
                int rings = Math.clamp(primitive.sphereRings(), 2, 64);
                yield new ViewportSnapMeshCounts(segments * (rings - 1) + 2,
                        segments * (2 * rings - 1), 2 * segments * (rings - 1));
            }
        };
    }
}
