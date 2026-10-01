package com.mine.geometry_node.client.model.render;

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
        // Expanded positions/normals/UV/color/index/centers = 156 retained bytes per triangle.
        long bytes = (long) triangles * 156;
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
        output.add(new ModelRenderAsset.Occurrence(new Matrix4f(), List.of(primitive)));
        return new Result(List.of(), primitive.cpuBytes(), primitive.gpuEstimate(), run.size() - 1);
    }

    private static ModelTransparentBsp.Vertex vertex(ModelRenderAsset.Primitive p, int i, Matrix4f transform, Matrix3f normal) {
        var position = transform.transformPosition(new Vector3f(p.positions[i * 3], p.positions[i * 3 + 1], p.positions[i * 3 + 2]));
        var n = normal.transform(new Vector3f(p.normals[i * 3], p.normals[i * 3 + 1], p.normals[i * 3 + 2]));
        if (n.lengthSquared() > 0) n.normalize();
        int color = p.colors[i];
        return new ModelTransparentBsp.Vertex(new float[]{position.x, position.y, position.z, n.x, n.y, n.z,
                p.baseUv[i * 2], p.baseUv[i * 2 + 1], p.emissionUv[i * 2], p.emissionUv[i * 2 + 1],
                ((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f});
    }
    private ModelOpaqueBatches() {}
}
