package com.mine.geometry_node.client.model.render.pipeline;

import com.mine.geometry_node.client.model.render.bvh.ModelNativeLight;
import com.mine.geometry_node.client.model.render.diagnostic.ModelRenderMetrics;
import com.mine.geometry_node.client.model.render.geometry.ModelRenderAsset;
import com.mine.geometry_node.client.model.render.geometry.ModelRenderInstance;
import com.mine.geometry_node.client.model.render.resource.ModelGpuBudgetException;
import com.mine.geometry_node.client.model.render.resource.ModelGpuUseFence;
import com.mine.geometry_node.client.model.render.resource.ModelIrisUploadBudget;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.*;

/** Shared resident vertices plus a cached cross-model triangle order; only indices change with the view. */
final class ModelTransparentRenderer {
    record Result(int draws, int triangles) {}
    private record Source(ModelRenderInstance instance, int nodeIndex, ModelRenderAsset asset, ModelRenderAsset.Primitive primitive,
                          ModelRenderAsset.GpuPrimitive mesh, Matrix4f transform, boolean mirrored, float lightFactor, int packedLight) {}
    private record Group(ModelTransparentScene scene, int firstSource, int sourceCount, int triangles, Matrix4f transform,
                         int[] ownerSources) {}
    private static List<Group> previousGroups = List.of();
    private static long bspGroups, centroidGroups;
    private static int visibleBspGroups, visibleCentroidGroups;
    private static List<ModelRenderInstance> reportInstances = List.of();
    private record DepthGroup(Group group, float depth) {}
    private record Run(int source, int firstIndex, int count) {}
    private static List<Source> previous = List.of();
    private static Matrix4f previousView;
    private static List<Run> runs = List.of();
    private static boolean previousResolvedWorld;
    private static final int MAX_SLOTS = 3;
    private static final class IndexSlot {
        final ModelGpuUseFence use = new ModelGpuUseFence();
        GpuBuffer buffer;
        long bytes;
    }
    private static final List<IndexSlot> slots = new ArrayList<>();
    private static IndexSlot currentSlot;
    private static ByteBuffer staging;
    private static long[] keys = new long[0];
    private static int[] owners = new int[0], localTriangles = new int[0], sortOwners = new int[0], sortVertices = new int[0];
    private static long transparentPasses, ringDeferrals, sorts, cacheHits, uploads, uploadedBytes, deferred, sortNanos, peakSortNanos;
    private static int lastInputTriangles, lastBspSplits, lastOutputTriangles;

