package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/** Immutable worker-built asset-local transparent geometry. Node boundaries do not split the BSP. */
final class ModelTransparentScene {
    static final ModelTransparentScene EMPTY = new ModelTransparentScene(null, List.of(), new int[0], new int[0],
            0, 0, 0, 0, "EMPTY");
    private static final int MAX_INPUT_TRIANGLES = 8192, MAX_FRAGMENTS = 65536;
    private static final long MAX_WORK = 2_000_000, MAX_BUILD_NANOS = 1_000_000_000L;
    private final ModelTransparentBsp.Result bsp;
    private final List<ModelRenderAsset.Primitive> primitives;
    private final int[] triangleOwners, triangleVertices;
    private final long cpuBytes, gpuEstimate, buildNanos;
    private final int inputTriangles;
    private final String status;

    private ModelTransparentScene(ModelTransparentBsp.Result bsp, List<ModelRenderAsset.Primitive> primitives,
                                  int[] owners, int[] vertices, long cpuBytes, long gpuEstimate,
                                  long buildNanos, int inputTriangles, String status) {
        this.bsp = bsp; this.primitives = List.copyOf(primitives); triangleOwners = owners; triangleVertices = vertices;
        this.cpuBytes = cpuBytes; this.gpuEstimate = gpuEstimate; this.buildNanos = buildNanos;
        this.inputTriangles = inputTriangles; this.status = status;
    }

    static ModelTransparentScene prepare(List<ModelRenderAsset.Occurrence> occurrences, ModelCancellation token,
                                         long availableCpuBytes) throws IOException {
        long start = System.nanoTime(); int inputs = 0;
        for (var occurrence : occurrences) for (var primitive : occurrence.primitives()) {
            token.check();
            if (primitive.material.alphaMode() == GltfAsset.AlphaMode.BLEND) {
                inputs = Math.addExact(inputs, primitive.indices.length / 3);
                if (inputs > MAX_INPUT_TRIANGLES) return fallback(start, inputs, "input triangle limit");
            }
        }
        if (inputs == 0) return EMPTY;
        // Both retained BSP attributes and resident-geometry CPU arrays are accounted for.
        // Reserve conservatively before allocating split polygons, not only after building them.
        int fragments = (int) Math.min(Math.min(MAX_FRAGMENTS, inputs * 8L), Math.max(0, availableCpuBytes / 1024));
        if (fragments < inputs) return fallback(start, inputs, "CPU preparation budget");
        Runnable check = () -> {
            try { token.check(); } catch (IOException e) { throw new UncheckedIOException(e); }
            if (System.nanoTime() - start > MAX_BUILD_NANOS) throw new PreparationDeadline();
        };
        try {
            List<ModelTransparentBsp.Triangle> triangles = new ArrayList<>(inputs);
            List<GltfAsset.Material> materials = new ArrayList<>();
            GltfAsset.Bounds bounds = GltfAsset.Bounds.empty();
            for (var occurrence : occurrences) {
                var transform = occurrence.transform();
                var normalMatrix = new Matrix3f(transform).invert().transpose();
                boolean mirrored = transform.determinant3x3() < 0;
                for (var primitive : occurrence.primitives()) {
                    if (primitive.material.alphaMode() != GltfAsset.AlphaMode.BLEND) continue;
                    int owner = materials.size(); materials.add(primitive.material);
                    for (int i = 0; i < primitive.indices.length; i += 3) {
                        if ((i & 255) == 0) check.run();
                        var a = vertex(primitive, primitive.indices[i], transform, normalMatrix);
                        var b = vertex(primitive, primitive.indices[i + (mirrored ? 2 : 1)], transform, normalMatrix);
                        var c = vertex(primitive, primitive.indices[i + (mirrored ? 1 : 2)], transform, normalMatrix);
                        triangles.add(new ModelTransparentBsp.Triangle(a, b, c, owner));
                        for (var v : List.of(a, b, c)) bounds = bounds.include(v.attribute(0), v.attribute(1), v.attribute(2));
                    }
                }
            }
            double extent = Math.max((double) bounds.maxX() - bounds.minX(),
                    Math.max((double) bounds.maxY() - bounds.minY(), (double) bounds.maxZ() - bounds.minZ()));
            var tree = ModelTransparentBsp.build(triangles,
                    new ModelTransparentBsp.Limits(128, fragments, MAX_WORK, Math.max(1e-6, extent * 1e-7)), check);
            List<List<ModelTransparentBsp.Triangle>> byOwner = new ArrayList<>();
            for (int i = 0; i < materials.size(); i++) byOwner.add(new ArrayList<>());
            int[] owners = new int[tree.triangles().size()], vertices = new int[owners.length];
            for (int i = 0; i < owners.length; i++) {
                if ((i & 255) == 0) check.run();
                var triangle = tree.triangles().get(i); int owner = triangle.owner();
                owners[i] = owner; vertices[i] = byOwner.get(owner).size() * 3;
                byOwner.get(owner).add(triangle);
            }
            if (tree.triangles().isEmpty()) return EMPTY;
            List<ModelRenderAsset.Primitive> primitives = new ArrayList<>();
            int[] remappedOwner = new int[materials.size()];
            long cpu = (long) owners.length * 512, gpu = 0;
            for (int owner = 0; owner < materials.size(); owner++) {
                check.run();
                if (byOwner.get(owner).isEmpty()) continue;
                remappedOwner[owner] = primitives.size();
                var primitive = new ModelRenderAsset.Primitive(materials.get(owner), byOwner.get(owner));
                primitives.add(primitive); cpu = Math.addExact(cpu, primitive.cpuBytes());
                gpu = Math.addExact(gpu, primitive.gpuEstimate());
            }
            for (int i = 0; i < owners.length; i++) owners[i] = remappedOwner[owners[i]];
            if (cpu > availableCpuBytes) return fallback(start, inputs, "CPU preparation budget");
            return new ModelTransparentScene(tree, primitives, owners, vertices, cpu, gpu,
                    System.nanoTime() - start, inputs, "BSP_READY");
        } catch (UncheckedIOException e) { throw e.getCause(); }
        catch (ModelTransparentBsp.BudgetExceededException | PreparationDeadline e) {
            return fallback(start, inputs, e instanceof PreparationDeadline ? "worker build deadline" : e.getMessage());
        }
    }

