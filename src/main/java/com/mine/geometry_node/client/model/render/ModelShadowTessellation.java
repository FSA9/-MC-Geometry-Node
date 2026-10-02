package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import org.joml.Matrix4f;
import java.io.IOException;
import java.util.*;
import static com.mine.geometry_node.client.model.render.ModelTransparentBsp.*;

/** Static worker tessellation: nonlinear shaderpack shadow warps need short raster edges. */
final class ModelShadowTessellation {
    static final float MAX_EDGE = 1;
    private static final int MAX_INPUT = 8192, MAX_OUTPUT = 32768;
    private static final long MAX_NANOS = 250_000_000L;
    record Result(List<ModelRenderAsset.Occurrence> occurrences, List<ModelRenderAsset.Occurrence> occlusionOccurrences,
                  long cpuBytes, long gpuBytes,
                  int refined, int outputTriangles, Map<String, Integer> degraded) {}
    private static final class Limit extends RuntimeException { Limit(String reason) { super(reason); } }

    static Result prepare(List<ModelRenderAsset.Occurrence> source, ModelCancellation token, long budget) throws IOException {
        long started = System.nanoTime(), cpu = 0, gpu = 0;
        int refined = 0, triangles = 0;
        Map<String, Integer> degraded = new TreeMap<>();
        List<ModelRenderAsset.Occurrence> result = new ArrayList<>(source.size());
        List<ModelRenderAsset.Occurrence> occlusion = new ArrayList<>(source.size());
        for (var occurrence : source) {
            List<ModelRenderAsset.Primitive> parts = new ArrayList<>();
            for (var p : occurrence.primitives()) {
                token.check();
                // Transparent geometry has a separate BSP budget; do not amplify that workload.
                if (p.material.alphaMode() == GltfAsset.AlphaMode.BLEND) { parts.add(p); continue; }
                try {
                    if (p.indices.length / 3 > MAX_INPUT) throw new Limit("input_triangle_budget");
                    List<Triangle> output = new ArrayList<>();
                    ArrayDeque<Triangle> pending = new ArrayDeque<>();
                    boolean changed = false;
                    for (int i = 0; i < p.indices.length; i += 3) {
                        pending.push(new Triangle(ModelLightPartitions.vertex(p, p.indices[i]),
                                ModelLightPartitions.vertex(p, p.indices[i + 1]), ModelLightPartitions.vertex(p, p.indices[i + 2]), 0));
                        while (!pending.isEmpty()) {
                            token.check();
                            if (System.nanoTime() - started > MAX_NANOS) throw new Limit("worker_time_budget");
                            if (output.size() + pending.size() + triangles > MAX_OUTPUT) throw new Limit("output_triangle_budget");
                            if ((output.size() + pending.size()) * 156L > budget - cpu) throw new Limit("cpu_budget");
                            Triangle t = pending.pop();
                            Vertex a = t.a(), b = t.b(), c = t.c();
                            double ab = lengthSquared(a, b, occurrence.transform());
                            double bc = lengthSquared(b, c, occurrence.transform());
                            double ca = lengthSquared(c, a, occurrence.transform());
                            if (Math.max(ab, Math.max(bc, ca)) <= MAX_EDGE * MAX_EDGE) { output.add(t); continue; }
                            // Cyclic permutation preserves winding while choosing the longest edge.
                            if (bc >= ab && bc >= ca) { a = t.b(); b = t.c(); c = t.a(); }
                            else if (ca >= ab && ca >= bc) { a = t.c(); b = t.a(); c = t.b(); }
                            float[] attrs = new float[14];
                            for (int k = 0; k < attrs.length; k++) attrs[k] = a.attribute(k) * .5f + b.attribute(k) * .5f;
                            Vertex mid = new Vertex(attrs);
                            pending.push(new Triangle(mid, b, c, 0));
                            pending.push(new Triangle(a, mid, c, 0));
                            changed = true;
                        }
                    }
                    if (changed) {
                        var replacement = new ModelRenderAsset.Primitive(p.material, output);
                        parts.add(replacement); cpu += replacement.cpuBytes(); gpu += replacement.gpuEstimate();
                        triangles += output.size(); refined++;
                    } else parts.add(p);
                } catch (Limit limit) {
                    parts.add(p); degraded.merge(limit.getMessage(), 1, Integer::sum);
                }
            }
            result.add(new ModelRenderAsset.Occurrence(occurrence.transform(), List.copyOf(parts)));
            // Opaque ray intersections are unchanged by splitting a planar triangle. Avoid paying
            // the raster refinement cost in every pixel's BVH traversal. Keep MASK refinement:
            // its bounded alpha sampling depends on source triangle size and must not regress.
            List<ModelRenderAsset.Primitive> blockers = new ArrayList<>(parts.size());
            for (int i = 0; i < parts.size(); i++) {
                var original = occurrence.primitives().get(i);
                blockers.add(original.material.alphaMode() == GltfAsset.AlphaMode.OPAQUE ? original : parts.get(i));
            }
            occlusion.add(new ModelRenderAsset.Occurrence(occurrence.transform(), List.copyOf(blockers)));
        }
        return new Result(List.copyOf(result), List.copyOf(occlusion), cpu, gpu, refined, triangles, Map.copyOf(degraded));
    }

    private static double lengthSquared(Vertex a, Vertex b, Matrix4f m) {
        double x = a.attribute(0) - b.attribute(0), y = a.attribute(1) - b.attribute(1), z = a.attribute(2) - b.attribute(2);
        double dx = m.m00()*x + m.m10()*y + m.m20()*z;
        double dy = m.m01()*x + m.m11()*y + m.m21()*z;
        double dz = m.m02()*x + m.m12()*y + m.m22()*z;
        return dx*dx + dy*dy + dz*dz;
    }
    private ModelShadowTessellation() {}
}