    static Result draw(Matrix4fc view, Vec3 camera, Frustum frustum, List<ModelRenderInstance> instances, boolean resolvedWorld) {
        List<Source> sources = new ArrayList<>();
        List<Group> groups = new ArrayList<>();
        int triangles = 0;
        visibleBspGroups = visibleCentroidGroups = 0;
        reportInstances = instances;
        for (ModelRenderInstance instance : instances) {
            if (!instance.visible() || instance.asset().isClosed() || !instance.asset().gpuResident()) continue;
            var asset = instance.asset();
            ModelTransparentScene scene = asset.transparentScene(instance.scene());
            Matrix4f instanceTransform = instance.transform().relativeTo(camera.x, camera.y, camera.z);
            int first = sources.size(), instanceTriangles = 0;
            if (scene != null && scene.bsp() != null) {
                if (scene.triangleOwners().length == 0 || scene.primitives().isEmpty()) continue;
                var meshes = new ModelRenderAsset.GpuPrimitive[scene.primitives().size()];
                Set<Integer> pendingObjects = new HashSet<>();
                for (int owner = 0; owner < scene.primitives().size(); owner++) {
                    int nodeIndex = scene.primitiveNodeIndex(owner);
                    if (ModelStaticRenderer.objectUploadDeferred(instance, nodeIndex)) continue;
                    var primitive = scene.primitives().get(owner);
                    ModelStaticRenderer.ClusterCullResult clusterCull = ModelStaticRenderer.cullClusters(primitive, instanceTransform, camera, frustum);
                    ModelStaticRenderer.metrics().recordTransparentClusterCulling(clusterCull.candidates(), clusterCull.visible(), clusterCull.culled());
                    if (clusterCull.allCulled()) continue;
                    asset.touch();
                    meshes[owner] = asset.gpuPrimitive(primitive);
                    if (meshes[owner] == null) pendingObjects.add(nodeIndex);
                }
                int[] ownerSources = new int[meshes.length];
                Arrays.fill(ownerSources, -1);
                for (int owner = 0; owner < meshes.length; owner++) {
                    int nodeIndex = scene.primitiveNodeIndex(owner);
                    if (meshes[owner] == null || pendingObjects.contains(nodeIndex)) continue;
                    var primitive = scene.primitives().get(owner);
                    ownerSources[owner] = sources.size();
                    sources.add(source(instance, nodeIndex, asset, primitive, meshes[owner], instanceTransform, camera));
                    instanceTriangles = Math.addExact(instanceTriangles, primitive.indices.length / 3);
                }
                deferred += pendingObjects.size();
                for (int nodeIndex : pendingObjects) ModelStaticRenderer.recordTransparentObjectDeferred(instance, nodeIndex);
                if (instanceTriangles == 0) continue;
                groups.add(new Group(scene, first, sources.size() - first, instanceTriangles, instanceTransform, ownerSources));
                visibleBspGroups++;
            } else {
                Set<Integer> pendingObjects = new HashSet<>();
                for (var occurrence : asset.occurrences(instance.scene())) {
                    if (ModelStaticRenderer.objectUploadDeferred(instance, occurrence.nodeIndex())) continue;
                    Matrix4f transform = new Matrix4f(instanceTransform).mul(occurrence.transform());
                    for (var primitive : occurrence.primitives()) {
                        if (primitive.material.alphaMode() != GltfAsset.AlphaMode.BLEND) continue;
                        ModelStaticRenderer.ClusterCullResult clusterCull = ModelStaticRenderer.cullClusters(primitive, transform, camera, frustum);
                        ModelStaticRenderer.metrics().recordTransparentClusterCulling(clusterCull.candidates(), clusterCull.visible(), clusterCull.culled());
                        if (clusterCull.allCulled()) continue;
                        asset.touch();
                        var mesh = asset.gpuPrimitive(primitive);
                        if (mesh == null) { pendingObjects.add(occurrence.nodeIndex()); continue; }
                        sources.add(source(instance, occurrence.nodeIndex(), asset, primitive, mesh, transform, camera));
                    }
                }
                if (!pendingObjects.isEmpty()) sources.subList(first, sources.size())
                        .removeIf(source -> pendingObjects.contains(source.nodeIndex()));
                deferred += pendingObjects.size();
                for (int nodeIndex : pendingObjects) ModelStaticRenderer.recordTransparentObjectDeferred(instance, nodeIndex);
                for (int index = first; index < sources.size(); index++)
                    instanceTriangles = Math.addExact(instanceTriangles, sources.get(index).primitive.indices.length / 3);
                if (instanceTriangles > 0) {
                    groups.add(new Group(null, first, sources.size() - first, instanceTriangles, instanceTransform, null));
                    visibleCentroidGroups++;
                }
            }
            triangles = Math.addExact(triangles, instanceTriangles);
            if (triangles > ModelStaticRenderer.budget().maxTransparentTriangles())
                throw new IllegalStateException("transparent triangle budget exceeded");
        }
        lastInputTriangles = groups.stream().mapToInt(group -> group.scene == null ? group.triangles : group.scene.inputTriangles()).sum();
        lastBspSplits = groups.stream().filter(group -> group.scene != null && group.scene.bsp() != null)
                .mapToInt(group -> group.scene.bsp().splitCount()).sum();
        lastOutputTriangles = triangles;
        if (sources.isEmpty()) { clearBuffers(); lastInputTriangles = lastBspSplits = lastOutputTriangles = 0; return new Result(0, 0); }
        boolean viewAffectsOrder = groups.size() != 1 || groups.getFirst().scene == null;
        boolean changed = previousView == null || (viewAffectsOrder && !previousView.equals(view)) || previousResolvedWorld != resolvedWorld || previous.size() != sources.size() || previousGroups.size() != groups.size();
        if (!changed) for (int i = 0; i < groups.size(); i++) {
            Group a = previousGroups.get(i), b = groups.get(i);
            if (a.scene != b.scene || a.firstSource != b.firstSource || a.sourceCount != b.sourceCount || a.triangles != b.triangles
                    || !Arrays.equals(a.ownerSources, b.ownerSources)) { changed = true; break; }
        }
        if (!changed) for (int i = 0; i < sources.size(); i++) {
            Source a = previous.get(i), b = sources.get(i);
            if (a.mesh != b.mesh || !a.transform.equals(b.transform)) { changed = true; break; }
        }
        IndexSlot selected = currentSlot;
        boolean used = false;
        long ticket = 0;
        try {
            if (changed) {
                selected = writableSlot();
                if (selected == null) { recordDeferred(sources); deferred++; ringDeferrals++; return new Result(0, 0); }
                long required = (long) triangles * 12;
                if (!ModelIrisUploadBudget.INSTANCE.tryAcquire(required)) { recordDeferred(sources); deferred++; return new Result(0, 0); }
                long started = System.nanoTime();
                try {
                    ensureCapacity(selected, triangles);
                    // A failed upload must not leave the old source/order cache reusable.
                    previousView = null;
                    ticket = selected.use.submitted(); used = true;
                    sortAndUpload(sources, groups, view, triangles, selected);
                    currentSlot = selected;
                    previous = List.copyOf(sources); previousGroups = List.copyOf(groups); previousView = new Matrix4f(view); previousResolvedWorld = resolvedWorld;
                } finally { ModelIrisUploadBudget.INSTANCE.recordElapsed(System.nanoTime() - started); }
            } else {
                cacheHits++;
                // Immutable repeated reads are allowed, but extend the latest-use fence.
                ticket = selected.use.submitted(); used = true;
            }
            return drawRuns(sources, view, resolvedWorld, selected);
        } finally {
            if (used) {
                IndexSlot submitted = selected; long submittedTicket = ticket;
                RenderSystem.queueFencedTask(() -> submitted.use.completed(submittedTicket));
            }
        }
    }

