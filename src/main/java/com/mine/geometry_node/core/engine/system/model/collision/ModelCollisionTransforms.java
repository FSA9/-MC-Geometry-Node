package com.mine.geometry_node.core.engine.system.model.collision;

import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import org.joml.Vector3d;

import java.util.ArrayList;

/** Applies instance transforms to cached scene-local convex geometry. */
public final class ModelCollisionTransforms {
    private ModelCollisionTransforms() {}

    /** Transform vertices and inverse-transpose planes together, including mirrored scaling. */
    public static ModelCollisionMesh transform(
            ModelCollisionMesh mesh, org.joml.Matrix4dc matrix, ModelCancellation cancellation)
            throws ModelProblem {
        cancellation.check();
        if (!Double.isFinite(matrix.determinant()) || Math.abs(matrix.determinant()) < 1e-18)
            throw new IllegalArgumentException("singular collision transform");
        var planeMatrix = new org.joml.Matrix4d(matrix).invert().transpose();
        var vertex = new Vector3d();
        var plane = new org.joml.Vector4d();
        var parts = new ArrayList<ModelCollisionMesh.Part>(mesh.parts().size());
        for (var part : mesh.parts()) {
            cancellation.check();
            double[] vertices = part.vertices(), planes = part.planes();
            int[] triangles = part.triangles();
            for (int i = 0; i < vertices.length; i += 3) {
                matrix.transformPosition(vertex.set(vertices[i], vertices[i + 1], vertices[i + 2]));
                vertices[i] = vertex.x;
                vertices[i + 1] = vertex.y;
                vertices[i + 2] = vertex.z;
            }
            for (int i = 0; i < planes.length; i += 4) {
                planeMatrix.transform(
                        plane.set(planes[i], planes[i + 1], planes[i + 2], -planes[i + 3]));
                double length =
                        Math.sqrt(plane.x * plane.x + plane.y * plane.y + plane.z * plane.z);
                planes[i] = plane.x / length;
                planes[i + 1] = plane.y / length;
                planes[i + 2] = plane.z / length;
                planes[i + 3] = -plane.w / length;
            }
            if (matrix.determinant3x3() < 0)
                for (int i = 0; i < triangles.length; i += 3) {
                    int swap = triangles[i + 1];
                    triangles[i + 1] = triangles[i + 2];
                    triangles[i + 2] = swap;
                }
            parts.add(new ModelCollisionMesh.Part(vertices, triangles, planes));
        }
        return new ModelCollisionMesh(
                parts,
                mesh.sourceTriangles(),
                mesh.skippedTriangles(),
                mesh.incompleteObjects(),
                mesh.generationIssues());
    }
}
