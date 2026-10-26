package com.mine.geometry_node.client.runtime.model.render.bvh;

import com.mine.geometry_node.client.runtime.model.render.geometry.ModelRenderAsset;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import org.joml.Vector3f;
import java.io.IOException;
import java.util.*;

/** Worker-prepared, asset-local alpha-aware occlusion data, shared by instances of the selected scene. */
public record ModelLightOcclusionScene(float[] texels, int nodeCount, int triangleOffset,
                                      int triangleCount, Map<String, Object> report) {
    static final int MAX_TRIANGLES = 2_000_000;
    static final long MAX_BYTES = 128L * 1024 * 1024;
    // Worker-only cold builds need headroom for million-triangle assets; cancellation stays frequent.
    static final long MAX_BUILD_NANOS = 10_000_000_000L;
    private static final float[] NO_TEXELS = new float[0];
    public static final ModelLightOcclusionScene CLOSED = absent("CLOSED", "asset_closed_or_scene_unavailable", 0, 0, 0, 0);

    public ModelLightOcclusionScene { Objects.requireNonNull(texels); report = Map.copyOf(report); }
    public long cpuBytes() { return (long) texels.length * Float.BYTES; }
    public boolean ready() { return triangleCount > 0 && texels.length > 0; }

    public record AlphaTexture(ModelOcclusionAlpha.Texture image, GltfAsset.Sampler sampler) {}
    public interface Textures { AlphaTexture get(int texture); }
    private static final Textures NO_TEXTURES = index -> null;
    static List<ModelLightOcclusionScene> prepare(List<List<ModelRenderAsset.Occurrence>> scenes,
                                                ModelCancellation token, long remainingCpuBytes) throws IOException {
        return prepare(scenes, token, remainingCpuBytes, NO_TEXTURES);
    }
    public static List<ModelLightOcclusionScene> prepare(List<List<ModelRenderAsset.Occurrence>> scenes,
            ModelCancellation token, long remainingCpuBytes, Textures textures) throws IOException {
        return prepare(scenes, token, remainingCpuBytes, MAX_TRIANGLES, MAX_BYTES, MAX_BUILD_NANOS, textures);
    }

    /** Limits cover the complete asset, not a fresh allowance for each scene. */
    static List<ModelLightOcclusionScene> prepare(List<List<ModelRenderAsset.Occurrence>> scenes,
            ModelCancellation token, long remainingCpuBytes, int maxTriangles, long maxBytes, long maxNanos) throws IOException {
        return prepare(scenes,token,remainingCpuBytes,maxTriangles,maxBytes,maxNanos,NO_TEXTURES);
    }
    private static List<ModelLightOcclusionScene> prepare(List<List<ModelRenderAsset.Occurrence>> scenes,
            ModelCancellation token, long remainingCpuBytes, int maxTriangles, long maxBytes, long maxNanos,
            Textures textures) throws IOException {
        if (remainingCpuBytes < 0 || maxTriangles < 0 || maxBytes < 0 || maxNanos <= 0)
            throw new IllegalArgumentException("invalid occlusion preparation budget");
        long started = System.nanoTime(), retained = 0;
        int accepted = 0;
        List<ModelLightOcclusionScene> result = new ArrayList<>();
        for (var scene : scenes) {
            token.check(); long opaque = 0, mask = 0, blend = 0;
            for (var occurrence : scene) for (var primitive : occurrence.primitives()) {
                token.check(); long count = primitive.indices.length / 3;
                switch (primitive.material.alphaMode()) {
                    case OPAQUE -> opaque += count;
                    case MASK -> mask += count;
                    case BLEND -> blend += count;
                }
            }
            long sceneStart = System.nanoTime();
            if (opaque + mask + blend == 0) {
                result.add(absent("EMPTY", "", opaque, mask, blend, 0));
                continue;
            }
            try {
                if (opaque > maxTriangles - accepted) throw new ModelLightOcclusionBvh.BudgetExceeded("asset_triangle_budget");
                final long[] alphaCount = {0}; final int available = maxTriangles - accepted - (int)opaque;
                if(mask + blend > 0) visitAlpha(scene, textures, token, started, maxNanos, (a,b,c,alpha) -> {
                    if(++alphaCount[0] > available) throw new ModelLightOcclusionBvh.BudgetExceeded("alpha_tessellation_triangle_budget");
                    if((alphaCount[0]&1023)==0) remainingNanos(started,maxNanos,token);
                });
                int total = Math.toIntExact(opaque + alphaCount[0]);
                if(total==0) { result.add(absent("EMPTY_ALPHA_COVERAGE","",opaque,mask,blend,System.nanoTime()-sceneStart)); continue; }
                boolean hasAlpha = alphaCount[0] > 0;
                long availableBytes = Math.min(maxBytes, remainingCpuBytes) - retained;
                long fixedBytes = total * 84L + (hasAlpha ? total * 4L : 0);
                long nodeBytes = 36L + ModelLightOcclusionBvh.NODE_TEXELS * 16L;
                // Reserve CPU capacity plus its possible export, without fixing the SAH topology.
                // During growth the export is not allocated yet; that headroom also covers copies.
                if (availableBytes < fixedBytes + nodeBytes)
                    throw new ModelLightOcclusionBvh.BudgetExceeded("asset_occlusion_memory_budget");
                int nodeCapacity = Math.toIntExact(Math.min(Math.min(2L * total - 1, Integer.MAX_VALUE / 6L),
                        (availableBytes - fixedBytes) / nodeBytes));
                remainingNanos(started, maxNanos, token);
                float[] soup = new float[Math.multiplyExact(total, 9)];
                Vector3f point = new Vector3f(); int offset = 0;
                for (var occurrence : scene) for (var primitive : occurrence.primitives()) {
                    if (primitive.material.alphaMode() != GltfAsset.AlphaMode.OPAQUE) continue;
                    for (int index : primitive.indices) {
                        if ((offset & 4095) == 0) remainingNanos(started, maxNanos, token);
                        int p = index * 3;
                        occurrence.transform().transformPosition(primitive.positions[p], primitive.positions[p + 1], primitive.positions[p + 2], point);
                        if (!point.isFinite()) throw new ModelLightOcclusionBvh.BudgetExceeded("nonfinite_transformed_geometry");
                        soup[offset++] = point.x; soup[offset++] = point.y; soup[offset++] = point.z;
                    }
                }
                float[] alpha = hasAlpha ? new float[total] : null;
                if(alpha != null) {
                    Arrays.fill(alpha,0,(int)opaque,1);
                    final int[] cursor = {offset};
                    visitAlpha(scene,textures,token,started,maxNanos,(a,b,c,opacity) -> {
                        int t=cursor[0]/9; alpha[t]=opacity;
                        for(var v: new ModelOcclusionAlpha.Vertex[]{a,b,c}) {
                            soup[cursor[0]++]=v.x();soup[cursor[0]++]=v.y();soup[cursor[0]++]=v.z();
                        }
                        if((t&1023)==0) remainingNanos(started,maxNanos,token);
                    });
                }
                var bvh = ModelLightOcclusionBvh.buildOwned(soup, alpha, new ModelLightOcclusionBvh.Budget(
                        total, availableBytes, remainingNanos(started, maxNanos, token)), token, nodeCapacity);
                long exportBytes = bvh.gpuBytes();
                long peak = retained + Math.max(bvh.peakCpuBytes(), bvh.cpuBytes() + exportBytes);
                if (peak > Math.min(maxBytes, remainingCpuBytes))
                    throw new ModelLightOcclusionBvh.BudgetExceeded("asset_occlusion_memory_budget");
                float[] texels = bvh.gpuTexels(availableBytes - bvh.cpuBytes(), token, remainingNanos(started, maxNanos, token));
                remainingNanos(started, maxNanos, token);
                long nanos = System.nanoTime() - sceneStart;
                var report = description(mask + blend == 0 ? "OPAQUE_READY" : "ALPHA_READY_APPROXIMATE", "", opaque, mask, blend, nanos);
                report.put("nodeCount", bvh.nodeCount()); report.put("generatedAlphaTriangles", alphaCount[0]);
                report.put("bvhMaxDepth", bvh.maxDepth());
                report.put("bvhNodeCapacity", bvh.nodeCapacity());
                report.put("bvhSahSplits", bvh.sahSplitCount());
                report.put("bvhMedianSplits", bvh.medianSplitCount());
                report.put("alphaSubdivisionLimit",ModelOcclusionAlpha.MAX_SUBDIVISIONS); report.put("cpuBytes", exportBytes);
                report.put("gpuBytesEstimated", exportBytes); report.put("peakBuildCpuBytesEstimated", peak);
                result.add(new ModelLightOcclusionScene(texels, bvh.nodeCount(), bvh.triangleTexelOffset(), total, report));
                retained += exportBytes; accepted += total;
            } catch (ModelLightOcclusionBvh.BudgetExceeded exceeded) {
                token.check();
                result.add(absent("DEGRADED", exceeded.getMessage(), opaque, mask, blend, System.nanoTime() - sceneStart));
            }
        }
        token.check(); return List.copyOf(result);
    }

    private static void visitAlpha(List<ModelRenderAsset.Occurrence> scene, Textures textures, ModelCancellation token,
            long started,long maxNanos,ModelOcclusionAlpha.Consumer out) throws IOException {
        Vector3f point=new Vector3f();
        for(var occurrence:scene) for(var p:occurrence.primitives()) {
            if(p.material.alphaMode()==GltfAsset.AlphaMode.OPAQUE)continue;
            var info=p.material.baseColorTexture();
            AlphaTexture texture=info==null?null:textures.get(info.textureIndex());
            if(info!=null&&texture==null)throw new ModelLightOcclusionBvh.BudgetExceeded("alpha_texture_unavailable");
            var sampler=texture==null?new GltfAsset.Sampler(9728,9728,33071,33071):texture.sampler();
            for(int i=0;i<p.indices.length;i+=3) {
                remainingNanos(started,maxNanos,token);
                ModelOcclusionAlpha.Vertex[] v=new ModelOcclusionAlpha.Vertex[3];
                for(int corner=0;corner<3;corner++) {
                    int index=p.indices[i+corner],pos=index*3;
                    occurrence.transform().transformPosition(p.positions[pos],p.positions[pos+1],p.positions[pos+2],point);
                    if(!point.isFinite())throw new ModelLightOcclusionBvh.BudgetExceeded("nonfinite_transformed_geometry");
                    v[corner]=new ModelOcclusionAlpha.Vertex(point.x,point.y,point.z,p.baseUv[index*2],p.baseUv[index*2+1],(p.colors[index]>>>24)/255f);
                }
                ModelOcclusionAlpha.emit(v[0],v[1],v[2],p.material.alphaMode(),p.baseColorFactor[3],p.material.alphaCutoff(),
                        texture==null?null:texture.image(),sampler,out);
            }
        }
    }

    private static long remainingNanos(long start, long max, ModelCancellation token) throws IOException {
        token.check(); long remaining = max - (System.nanoTime() - start);
        if (remaining <= 0) throw new ModelLightOcclusionBvh.BudgetExceeded("asset_worker_time_budget");
        return remaining;
    }
    private static ModelLightOcclusionScene absent(String status, String reason, long opaque, long mask, long blend, long nanos) {
        return new ModelLightOcclusionScene(NO_TEXELS, 0, 0, 0, description(status, reason, opaque, mask, blend, nanos));
    }
    private static Map<String, Object> description(String status, String reason, long opaque, long mask, long blend, long nanos) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", status); report.put("reason", reason); report.put("opaqueTriangles", opaque);
        report.put("maskSourceTriangles", mask); report.put("blendSourceTriangles", blend);
        report.put("buildNanos", nanos); report.put("cpuBytes", 0L); report.put("gpuBytesEstimated", 0L);
        report.put("bvhBuildSplit", "binned_sah_16_with_median_fallback");
        report.put("bvhLeafTriangles", 8);
        report.put("bvhMaxDepth", 0);
        report.put("bvhStackSlots", ModelLightOcclusionBvh.MAX_TRAVERSAL_STACK);
        report.put("bvhNodeBytes", ModelLightOcclusionBvh.NODE_TEXELS * 16);
        report.put("triangleDataLayout", "v0_v1_v2");
        report.put("scope", "two-sided OPAQUE; MASK alpha-tested worker microtriangles; BLEND scalar transmission, no refraction/tint; base-level alpha approximation capped at 64 subdivisions per source edge; no visual verification implied");
        return report;
    }
}