    private static ModelTransparentBsp.Vertex vertex(ModelRenderAsset.Primitive p, int index,
                                                      org.joml.Matrix4f transform, Matrix3f normalMatrix) {
        int at = index * 3, uv = index * 2, color = p.colors[index];
        var position = transform.transformPosition(new Vector3f(p.positions[at], p.positions[at + 1], p.positions[at + 2]));
        var normal = normalMatrix.transform(new Vector3f(p.normals[at], p.normals[at + 1], p.normals[at + 2]));
        // Match the existing packed-normal vertex path before interpolation introduces new vertices.
        if (normal.lengthSquared() > 0) normal.normalize();
        return new ModelTransparentBsp.Vertex(new float[]{position.x, position.y, position.z,
                normal.x, normal.y, normal.z, p.baseUv[uv], p.baseUv[uv + 1], p.emissionUv[uv], p.emissionUv[uv + 1],
                ((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f});
    }
    private static ModelTransparentScene fallback(long start, int inputs, String reason) {
        return new ModelTransparentScene(null, List.of(), new int[0], new int[0], 0, 0,
                System.nanoTime() - start, inputs, "DEGRADED_CENTROID: " + reason);
    }
    private static final class PreparationDeadline extends RuntimeException {}
    ModelTransparentBsp.Result bsp() { return bsp; }
    List<ModelRenderAsset.Primitive> primitives() { return primitives; }
    int[] triangleOwners() { return triangleOwners; }
    int[] triangleVertices() { return triangleVertices; }
    long cpuBytes() { return cpuBytes; }
    long gpuEstimate() { return gpuEstimate; }
    Map<String, Object> report() {
        return Map.of("status", status, "inputTriangles", inputTriangles, "outputTriangles", triangleOwners.length,
                "splits", bsp == null ? 0 : bsp.splitCount(), "work", bsp == null ? 0L : bsp.work(),
                "buildNanos", buildNanos, "cpuBytes", cpuBytes, "gpuBytesEstimated", gpuEstimate,
                "scope", "static intersections within one asset scene and rigid instance; cross-instance and vanilla transparency remain approximate");
    }
}
