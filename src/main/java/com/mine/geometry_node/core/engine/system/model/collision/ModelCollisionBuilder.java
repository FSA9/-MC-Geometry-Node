package com.mine.geometry_node.core.engine.system.model.collision;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import org.joml.Matrix4d;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds closed volumes per scene object, retaining successful components on local failure. */
public final class ModelCollisionBuilder {
    public static final int MAX_PARTS = 300_000;
    public static final int MAX_TRIANGLES = 4_000_000;
    private static final long GENERATION_NANOS = 15_000_000_000L;

    private ModelCollisionBuilder() {}

    record Built(
            ModelCollisionMesh mesh,
            long sceneBakeNanos,
            long volumeGenerationNanos,
            long mergeNanos) {}

    public static ModelCollisionMesh build(
            GltfAsset asset,
            int sceneIndex,
            Matrix4fc transform,
            ModelCollisionSettings settings,
            ModelCancellation token)
            throws IOException {
        return buildMeasured(asset, sceneIndex, transform, settings, token).mesh();
    }

    static Built buildMeasured(
            GltfAsset asset,
            int sceneIndex,
            Matrix4fc transform,
            ModelCollisionSettings settings,
            ModelCancellation token)
            throws IOException {
        return generate(asset, sceneIndex, transform, settings, token, GENERATION_NANOS);
    }

    static ModelCollisionMesh build(
            GltfAsset asset,
            int sceneIndex,
            Matrix4fc transform,
            ModelCollisionSettings settings,
            ModelCancellation token,
            long budgetNanos)
            throws IOException {
        return generate(asset, sceneIndex, transform, settings, token, budgetNanos).mesh();
    }

    private static Built generate(
            GltfAsset asset,
            int sceneIndex,
            Matrix4fc transform,
            ModelCollisionSettings settings,
            ModelCancellation token,
            long budgetNanos)
            throws IOException {
        long started = System.nanoTime();
        long deadline = System.nanoTime() + Math.min(GENERATION_NANOS, Math.max(0, budgetNanos));
        check(token, deadline);
        var inputs = bake(asset, sceneIndex, transform, token, deadline);
        long baked = System.nanoTime();
        List<ModelCollisionMesh.Part> parts = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        int sourceTriangles = 0, skippedTriangles = 0, incompleteObjects = 0;
        double pendingWeight =
                inputs.stream()
                        .mapToDouble(
                                object ->
                                        Math.sqrt(Math.max(1, object.input().indices().length / 3)))
                        .sum();
        long mergeNanos = 0;
        for (var object : inputs) {
            var input = object.input();
            token.check();
            long now = System.nanoTime();
            double weight = Math.sqrt(Math.max(1, input.indices().length / 3));
            long objectDeadline =
                    Math.min(
                            deadline,
                            now
                                    + Math.max(
                                            50_000_000L,
                                            (long)
                                                    (Math.max(0, deadline - now)
                                                            * weight
                                                            / pendingWeight)));
            pendingWeight -= weight;
            int source = input.indices().length / 3;
            sourceTriangles += source;
            if (source == 0) continue;
            if (System.nanoTime() >= deadline) {
                skippedTriangles += source;
                incompleteObjects++;
                if (issues.size() < 32)
                    issues.add(object.name() + ": generation time budget exhausted");
                continue;
            }
            ModelCollisionSolids.Extracted extracted;
            try {
                extracted =
                        ModelCollisionSolids.extractVolume(
                                input, settings, token, objectDeadline, true);
            } catch (IOException | IllegalArgumentException failure) {
                token.check();
                if (issues.size() < 32) issues.add(object.name() + ": " + failure.getMessage());
                skippedTriangles += source;
                incompleteObjects++;
                continue;
            }
            for (String issue : extracted.issues())
                if (issues.size() < 32) issues.add(object.name() + ": " + issue);
            int skipped = extracted.remaining().length / 3;
            if (parts.size() + extracted.solids().size() > MAX_PARTS) {
                skippedTriangles += source;
                incompleteObjects++;
                continue;
            }
            skippedTriangles += skipped;
            if (skipped != 0) incompleteObjects++;
            long merging = System.nanoTime();
            // Never merge across object boundaries, even when their surfaces touch.
            try {
                parts.addAll(
                        ModelCollisionPrismMerge.merge(
                                extracted.solids(),
                                settings.mergeScale(),
                                settings.fitError() * .5,
                                token,
                                Math.min(deadline, merging + 20_000_000L)));
            } catch (IOException | IllegalArgumentException failure) {
                token.check();
                parts.addAll(extracted.solids());
                if (issues.size() < 32)
                    issues.add(object.name() + ": optional merge skipped: " + failure.getMessage());
            }
            mergeNanos += System.nanoTime() - merging;
        }
        if (parts.isEmpty())
            throw new IOException(
                    "model contains no generated collision volumes: "
                            + (issues.isEmpty() ? "empty geometry" : issues.getFirst()));
        token.check();
        return new Built(
                new ModelCollisionMesh(
                        parts, sourceTriangles, skippedTriangles, incompleteObjects, issues),
                baked - started,
                System.nanoTime() - baked - mergeNanos,
                mergeNanos);
    }