    private static void recordDeferred(List<Source> sources) {
        for (var source : sources)
            ModelStaticRenderer.recordTransparentObjectDeferred(source.instance(), source.nodeIndex());
    }

    private static Source source(ModelRenderInstance instance, int nodeIndex, ModelRenderAsset asset, ModelRenderAsset.Primitive primitive,
                                 ModelRenderAsset.GpuPrimitive mesh, Matrix4f transform, Vec3 camera) {
        var light=ModelStaticRenderer.sampleLight(primitive,transform,camera);
        return new Source(instance, nodeIndex, asset, primitive, mesh, transform, transform.determinant3x3() < 0, light.factor(), light.packed());
    }

    private static float groupDepth(Group group, List<Source> sources, Matrix4fc view) {
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        Vector3f point = new Vector3f();
        for (int i = group.firstSource; i < group.firstSource + group.sourceCount; i++) {
            Source source = sources.get(i); var bounds = source.primitive.bounds;
            for (int corner = 0; corner < 8; corner++) {
                source.transform.transformPosition((corner & 1) == 0 ? bounds.minX() : bounds.maxX(),
                        (corner & 2) == 0 ? bounds.minY() : bounds.maxY(), (corner & 4) == 0 ? bounds.minZ() : bounds.maxZ(), point);
                minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
                minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z);
            }
        }
        return view.m02() * (minX + maxX) * .5f + view.m12() * (minY + maxY) * .5f
                + view.m22() * (minZ + maxZ) * .5f + view.m32();
    }

    private static IndexSlot writableSlot() {
        for (IndexSlot slot : slots) if (slot.use.writable()) return slot;
        if (slots.size() >= MAX_SLOTS) return null;
        IndexSlot slot = new IndexSlot(); slots.add(slot); return slot;
    }

    private static void ensureCapacity(IndexSlot slot, int count) {
        if (!slot.use.writable()) throw new IllegalStateException("attempted overwrite of in-flight transparent indices");
        long required = (long) count * 12;
        if (staging == null || staging.capacity() < required) {
            // Allocate everything before discarding the previous staging arrays.
            long[] nextKeys = new long[count]; int[] nextOwners = new int[count], nextLocal = new int[count];
            int[] nextSortOwners = new int[count], nextSortVertices = new int[count];
            ByteBuffer nextStaging = MemoryUtil.memAlloc(Math.toIntExact(required));
            if (staging != null) MemoryUtil.memFree(staging);
            staging = nextStaging; keys = nextKeys; owners = nextOwners; localTriangles = nextLocal;
            sortOwners = nextSortOwners; sortVertices = nextSortVertices;
        }
        if (slot.buffer != null && slot.bytes >= required) return;
        if (!ModelRenderAsset.residency().reserveTransientGpu(required))
            throw new ModelGpuBudgetException("sorted transparent index ring budget exhausted");
        GpuBuffer next;
        try {
            next = RenderSystem.getDevice().createBuffer(() -> "GeometryNode sorted transparent index slot",
                    GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, required);
        } catch (RuntimeException | Error failure) {
            ModelRenderAsset.residency().releaseTransientGpu(required); throw failure;
        }
        // This slot is fence-complete; its previous buffer has no outstanding GPU readers/writers.
        if (slot.buffer != null) {
            slot.buffer.close(); ModelRenderAsset.residency().releaseTransientGpu(slot.bytes);
        }
        slot.buffer = next; slot.bytes = required;
    }

    private static void sortAndUpload(List<Source> sources, List<Group> groups, Matrix4fc view, int triangles, IndexSlot slot) {
        long started = System.nanoTime();
        List<DepthGroup> ordered = new ArrayList<>(groups.size());
        boolean hasBsp = groups.stream().anyMatch(group -> group.scene != null);
        if (!hasBsp) {
            // Preserve the pre-BSP global centroid order when every scene uses the fallback.
            ordered.add(new DepthGroup(new Group(null, 0, sources.size(), triangles, new Matrix4f(), null), 0));
        } else {
            for (Group group : groups) ordered.add(new DepthGroup(group, groupDepth(group, sources, view)));
            ordered.sort(Comparator.comparingDouble(DepthGroup::depth));
        }
        int ordinal = 0;
        for (DepthGroup entry : ordered) {
            Group group = entry.group;
            if (group.scene != null) {
                Vector3f localCamera = new Matrix4f(group.transform).invert().transformPosition(0, 0, 0, new Vector3f());
                int[] order = group.scene.bsp().triangleOrder(localCamera.x, localCamera.y, localCamera.z);
                int first = ordinal;
                ordinal = group.scene.appendVisibleOrder(order, group.ownerSources, owners, localTriangles, ordinal);
                if (ordinal - first != group.triangles) throw new IllegalStateException("BSP visible triangle order size mismatch");
                bspGroups++;
            } else {
                int first = ordinal;
                for (int owner = group.firstSource; owner < group.firstSource + group.sourceCount; owner++) {
                    Source source = sources.get(owner);
                    Matrix4f eye = new Matrix4f(view).mul(source.transform);
                    float[] centers = source.primitive.triangleCenters;
                    for (int offset = 0; offset < centers.length; offset += 3) {
                        float depth = eye.m02() * centers[offset] + eye.m12() * centers[offset + 1]
                                + eye.m22() * centers[offset + 2] + eye.m32();
                        keys[ordinal] = ModelTransparentOrder.key(depth, ordinal);
                        owners[ordinal] = owner; localTriangles[ordinal] = offset; ordinal++;
                    }
                }
                Arrays.sort(keys, first, ordinal);
                // Preserve mappings while permuting this fallback range (global when no BSP is present).
                for (int i = first; i < ordinal; i++) {
                    int triangle = ModelTransparentOrder.triangle(keys[i]);
                    sortOwners[i - first] = owners[triangle]; sortVertices[i - first] = localTriangles[triangle];
                }
                System.arraycopy(sortOwners, 0, owners, first, ordinal - first);
                System.arraycopy(sortVertices, 0, localTriangles, first, ordinal - first);
                centroidGroups++;
            }
        }
        if (ordinal != triangles) throw new IllegalStateException("transparent triangle mapping size mismatch");
        staging.clear(); List<Run> nextRuns = new ArrayList<>();
        int previousOwner = -1, first = 0;
        for (int i = 0; i < triangles; i++) {
            int owner = owners[i];
            if (owner != previousOwner) {
                if (previousOwner >= 0) nextRuns.add(new Run(previousOwner, first * 3, (i - first) * 3));
                first = i; previousOwner = owner;
            }
            int vertex = localTriangles[i]; boolean mirrored = sources.get(owner).mirrored;
            staging.putInt(vertex).putInt(vertex + (mirrored ? 2 : 1)).putInt(vertex + (mirrored ? 1 : 2));
        }
        if (previousOwner >= 0) nextRuns.add(new Run(previousOwner, first * 3, (triangles - first) * 3));
        staging.flip();
        long elapsed = System.nanoTime() - started;
        sorts++; sortNanos += elapsed; peakSortNanos = Math.max(peakSortNanos, elapsed);
        long uploadStarted = System.nanoTime();
        int transferBytes = staging.remaining();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(slot.buffer.slice(0, transferBytes), staging);
        ModelStaticRenderer.metrics().sortedIndexUpload(transferBytes, System.nanoTime() - uploadStarted);
        uploads++; uploadedBytes += transferBytes; runs = List.copyOf(nextRuns);
    }

    private static Result drawRuns(List<Source> sources, Matrix4fc view, boolean resolvedWorld, IndexSlot slot) {
        var minecraft = Minecraft.getInstance(); var target = minecraft.getMainRenderTarget();
        var color = !resolvedWorld && RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        var depth = !resolvedWorld && RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
        if (color == null || depth == null) return new Result(0, 0);
        // This path uses our native shader after Iris resolves, or with no pack active.
        // All per-source transforms live in uniforms, so changing instances does not require
        // opening a RenderPass or asking Iris to rebuild inverse/normal uniforms for each run.
        GpuBufferSlice[] transforms = new GpuBufferSlice[sources.size()];
        for (int i = 0; i < sources.size(); i++) {
            Source source = sources.get(i);
            transforms[i] = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(view).mul(source.transform),
                    new Vector4f(1, 1, 1, source.lightFactor), new Vector3f(), source.transform);
            ModelStaticRenderer.metrics().dynamicUniformUpload(ModelRenderMetrics.Pass.MAIN, transforms[i].length());
        }
        int draws = 0, triangles = 0;
        boolean[] recordedSources = new boolean[sources.size()];
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (var lighting = ModelNativeLight.scope();
             var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "GeometryNode sorted resident transparency", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            transparentPasses++;
            int previousSource = -1;
            RenderPipeline previousPipeline = null;
            for (Run run : runs) {
                Source source = sources.get(run.source);
                if (previousSource != run.source) {
                    lighting.receiver(source.instance.id());
                    var binding = source.asset.binding(source.primitive.material);
                    RenderPipeline pipeline = ModelStaticPipelines.pipeline(true, binding.doubleSided());
                    if (pipeline != previousPipeline) {
                        pass.setPipeline(pipeline);
                        previousPipeline = pipeline;
                    }
                    if (previousSource < 0) {
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), sampler);
                        pass.setIndexBuffer(slot.buffer, VertexFormat.IndexType.INT);
                    }
                    pass.setUniform("DynamicTransforms", transforms[run.source]);
                    var texture = minecraft.getTextureManager().getTexture(binding.base());
                    pass.bindTexture("BaseTexture", texture.getTextureView(), texture.getSampler());
                    var emission = minecraft.getTextureManager().getTexture(binding.emission());
                    var material = minecraft.getTextureManager().getTexture(binding.data());
                    pass.bindTexture("EmissionTexture", emission.getTextureView(), emission.getSampler());
                    pass.bindTexture("MaterialData", material.getTextureView(), material.getSampler());
                    var normal = minecraft.getTextureManager().getTexture(binding.normal());
                    var mr = minecraft.getTextureManager().getTexture(binding.metallicRoughness());
                    var ao = minecraft.getTextureManager().getTexture(binding.occlusion());
                    pass.bindTexture("NormalTexture", normal.getTextureView(), normal.getSampler());
                    pass.bindTexture("MetallicRoughnessTexture", mr.getTextureView(), mr.getSampler());
                    pass.bindTexture("OcclusionTexture", ao.getTextureView(), ao.getSampler());
                    pass.setVertexBuffer(0, source.mesh.vertices());
                    previousSource = run.source;
                }
                lighting.light(source.packedLight);
                pass.drawIndexed(0, run.firstIndex, run.count, 1);
                if (!recordedSources[run.source]) {
                    ModelStaticRenderer.recordTransparentObject(source.instance, source.nodeIndex);
                    recordedSources[run.source] = true;
                }
                ModelStaticRenderer.recordTransparentDraw(run.count / 3); draws++; triangles += run.count / 3;
            }
        }
        return new Result(draws, triangles);
    }

    private static void clearBuffers() {
        if (staging != null) { MemoryUtil.memFree(staging); staging = null; }
        for (IndexSlot slot : slots) {
            slot.use.retire();
            if (slot.buffer != null) {
                GpuBuffer retired = slot.buffer; long retiredBytes = slot.bytes;
                RenderSystem.queueFencedTask(() -> { retired.close(); ModelRenderAsset.residency().releaseTransientGpu(retiredBytes); });
            }
        }
        slots.clear(); currentSlot = null;
        previous = List.of(); previousGroups = List.of(); previousView = null; runs = List.of();
        if (keys.length != 0) keys = new long[0];
        if (owners.length != 0 || localTriangles.length != 0 || sortOwners.length != 0 || sortVertices.length != 0)
            owners = localTriangles = sortOwners = sortVertices = new int[0];
    }
    static void clear() { clearBuffers(); visibleBspGroups = visibleCentroidGroups = 0; lastInputTriangles = lastBspSplits = lastOutputTriangles = 0; reportInstances = List.of(); }
    static Map<String, Object> report() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cpuStagingBytes", (long) keys.length * 24 + (staging == null ? 0 : staging.capacity()));
        result.put("indexBytes", slots.stream().mapToLong(slot -> slot.bytes).sum());
        result.put("indexSlots", slots.size()); result.put("maxIndexSlots", MAX_SLOTS);
        result.put("renderPasses", transparentPasses); result.put("currentDrawRuns", runs.size());
        result.put("ringDeferred", ringDeferrals); result.put("sorts", sorts); result.put("cacheHits", cacheHits);
        result.put("uploads", uploads); result.put("uploadedBytes", uploadedBytes); result.put("deferred", deferred);
        result.put("bspOrderedGroupsTotal", bspGroups); result.put("centroidFallbackGroupsTotal", centroidGroups);
        result.put("lastInputTriangles", lastInputTriangles); result.put("lastBspSplits", lastBspSplits);
        result.put("lastOutputTriangles", lastOutputTriangles);
        result.put("visibleBspGroups", visibleBspGroups); result.put("centroidFallbackGroups", visibleCentroidGroups);
        List<Map<String, Object>> sceneReports = new ArrayList<>();
        for (ModelRenderInstance instance : reportInstances) {
            if (!instance.visible() || instance.asset().isClosed()) continue;
            ModelTransparentScene scene = instance.asset().transparentScene(instance.scene());
            if (scene != null) sceneReports.add(Map.of("instance", instance.id(), "sceneIndex", instance.scene(),
                    "order", scene.bsp() == null ? "CENTROID_OR_EMPTY" : "BSP"));
        }
        result.put("scenes", List.copyOf(sceneReports));
        result.put("localLighting", ModelNativeLight.report());
        result.put("sortCpuNanos", sortNanos); result.put("sortCpuNanosMax", peakSortNanos);
        result.put("scope", "BSP split/order within each prepared instance; all-fallback frames retain global model centroid sorting; mixed/BSP instance groups use bounds-center depth, not exact cross-instance or vanilla transparency; resident vertices; fenced three-slot index ring");
        return result;
    }
    private ModelTransparentRenderer() {}
}
