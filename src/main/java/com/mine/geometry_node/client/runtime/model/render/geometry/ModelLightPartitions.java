package com.mine.geometry_node.client.runtime.model.render.geometry;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import org.joml.Matrix4f;
import java.io.IOException;
import java.util.*;
import static com.mine.geometry_node.client.runtime.model.render.pipeline.ModelTransparentBsp.*;

/** Bounded worker-side light sampling cells; static geometry stays resident in both passes. */
public final class ModelLightPartitions {
    static final int MAX_EXTRA_DRAWS = 16, MAX_PARTS = 8, MAX_INPUT_TRIANGLES = 1024, MAX_OUTPUT_TRIANGLES = 8192;
    static final float TARGET_SPAN = 8;
    private static final long MAX_NANOS = 250_000_000L;
    private static final int MAX_REUSE_ENTRIES = 256;
    private record ReuseKey(ModelRenderAsset.Primitive primitive, float x, float y, float z, int maxParts) {}
    record Result(List<ModelRenderAsset.Occurrence> occurrences, long cpuBytes, long gpuBytes,
                  int partitioned, int extraDraws, Map<String, Integer> degraded) {}
    private static final class Limit extends RuntimeException { Limit(String reason) { super(reason); } }
    private static void check(ModelCancellation cancellation, long start) throws IOException {
        cancellation.check();
        if (System.nanoTime() - start > MAX_NANOS) throw new Limit("worker_time_budget");
    }
    static Result prepare(List<ModelRenderAsset.Occurrence> source, ModelCancellation cancellation, long remainingCpuBytes) throws IOException {
        if (remainingCpuBytes < 0) throw new IllegalArgumentException("negative CPU budget");
        cancellation.check(); long start = System.nanoTime(), cpu = 0, gpu = 0;
        int partitioned = 0, extra = 0; Map<String, Integer> degraded = new TreeMap<>();
        List<ModelRenderAsset.Occurrence> result = new ArrayList<>(source.size());
        Map<ReuseKey, List<ModelRenderAsset.Primitive>> reuse = new HashMap<>();
        for (var occurrence : source) {
            cancellation.check(); List<ModelRenderAsset.Primitive> output = new ArrayList<>(); boolean changed = false;
            float[] scale = axisScale(occurrence.transform());
            for (var primitive : occurrence.primitives()) {
                cancellation.check();
                if (primitive.material.unlit() || primitive.material.alphaMode() == GltfAsset.AlphaMode.BLEND
                        || longestSpan(primitive.bounds, scale) <= TARGET_SPAN) { output.add(primitive); continue; }
                try {
                    check(cancellation, start);
                    long triangleBytes = primitive.material.normalTexture()==null ? 192L : 240L;
                    if (extra >= MAX_EXTRA_DRAWS) throw new Limit("scene_draw_budget");
                    if (primitive.indices.length / 3 > MAX_INPUT_TRIANGLES) throw new Limit("input_triangle_budget");
                    int maxParts = Math.min(MAX_PARTS, MAX_EXTRA_DRAWS - extra + 1);
                    ReuseKey key = new ReuseKey(primitive, scale[0], scale[1], scale[2], maxParts);
                    var cached = reuse.get(key);
                    if (cached != null) {
                        output.addAll(cached);
                        extra += cached.size() - 1; partitioned++; changed = true;
                        if (cached.stream().anyMatch(p -> longestSpan(p.bounds, scale) > TARGET_SPAN))
                            degraded.merge("coarse_cells_at_partition_limit", 1, Integer::sum);
                        continue;
                    }
                    List<List<Triangle>> parts = new ArrayList<>(); parts.add(triangles(primitive, cancellation));
                    while (parts.size() < maxParts) {
                        check(cancellation, start);
                        int selected = -1, axis = 0; float longest = TARGET_SPAN, split = 0;
                        for (int i = 0; i < parts.size(); i++) {
                            var bounds = bounds(parts.get(i)); float[] lo = {bounds.minX(), bounds.minY(), bounds.minZ()};
                            float[] hi = {bounds.maxX(), bounds.maxY(), bounds.maxZ()};
                            for (int a = 0; a < 3; a++) if ((hi[a] - lo[a]) * scale[a] > longest) {
                                selected = i; axis = a; longest = (hi[a] - lo[a]) * scale[a]; split = lo[a] + (hi[a] - lo[a]) * .5f;
                            }
                        }
                        if (selected < 0) break;
                        List<Triangle> low = new ArrayList<>(), high = new ArrayList<>();
                        for (var triangle : parts.get(selected)) {
                            check(cancellation, start); split(triangle, axis, split, low, high);
                            if (low.size() + high.size() > MAX_OUTPUT_TRIANGLES) throw new Limit("output_triangle_budget");
                        }
                        if (low.isEmpty() || high.isEmpty()) break;
                        parts.set(selected, low); parts.add(selected + 1, high);
                        long count = parts.stream().mapToLong(List::size).sum();
                        if (count > MAX_OUTPUT_TRIANGLES) throw new Limit("output_triangle_budget");
                        if (cpuBytes(parts, triangleBytes) > remainingCpuBytes - cpu) throw new Limit("cpu_budget");
                    }
                    if (parts.size() < 2) { output.add(primitive); continue; }
                    long bytes = cpuBytes(parts, triangleBytes);
                    if (bytes > remainingCpuBytes - cpu) throw new Limit("cpu_budget");
                    List<ModelRenderAsset.Primitive> prepared = new ArrayList<>(); long newCpu = 0, newGpu = 0;
                    for (var part : parts) { check(cancellation, start); var p = new ModelRenderAsset.Primitive(primitive.material, part); prepared.add(p); newCpu += p.cpuBytes(); newGpu += p.gpuEstimate(); }
                    check(cancellation, start);
                    if (newCpu > remainingCpuBytes - cpu) {
                        output.add(primitive);
                        degraded.merge("cpu_budget", 1, Integer::sum);
                        continue;
                    }
                    if (prepared.stream().anyMatch(p -> longestSpan(p.bounds, scale) > TARGET_SPAN))
                        degraded.merge("coarse_cells_at_partition_limit", 1, Integer::sum);
                    output.addAll(prepared); cpu += newCpu; gpu += newGpu; extra += parts.size() - 1; partitioned++; changed = true;
                    if (reuse.size() < MAX_REUSE_ENTRIES) reuse.put(key, List.copyOf(prepared));
                } catch (Limit limit) { output.add(primitive); degraded.merge(limit.getMessage(), 1, Integer::sum); }
            }
            result.add(changed ? new ModelRenderAsset.Occurrence(occurrence.transform(), List.copyOf(output), occurrence.nodeIndex()) : occurrence);
        }
        cancellation.check();
        return new Result(List.copyOf(result), cpu, gpu, partitioned, extra, Map.copyOf(degraded));
    }
    private static long cpuBytes(List<List<Triangle>> parts, long triangleBytes) {
        long bytes = 0;
        int clusterSize = ModelClusterAsset.DEFAULT_TRIANGLES_PER_CLUSTER;
        for (var part : parts) {
            long clusters = (part.size() + clusterSize - 1L) / clusterSize;
            // Each generated primitive retains its own cluster metadata, as counted by Primitive.cpuBytes().
            bytes += part.size() * triangleBytes + clusters * 96L;
        }
        return bytes;
    }
    private static float[] axisScale(Matrix4f m) {
        return new float[]{(float)Math.sqrt(m.m00()*m.m00()+m.m01()*m.m01()+m.m02()*m.m02()),
                (float)Math.sqrt(m.m10()*m.m10()+m.m11()*m.m11()+m.m12()*m.m12()),
                (float)Math.sqrt(m.m20()*m.m20()+m.m21()*m.m21()+m.m22()*m.m22())};
    }
    private static float longestSpan(GltfAsset.Bounds b, float[] s) {
        return Math.max((b.maxX()-b.minX())*s[0], Math.max((b.maxY()-b.minY())*s[1],(b.maxZ()-b.minZ())*s[2]));
    }
    private static List<Triangle> triangles(ModelRenderAsset.Primitive p, ModelCancellation cancellation) throws IOException {
        List<Triangle> result = new ArrayList<>();
        for (int i=0; i<p.indices.length; i+=3) { cancellation.check(); result.add(new Triangle(vertex(p,p.indices[i]),vertex(p,p.indices[i+1]),vertex(p,p.indices[i+2]),0)); }
        return result;
    }
    public static Vertex vertex(ModelRenderAsset.Primitive p, int i) {
        int c=p.colors[i], v=i*3, uv=i*2;
        return new Vertex(new float[]{p.positions[v],p.positions[v+1],p.positions[v+2],p.normals[v],p.normals[v+1],p.normals[v+2],
                p.baseUv[uv],p.baseUv[uv+1],p.emissionUv[uv],p.emissionUv[uv+1],
                ((c>>>16)&255)/255f,((c>>>8)&255)/255f,(c&255)/255f,((c>>>24)&255)/255f, p.uv0[i*2], p.uv0[i*2+1], p.uv1[i*2], p.uv1[i*2+1], tangent(p,i,0), tangent(p,i,1), tangent(p,i,2), tangent(p,i,3)});
    }
    private static float tangent(ModelRenderAsset.Primitive p, int vertex, int component) {
        return p.tangents.length==0 ? 0 : p.tangents[vertex*4+component];
    }
    private static GltfAsset.Bounds bounds(List<Triangle> triangles) {
        var b=GltfAsset.Bounds.empty();
        for (var t:triangles) for (var v:List.of(t.a(),t.b(),t.c())) b=b.include(v.attribute(0),v.attribute(1),v.attribute(2));
        return b;
    }
    private static void split(Triangle t, int axis, float plane, List<Triangle> low, List<Triangle> high) {
        var vertices=List.of(t.a(),t.b(),t.c());
        boolean negative=false,positive=false;
        for (var v:vertices) { negative|=v.attribute(axis)<plane; positive|=v.attribute(axis)>plane; }
        if (!positive) { low.add(t); return; }
        if (!negative) { high.add(t); return; }
        List<Vertex> a=new ArrayList<>(4),b=new ArrayList<>(4);
        for (int i=0;i<3;i++) {
            var v=vertices.get(i); var w=vertices.get((i+1)%3);
            double d=(double)v.attribute(axis)-plane, e=(double)w.attribute(axis)-plane;
            if (d<=0) a.add(v); if (d>=0) b.add(v);
            if ((d<0 && e>0)||(d>0 && e<0)) {
                double fraction=d/(d-e); float[] attrs=new float[v.attributeCount()];
                for (int k=0;k<attrs.length;k++) attrs[k]=(float)((1-fraction)*v.attribute(k)+fraction*w.attribute(k));
                attrs[axis]=plane; var intersection=new Vertex(attrs); a.add(intersection); b.add(intersection);
            }
        }
        triangulate(a,low); triangulate(b,high);
    }
    private static void triangulate(List<Vertex> vertices, List<Triangle> out) {
        for (int i=1;i+1<vertices.size();i++) out.add(new Triangle(vertices.getFirst(),vertices.get(i),vertices.get(i+1),0));
    }
    private ModelLightPartitions() {}
}