    private record Instance(GltfAsset.Mesh mesh, Matrix4d transform, String name) {}

    private record ObjectInput(String name, ModelCollisionSolids.Input input) {}

    private record Visit(int node, Matrix4d parent) {}

    private static List<ObjectInput> bake(
            GltfAsset asset,
            int sceneIndex,
            Matrix4fc transform,
            ModelCancellation token,
            long deadline)
            throws IOException {
        if (sceneIndex < 0 || sceneIndex >= asset.scenes().size())
            throw new IOException("collision scene index out of range");
        List<Instance> instances = new ArrayList<>();
        ArrayDeque<Visit> pending = new ArrayDeque<>();
        for (int root : asset.scenes().get(sceneIndex).roots())
            pending.push(new Visit(root, new Matrix4d(transform)));
        Set<Integer> visited = new HashSet<>();
        long vertices = 0, triangles = 0;
        while (!pending.isEmpty()) {
            check(token, deadline);
            Visit visit = pending.pop();
            if (visit.node < 0 || visit.node >= asset.nodes().size() || !visited.add(visit.node))
                throw new IOException("invalid collision scene hierarchy");
            var node = asset.nodes().get(visit.node);
            Matrix4d matrix = new Matrix4d(visit.parent).mul(new Matrix4d(node.local()));
            for (int child : node.children()) pending.push(new Visit(child, matrix));
            if (node.mesh() < 0) continue;
            if (node.mesh() >= asset.meshes().size())
                throw new IOException("invalid collision mesh index");
            var mesh = asset.meshes().get(node.mesh());
            instances.add(new Instance(mesh, matrix, "node=" + visit.node + " " + node.name()));
            for (var primitive : mesh.primitives()) {
                vertices += primitive.vertexCount();
                triangles += primitive.indexCount() / 3;
            }
            if (triangles > MAX_TRIANGLES || vertices > MAX_TRIANGLES * 3L)
                throw new IOException("collision source triangle/vertex budget exceeded");
        }
        List<ObjectInput> inputs = new ArrayList<>();
        Vector3d point = new Vector3d();
        for (Instance instance : instances) {
            int objectVertices = 0, objectIndices = 0;
            for (var primitive : instance.mesh.primitives()) {
                objectVertices += primitive.vertexCount();
                objectIndices += primitive.indexCount();
            }
            double[] positions = new double[objectVertices * 3];
            int[] indices = new int[objectIndices];
            int vertexOffset = 0, indexOffset = 0;
            boolean mirrored = instance.transform.determinant3x3() < 0;
            for (var primitive : instance.mesh.primitives()) {
                float[] source = primitive.positions();
                for (int v = 0; v < source.length; v += 3) {
                    if ((v & 4095) == 0) check(token, deadline);
                    instance.transform.transformPosition(
                            point.set(source[v], source[v + 1], source[v + 2]));
                    if (!point.isFinite() || point.lengthSquared() > 1e16)
                        throw new IOException("collision geometry exceeds coordinate budget");
                    int target = vertexOffset * 3 + v;
                    positions[target] = point.x;
                    positions[target + 1] = point.y;
                    positions[target + 2] = point.z;
                }
                int[] sourceIndices = primitive.indices();
                for (int t = 0; t < sourceIndices.length; t += 3) {
                    if ((t & 4095) == 0) check(token, deadline);
                    indices[indexOffset++] = sourceIndices[t] + vertexOffset;
                    indices[indexOffset++] = sourceIndices[t + (mirrored ? 2 : 1)] + vertexOffset;
                    indices[indexOffset++] = sourceIndices[t + (mirrored ? 1 : 2)] + vertexOffset;
                }
                vertexOffset += primitive.vertexCount();
            }
            inputs.add(
                    new ObjectInput(
                            instance.name, new ModelCollisionSolids.Input(positions, indices)));
        }
        return inputs;
    }

    private static void check(ModelCancellation token, long deadline) throws IOException {
        token.check();
        if (System.nanoTime() > deadline)
            throw new IOException(
                    "collision volume generation exceeded time budget; no partial collider"
                            + " published");
    }
}
