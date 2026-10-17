package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugType;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.phys.Vec3;

/** Converts local collision solids using the same facets as CollisionDebugProvider. */
public final class CollisionDebugMeshFactory {
    private CollisionDebugMeshFactory() {}

    public static GeometryDebugElement buildMesh(String id, String graphId, ModelCollisionMesh mesh) {
        var vertices = new FloatArrayList();
        var edges = new IntArrayList();
        var faces = new IntArrayList();
        for (var part : mesh.parts()) {
            var surface = ModelCollisionDebugGeometry.of(part);
            int offset = vertices.size() / 3;
            for (double coordinate : surface.vertices()) vertices.add((float) coordinate);
            for (int edge : surface.edges()) edges.add(edge + offset);
            for (int face : surface.faces()) faces.add(face + offset);
        }
        return new GeometryDebugElement(id, graphId, GeometryDebugType.MESH,
                DebugRenderStyle.COLLISION.color(), DebugRenderStyle.COLLISION.showPoints(),
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                vertices.toFloatArray(), edges.toIntArray(), faces.toIntArray());
    }
}
