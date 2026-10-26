package com.mine.geometry_node.client.runtime.model.render.geometry;

import com.mine.geometry_node.client.runtime.model.render.pipeline.ModelIrisTangents;
import com.mine.geometry_node.client.runtime.model.render.pipeline.ModelTransparentBsp;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import org.joml.*;
import java.io.IOException;
import java.util.*;

/** Worker-only batching of consecutive identical unlit opaque materials. Lit sampling and BLEND order stay independent. */
final class ModelOpaqueBatches {
    private static final int MAX_TRIANGLES = 4096;
    record Result(List<ModelRenderAsset.Occurrence> occurrences, long cpuBytes, long gpuBytes, int savedDraws) {}
    private record Part(Matrix4f transform, ModelRenderAsset.Primitive primitive) {}

    static Result prepare(List<ModelRenderAsset.Occurrence> source, ModelCancellation token, long availableBytes) throws IOException {
        token.check();
        if (availableBytes < 0) throw new IllegalArgumentException("negative opaque batch CPU budget");
        if (source.stream().noneMatch(occurrence -> occurrence.nodeIndex() >= 0))
            return prepareRuns(source, token, availableBytes);
        // Batch in mesh-local coordinates once; repeated nodes keep shared geometry and their own transforms.
        Map<List<ModelRenderAsset.Primitive>, Result> cache = new HashMap<>();
        List<ModelRenderAsset.Occurrence> output = new ArrayList<>();
        long cpu = 0, gpu = 0;
        int saved = 0;
        for (var occurrence : source) {
            token.check();
            var key = occurrence.primitives();
            Result batched = cache.get(key);
            if (batched == null) {
                batched = prepareRuns(List.of(new ModelRenderAsset.Occurrence(new Matrix4f(), key)), token,
                        java.lang.Math.max(0, availableBytes - cpu));
                cache.put(key, batched);
                cpu += batched.cpuBytes(); gpu += batched.gpuBytes();
            }
            saved += batched.savedDraws();
            for (var local : batched.occurrences())
                output.add(new ModelRenderAsset.Occurrence(new Matrix4f(occurrence.transform()).mul(local.transform()),
                        local.primitives(), occurrence.nodeIndex()));
        }
        return new Result(List.copyOf(output), cpu, gpu, saved);
    }

    private static Result prepareRuns(List<ModelRenderAsset.Occurrence> source, ModelCancellation token, long availableBytes) throws IOException {
        token.check();
        if (availableBytes < 0) throw new IllegalArgumentException("negative opaque batch CPU budget");
        List<ModelRenderAsset.Occurrence> output = new ArrayList<>();
        List<Part> run = new ArrayList<>();
        long cpu = 0, gpu = 0; int saved = 0, triangles = 0;
        for (var occurrence : source) for (var primitive : occurrence.primitives()) {
            token.check();
            boolean eligible = primitive.material.unlit() && primitive.material.alphaMode() == GltfAsset.AlphaMode.OPAQUE;
            int count = primitive.indices.length / 3;
            if (!run.isEmpty() && (!eligible || run.getFirst().primitive.material != primitive.material || triangles + count > MAX_TRIANGLES)) {
                Result flushed = flush(run, output, token, availableBytes - cpu);
                cpu += flushed.cpuBytes; gpu += flushed.gpuBytes; saved += flushed.savedDraws; run.clear(); triangles = 0;
            }
            if (eligible && count <= MAX_TRIANGLES) { run.add(new Part(occurrence.transform(), primitive)); triangles += count; }
            else output.add(new ModelRenderAsset.Occurrence(occurrence.transform(), List.of(primitive)));
        }
        Result flushed = flush(run, output, token, availableBytes - cpu);
        token.check();
        if (saved + flushed.savedDraws == 0) return new Result(List.copyOf(source), 0, 0, 0);
        return new Result(List.copyOf(output), cpu + flushed.cpuBytes, gpu + flushed.gpuBytes, saved + flushed.savedDraws);
    }

    private static Result flush(List<Part> run, List<ModelRenderAsset.Occurrence> output, ModelCancellation token, long availableBytes) throws IOException {
        int triangles = run.stream().mapToInt(p -> p.primitive.indices.length / 3).sum();
        // Match Primitive's retained arrays and 96-byte cluster accounting. Normal mapping
        // adds 48 tangent bytes per triangle. Every run shares one material identity.
        long triangleBytes = !run.isEmpty() && run.getFirst().primitive.material.normalTexture()!=null ? 240L : 192L;
        int clusterSize = ModelClusterAsset.DEFAULT_TRIANGLES_PER_CLUSTER;
        long clusters = (triangles + clusterSize - 1L) / clusterSize;
        long bytes = (long) triangles * triangleBytes + clusters * 96L;
        if (run.size() < 2 || bytes > availableBytes) {
            for (Part part : run) output.add(new ModelRenderAsset.Occurrence(part.transform, List.of(part.primitive)));
            return new Result(List.of(), 0, 0, 0);
        }
        List<ModelTransparentBsp.Triangle> baked = new ArrayList<>(triangles);
        for (Part part : run) {
            Matrix3f normal = new Matrix3f(part.transform).invert().transpose();
            boolean mirrored = part.transform.determinant3x3() < 0;
            var p = part.primitive;
            for (int i = 0; i < p.indices.length; i += 3) {
                if ((i & 255) == 0) token.check();
                var a = vertex(p, p.indices[i], part.transform, normal);
                var b = vertex(p, p.indices[i + (mirrored ? 2 : 1)], part.transform, normal);
                var c = vertex(p, p.indices[i + (mirrored ? 1 : 2)], part.transform, normal);
                baked.add(new ModelTransparentBsp.Triangle(a, b, c, 0));
            }
        }
        token.check();
        var primitive = new ModelRenderAsset.Primitive(run.getFirst().primitive.material, baked);
        token.check();
        long actualCpu = primitive.cpuBytes();
        if (actualCpu > availableBytes) {
            // Keep the original transformed runs if the exact retained size does not fit.
            for (Part part : run) output.add(new ModelRenderAsset.Occurrence(part.transform, List.of(part.primitive)));
            return new Result(List.of(), 0, 0, 0);
        }
        output.add(new ModelRenderAsset.Occurrence(new Matrix4f(), List.of(primitive)));
        return new Result(List.of(), actualCpu, primitive.gpuEstimate(), run.size() - 1);
    }

    private static ModelTransparentBsp.Vertex vertex(ModelRenderAsset.Primitive p, int i, Matrix4f transform, Matrix3f normal) {
        var position = transform.transformPosition(new Vector3f(p.positions[i * 3], p.positions[i * 3 + 1], p.positions[i * 3 + 2]));
        var n = normal.transform(new Vector3f(p.normals[i * 3], p.normals[i * 3 + 1], p.normals[i * 3 + 2]));
        if (n.lengthSquared() > 0) n.normalize();
        var tangent = ModelIrisTangents.transformed(p.tangents, i, transform, n);
        int color = p.colors[i];
        return new ModelTransparentBsp.Vertex(new float[]{position.x, position.y, position.z, n.x, n.y, n.z,
                p.baseUv[i * 2], p.baseUv[i * 2 + 1], p.emissionUv[i * 2], p.emissionUv[i * 2 + 1],
                ((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f, p.uv0[i*2], p.uv0[i*2+1], p.uv1[i*2], p.uv1[i*2+1], tangent.x, tangent.y, tangent.z, tangent.w});
    }
    private ModelOpaqueBatches() {}
}
