package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Immutable CPU preparation plus render-thread-owned textures and resident indexed geometry. */
public final class ModelRenderAsset implements AutoCloseable {
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final ModelResidencyLedger RESIDENCY = new ModelResidencyLedger();
    static final int FULL_LIGHT = LightCoordsUtil.pack(15, 15);

    static final class Primitive {
        final float[] positions, normals, baseUv, emissionUv, triangleCenters;
        final int[] colors, indices;
        final float[] baseColorFactor;
        final GltfAsset.Material material;
        final GltfAsset.Bounds bounds;
        Primitive(GltfAsset.Primitive source, ModelCancellation token) throws IOException {
            positions = source.positions(); normals = source.normals(); indices = source.indices();
            material = Objects.requireNonNull(source.material()); bounds = source.bounds();
            triangleCenters = new float[indices.length];
            for (int t = 0; t < indices.length; t += 3) {
                if ((t & 4095) == 0) token.check();
                for (int axis = 0; axis < 3; axis++) triangleCenters[t + axis] =
                        (positions[indices[t] * 3 + axis] + positions[indices[t + 1] * 3 + axis] + positions[indices[t + 2] * 3 + axis]) / 3;
            }
            baseColorFactor = material.baseColorFactor();
            if (normals.length != positions.length) throw new IOException("renderer requires importer-generated normals");
            float[] uv0 = source.uv0(), uv1 = source.uv1();
            baseUv = uv(source.vertexCount(), uv0, uv1, material.baseColorTexture(), token);
            // Keep meaningful UV derivatives even on a white fallback texture; some packs
            // interpret a constant zero UV as a special untextured vanilla effect.
            if (material.baseColorTexture() == null) {
                for (int i = 0; i < source.vertexCount(); i++) {
                    if ((i & 4095) == 0) token.check();
                    baseUv[i * 2] = uv0.length == baseUv.length ? uv0[i * 2] : positions[i * 3] + .371f * positions[i * 3 + 1] + .113f * positions[i * 3 + 2];
                    baseUv[i * 2 + 1] = uv0.length == baseUv.length ? uv0[i * 2 + 1] : positions[i * 3 + 2] + .279f * positions[i * 3 + 1] - .159f * positions[i * 3];
                }
            }
            emissionUv = uv(source.vertexCount(), uv0, uv1, material.emissiveTexture(), token);
            float[] color = source.colors(); colors = new int[source.vertexCount()];
            for (int i = 0; i < colors.length; i++) {
                if ((i & 4095) == 0) token.check();
                colors[i] = color.length == 0 ? 0xffffffff : channel(color[i * 4 + 3]) << 24
                        | channel(color[i * 4]) << 16 | channel(color[i * 4 + 1]) << 8 | channel(color[i * 4 + 2]);
            }
        }
        /** Worker-created split geometry, already expressed in asset-local coordinates. */
        Primitive(GltfAsset.Material material, List<ModelTransparentBsp.Triangle> triangles) {
            this.material = material; this.baseColorFactor = material.baseColorFactor();
            int count = Math.multiplyExact(triangles.size(), 3);
            positions = new float[count * 3]; normals = new float[count * 3];
            baseUv = new float[count * 2]; emissionUv = new float[count * 2];
            colors = new int[count]; indices = new int[count]; triangleCenters = new float[count];
            GltfAsset.Bounds extent = GltfAsset.Bounds.empty();
            int vertex = 0;
            for (var triangle : triangles) {
                for (var point : List.of(triangle.a(), triangle.b(), triangle.c())) {
                    int p = vertex * 3, uv = vertex * 2;
                    for (int axis = 0; axis < 3; axis++) {
                        positions[p + axis] = point.attribute(axis);
                        normals[p + axis] = point.attribute(3 + axis);
                        triangleCenters[(vertex / 3) * 3 + axis] += positions[p + axis] / 3;
                    }
                    // Preserve the interpolated normal: normalizing newly introduced vertices
                    // would change the original triangle's interpolated shading field.
                    extent = extent.include(positions[p], positions[p + 1], positions[p + 2]);
                    baseUv[uv] = point.attribute(6); baseUv[uv + 1] = point.attribute(7);
                    emissionUv[uv] = point.attribute(8); emissionUv[uv + 1] = point.attribute(9);
                    colors[vertex] = channel(point.attribute(13)) << 24 | channel(point.attribute(10)) << 16
                            | channel(point.attribute(11)) << 8 | channel(point.attribute(12));
                    indices[vertex] = vertex; vertex++;
                }
            }
            bounds = extent;
        }
        private static int channel(float v) { return Math.clamp(Math.round(v * 255), 0, 255); }
        private static float[] uv(int count, float[] uv0, float[] uv1, GltfAsset.TextureInfo info, ModelCancellation token) throws IOException {
            float[] result = new float[count * 2]; if (info == null) return result;
            float[] input = info.texCoord() == 0 ? uv0 : info.texCoord() == 1 ? uv1 : null;
            if (input == null || input.length != count * 2) throw new IOException("material references missing TEXCOORD_" + info.texCoord());
            float[] scale = info.scale(), offset = info.offset(); float c = (float) Math.cos(info.rotation()), s = (float) Math.sin(info.rotation());
            for (int i = 0; i < count; i++) {
                if ((i & 4095) == 0) token.check();
                float u = input[i * 2] * scale[0], v = input[i * 2 + 1] * scale[1];
                result[i * 2] = c * u - s * v + offset[0]; result[i * 2 + 1] = s * u + c * v + offset[1];
            }
            return result;
        }
        long cpuBytes() { return (long) (positions.length + normals.length + baseUv.length + emissionUv.length + triangleCenters.length + colors.length) * 4L + (long) indices.length * 4L; }
        long gpuEstimate() { return Math.addExact((long) indices.length * 64L, (long) indices.length * 4L); }
    }
    record Occurrence(Matrix4f transform, List<Primitive> primitives) {}
    record MaterialBinding(Identifier base, Identifier emission, Identifier data, Identifier overlay, boolean blend, boolean doubleSided, Identifier irisBase) {}

    static final class GpuPrimitive implements AutoCloseable {
        private GpuBuffer vertices, indices, mirroredIndices;
        private final VertexFormat format;
        private final int indexCount;
        private final long bytes;
        GpuPrimitive(GpuBuffer vertices, GpuBuffer indices, int indexCount, long bytes) {
            this(vertices, indices, null, indexCount, bytes, ModelStaticPipelines.FORMAT);
        }
        GpuPrimitive(GpuBuffer vertices, GpuBuffer indices, GpuBuffer mirroredIndices, int indexCount, long bytes, VertexFormat format) {
            this.vertices = vertices; this.indices = indices; this.mirroredIndices = mirroredIndices;
            this.indexCount = indexCount; this.bytes = bytes; this.format = format;
        }
        VertexFormat format() { return format; }
        GpuBuffer indices(boolean mirrored) { return mirrored && mirroredIndices != null ? mirroredIndices : indices; }
        GpuBuffer vertices() { return vertices; }
        GpuBuffer indices() { return indices; }
        int indexCount() { return indexCount; }
        long bytes() { return bytes; }
        @Override public void close() {
            GpuBuffer vertex = vertices, index = indices, reversed = mirroredIndices; vertices = null; indices = null; mirroredIndices = null;
            if (vertex != null) vertex.close(); if (index != null) index.close(); if (reversed != null) reversed.close();
        }
    }

    public static final class Prepared implements AutoCloseable {
        final GltfAsset source;
        final Map<Integer, ModelTextureBackend.Decoded> images, opaqueImages;
        final List<List<Occurrence>> scenes;
        final List<ModelTransparentScene> transparentScenes;
        final List<GltfAsset.Material> usedMaterials;
        final long cpuGeometryBytes;
        final long gpuGeometryBytes;
        final int primitiveCount;
        final int occurrenceCount;
        final List<Map<String, Object>> opaqueBatches;
        final List<Map<String, Object>> lightPartitions;
        private boolean closed;
        Prepared(GltfAsset source, Map<Integer, ModelTextureBackend.Decoded> images, Map<Integer, ModelTextureBackend.Decoded> opaqueImages, List<List<Occurrence>> scenes,
                 List<ModelTransparentScene> transparentScenes, List<GltfAsset.Material> materials, long cpuGeometryBytes, long gpuGeometryBytes, int primitiveCount, int occurrenceCount, List<Map<String, Object>> opaqueBatches, List<Map<String, Object>> lightPartitions) {
            this.lightPartitions = List.copyOf(lightPartitions);
            this.opaqueBatches = List.copyOf(opaqueBatches);
            this.source = source; this.images = Map.copyOf(images); this.opaqueImages = Map.copyOf(opaqueImages); this.scenes = List.copyOf(scenes); this.usedMaterials = List.copyOf(materials);
            this.transparentScenes = List.copyOf(transparentScenes);
            this.cpuGeometryBytes = cpuGeometryBytes; this.gpuGeometryBytes = gpuGeometryBytes; this.primitiveCount = primitiveCount; this.occurrenceCount = occurrenceCount;
        }
        public GltfAsset source() { return source; }
        public long decodedTextureBytes() { return images.values().stream().mapToLong(d -> d.bytes).sum() + opaqueImages.values().stream().mapToLong(d -> d.bytes).sum(); }
        public long cpuGeometryBytes() { return cpuGeometryBytes; }
        public long initialTextureUploadBytes() { return Math.addExact(decodedTextureBytes(), 4L + usedMaterials.size() * 40L); }
        public long gpuGeometryBytes() { return gpuGeometryBytes; }
        public int primitiveCount() { return primitiveCount; }
        public int occurrenceCount() { return occurrenceCount; }
        public List<Map<String, Object>> opaqueBatchReport() { return opaqueBatches; }
        public List<Map<String, Object>> lightPartitionReport() { return lightPartitions; }
        @Override public synchronized void close() { if (closed) return; closed = true; images.values().forEach(ModelTextureBackend.Decoded::close); opaqueImages.values().forEach(ModelTextureBackend.Decoded::close); }
    }

    private final GltfAsset source;
    private final List<List<Occurrence>> scenes;
    private final List<ModelTransparentScene> transparentScenes;
    private final IdentityHashMap<GltfAsset.Material, RenderType> materials = new IdentityHashMap<>();
    private final IdentityHashMap<GltfAsset.Material, RenderType> irisMaterials = new IdentityHashMap<>();
    private final IdentityHashMap<GltfAsset.Material, RenderType> irisShadowMaterials = new IdentityHashMap<>();
    private final IdentityHashMap<GltfAsset.Material, MaterialBinding> bindings = new IdentityHashMap<>();
    private final IdentityHashMap<Primitive, GpuPrimitive> gpuMeshes = new IdentityHashMap<>();
    private final IdentityHashMap<Primitive, GpuPrimitive> irisGpuMeshes = new IdentityHashMap<>();
    private long nativeGpuBytes, irisGpuBytes, irisCacheBuilds, irisCacheHits, irisLayoutInvalidations;
    private final IdentityHashMap<Primitive, PendingGeometry> pendingNative = new IdentityHashMap<>();
    private final IdentityHashMap<Primitive, PendingGeometry> pendingIris = new IdentityHashMap<>();
    private long irisGeometryChunks;
    private final List<Identifier> textureIds = new ArrayList<>();
    private final long identity = NEXT_ID.incrementAndGet();
    private final long cpuGeometryBytes;
    private final long gpuGeometryBytes;
    private final long decodedTextureBytes;
    private boolean closed;
    private boolean gpuResident;
    private long textureBytes;

    public static ModelResidencyLedger residency() { return RESIDENCY; }

    public static Prepared prepare(GltfAsset source, ModelCancellation token) throws IOException {
        Objects.requireNonNull(source); token.check(); Map<Integer, ModelTextureBackend.Decoded> images = new LinkedHashMap<>(), opaqueImages = new LinkedHashMap<>();
        try {
            List<List<Primitive>> meshes = new ArrayList<>(); List<GltfAsset.Material> usedMaterials = new ArrayList<>();
            Set<GltfAsset.Material> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            long geometryBytes = 0, gpuBytes = 0; int primitiveCount = 0;
            for (GltfAsset.Mesh mesh : source.meshes()) {
                List<Primitive> primitives = new ArrayList<>();
                for (GltfAsset.Primitive primitive : mesh.primitives()) {
                    token.check(); Primitive prepared = new Primitive(primitive, token);
                    geometryBytes = Math.addExact(geometryBytes, prepared.cpuBytes()); gpuBytes = Math.addExact(gpuBytes, prepared.gpuEstimate());
                    if (geometryBytes > 512L * 1024 * 1024) throw new IOException("prepared geometry exceeds 512 MiB CPU budget");
                    primitives.add(prepared); primitiveCount++;
                    if (seen.add(primitive.material())) usedMaterials.add(primitive.material());
                }
                meshes.add(List.copyOf(primitives));
            }
            Set<Integer> usedTextures = new LinkedHashSet<>();
            for (GltfAsset.Material material : usedMaterials) {
                if (material.baseColorTexture() != null) usedTextures.add(material.baseColorTexture().textureIndex());
                if (!material.unlit() && material.emissiveTexture() != null) usedTextures.add(material.emissiveTexture().textureIndex());
            }
            long remaining = ModelTextureBackend.MAX_TEXTURE_BYTES;
            for (int index : usedTextures) {
                token.check(); if (index < 0 || index >= source.textures().size()) throw new IOException("material texture index out of bounds: " + index);
                var decoded = ModelTextureBackend.decode(source.textures().get(index), token, remaining); images.put(index, decoded); remaining -= decoded.bytes;
            }
            Set<Integer> opaque = new LinkedHashSet<>();
            for (GltfAsset.Material material : usedMaterials) {
                if (material.alphaMode() == GltfAsset.AlphaMode.OPAQUE && material.baseColorTexture() != null) opaque.add(material.baseColorTexture().textureIndex());
            }
            for (int index : opaque) {
                var copy = ModelTextureBackend.opaqueCopy(images.get(index), token, remaining);
                opaqueImages.put(index, copy); remaining -= copy.bytes;
            }
            List<List<Occurrence>> scenes = new ArrayList<>(); int occurrenceCount = 0;
            for (GltfAsset.Scene scene : source.scenes()) {
                List<Occurrence> occurrences = new ArrayList<>(); record Pending(int node, Matrix4f parent, int depth) {}
                Deque<Pending> queue = new ArrayDeque<>(); for (int root : scene.roots()) queue.addLast(new Pending(root, new Matrix4f(), 0)); Set<Integer> visited = new HashSet<>();
                while (!queue.isEmpty()) {
                    token.check(); Pending pending = queue.removeFirst(); int index = pending.node();
                    if (index < 0 || index >= source.nodes().size() || !visited.add(index)) throw new IOException("cyclic/duplicate scene node " + index);
                    if (pending.depth() > 1024) throw new IOException("scene depth exceeds 1024");
                    GltfAsset.Node node = source.nodes().get(index); Matrix4f matrix = new Matrix4f(pending.parent()).mul(node.local());
                    if (!matrix.isFinite() || Math.abs(matrix.determinant3x3()) < 1e-12f) throw new IOException("singular/nonfinite world node transform " + index);
                    if (node.mesh() >= 0) { if (node.mesh() >= meshes.size()) throw new IOException("mesh index out of bounds"); occurrences.add(new Occurrence(matrix, meshes.get(node.mesh()))); occurrenceCount++; }
                    for (int child : node.children()) queue.addLast(new Pending(child, matrix, pending.depth() + 1));
                }
                scenes.add(List.copyOf(occurrences));
            }
            List<Map<String, Object>> opaqueBatches = new ArrayList<>();
            // Merge small static unlit runs before either main or shadow creates resident buffers.
            // Keep original-array estimates charged conservatively alongside the baked copy.
            for (int i = 0; i < scenes.size(); i++) {
                var batched = ModelOpaqueBatches.prepare(scenes.get(i), token, 512L * 1024 * 1024 - geometryBytes);
                long before = scenes.get(i).stream().flatMap(o -> o.primitives().stream())
                        .filter(p -> p.material.alphaMode() != GltfAsset.AlphaMode.BLEND).count();
                opaqueBatches.add(Map.of("scene", i, "sourceOpaqueDraws", before,
                        "preparedOpaqueDraws", before - batched.savedDraws(), "savedDrawsPerPass", batched.savedDraws(),
                        "additionalCpuBytes", batched.cpuBytes(),
                        "scope", "consecutive identical unlit OPAQUE material; static node transforms baked; lit/BLEND preserved; culling uses merged bounds"));
                scenes.set(i, batched.occurrences());
                geometryBytes = Math.addExact(geometryBytes, batched.cpuBytes());
                gpuBytes = Math.addExact(gpuBytes, batched.gpuBytes());
            }
            List<Map<String, Object>> lightPartitions = new ArrayList<>();
            for (int i = 0; i < scenes.size(); i++) {
                var partitioned = ModelLightPartitions.prepare(scenes.get(i), token, 512L * 1024 * 1024 - geometryBytes);
                scenes.set(i, partitioned.occurrences());
                geometryBytes = Math.addExact(geometryBytes, partitioned.cpuBytes());
                gpuBytes = Math.addExact(gpuBytes, partitioned.gpuBytes());
                lightPartitions.add(Map.of("scene", i, "partitionedPrimitives", partitioned.partitioned(),
                        "additionalDrawsPerPass", partitioned.extraDraws(), "additionalCpuBytes", partitioned.cpuBytes(),
                        "degraded", partitioned.degraded(), "targetAssetSpan", ModelLightPartitions.TARGET_SPAN,
                        "maxExtraDrawsPerScene", ModelLightPartitions.MAX_EXTRA_DRAWS,
                        "scope", "bounded lit OPAQUE/MASK cells; node transforms preserved; constant light per cell, no local light occlusion"));
            }
            List<ModelTransparentScene> transparentScenes = new ArrayList<>();
            for (List<Occurrence> scene : scenes) {
                token.check();
                var transparency = ModelTransparentScene.prepare(scene, token, 512L * 1024 * 1024 - geometryBytes);
                transparentScenes.add(transparency);
                geometryBytes = Math.addExact(geometryBytes, transparency.cpuBytes());
                gpuBytes = Math.addExact(gpuBytes, transparency.gpuEstimate());
            }
            token.check(); return new Prepared(source, images, opaqueImages, scenes, transparentScenes, usedMaterials, geometryBytes, gpuBytes, primitiveCount, occurrenceCount, opaqueBatches, lightPartitions);
        } catch (IOException | RuntimeException | Error e) { images.values().forEach(ModelTextureBackend.Decoded::close); opaqueImages.values().forEach(ModelTextureBackend.Decoded::close); throw e; }
    }

    /** Prepared stays reusable until its repository lease closes; GPU work runs only on the render thread. */
    public static ModelRenderAsset create(Prepared prepared) { RenderSystem.assertOnRenderThread(); synchronized (prepared) { if (prepared.closed) throw new IllegalStateException("prepared asset closed"); return new ModelRenderAsset(prepared); } }

    private ModelRenderAsset(Prepared prepared) {
        source = prepared.source; scenes = prepared.scenes; transparentScenes = prepared.transparentScenes; cpuGeometryBytes = prepared.cpuGeometryBytes; gpuGeometryBytes = prepared.gpuGeometryBytes; decodedTextureBytes = prepared.decodedTextureBytes();
        long initialTextures = prepared.initialTextureUploadBytes();
        if (!RESIDENCY.reserve(this, cpuGeometryBytes + decodedTextureBytes, initialTextures, initialTextures)) throw new ModelGpuBudgetException("P4 model residency budget exhausted: " + RESIDENCY.snapshot());
        boolean registered = false;
        try {
            Map<Integer, Identifier> uploaded = new HashMap<>();
            for (var entry : prepared.images.entrySet()) uploaded.put(entry.getKey(), register("texture_" + entry.getKey(), entry.getValue(), source.textures().get(entry.getKey()).sampler()));
            Map<Integer, Identifier> opaqueUploaded = new HashMap<>();
            for (var entry : prepared.opaqueImages.entrySet()) opaqueUploaded.put(entry.getKey(), register("opaque_texture_" + entry.getKey(), entry.getValue(), source.textures().get(entry.getKey()).sampler()));
            Identifier white; try (var pixel = ModelTextureBackend.solid(0xffffffff)) { white = register("white", pixel, ModelTextureBackend.WHITE_SAMPLER); }
            int number = 0;
            for (GltfAsset.Material material : prepared.usedMaterials) {
                Identifier base = material.baseColorTexture() == null ? white : uploaded.get(material.baseColorTexture().textureIndex());
                Identifier emission = material.emissiveTexture() == null || material.unlit() ? white : uploaded.get(material.emissiveTexture().textureIndex());
                Identifier data; try (var decoded = materialData(material)) { data = register("material_" + number, decoded, ModelTextureBackend.WHITE_SAMPLER); }
                boolean blend = material.alphaMode() == GltfAsset.AlphaMode.BLEND;
                Identifier irisBase = material.baseColorTexture() != null && material.alphaMode() == GltfAsset.AlphaMode.OPAQUE
                        ? opaqueUploaded.get(material.baseColorTexture().textureIndex()) : base;
                RenderSetup setup = RenderSetup.builder(ModelStaticPipelines.get(blend, material.doubleSided()))
                        .withTexture("BaseTexture", base).withTexture("EmissionTexture", emission).withTexture("MaterialData", data)
                        .withTexture("Sampler0", base).withTexture("Sampler1", white).useLightmap().bufferSize(65536).createRenderSetup();
                materials.put(material, RenderType.create("geometry_node_model_" + identity + "_" + number, setup));
                irisMaterials.put(material, RenderType.create("geometry_node_model_iris_" + identity + "_" + number,
                        RenderSetup.builder(material.unlit() ? ModelStaticPipelines.irisUnlit(blend, material.doubleSided()) : ModelStaticPipelines.iris(blend, material.doubleSided()))
                                .withTexture("Sampler0", irisBase).useOverlay().useLightmap().bufferSize(65536).createRenderSetup()));
                irisShadowMaterials.put(material, RenderType.create("geometry_node_model_iris_shadow_" + identity + "_" + number,
                        RenderSetup.builder(ModelStaticPipelines.iris(false, material.doubleSided()))
                                .withTexture("Sampler0", irisBase).useOverlay().useLightmap().bufferSize(65536).createRenderSetup()));
                ModelIrisAlphaTest.registerRenderType(irisShadowMaterials.get(material), alphaPolicy(material));
                ModelIrisAlphaTest.registerRenderType(irisMaterials.get(material), alphaPolicy(material));
                if (!blend) {
                    ModelIrisBlendOverride.registerRenderType(irisMaterials.get(material), false);
                    ModelIrisBlendOverride.registerRenderType(irisShadowMaterials.get(material), false);
                }
                bindings.put(material, new MaterialBinding(base, emission, data, white, blend, material.doubleSided(), irisBase)); number++;
            }
            // Geometry is built lazily in bounded slices for the backend that actually consumes it.
            // Creating an Iris asset must not first build an unused native copy.
            gpuResident = true;
            registered = true;
        } catch (RuntimeException | Error e) { gpuMeshes.values().forEach(GpuPrimitive::close); gpuMeshes.clear(); releaseImmediately(); RESIDENCY.release(this); closed = true; throw e; }
        if (!registered) throw new IllegalStateException("model asset was not registered");
    }

    /** Partial buffers are never exposed to a draw; each slice ends on a triangle boundary. */
    private static final class PendingGeometry implements AutoCloseable {
        final VertexFormat format;
        final boolean iris;
        final long bytes;
        GpuPrimitive storage;
        int completedVertices;
        long buildNanos;
        PendingGeometry(VertexFormat format, boolean iris, long bytes) {
            this.format = format; this.iris = iris; this.bytes = bytes;
        }
        @Override public void close() { if (storage != null) storage.close(); }
    }

    /** Called outside a host RenderPass. Both passes advance one shared, frame-budgeted build. */
    GpuPrimitive irisGpuPrimitive(Primitive primitive) {
        return residentPrimitive(primitive, irisPipeline(primitive, false).getVertexFormat(), true);
    }

    static ModelIrisAlphaPolicy alphaPolicy(GltfAsset.Material material) {
        return switch (material.alphaMode()) {
            case OPAQUE -> ModelIrisAlphaPolicy.opaque();
            case MASK -> ModelIrisAlphaPolicy.mask(material.alphaCutoff());
            case BLEND -> ModelIrisAlphaPolicy.blend();
        };
    }

    static com.mojang.blaze3d.pipeline.RenderPipeline irisPipeline(Primitive primitive, boolean shadow) {
        boolean blend = primitive.material.alphaMode() == GltfAsset.AlphaMode.BLEND;
        return !shadow && primitive.material.unlit()
                ? ModelStaticPipelines.irisUnlit(blend, primitive.material.doubleSided())
                : ModelStaticPipelines.iris(blend, primitive.material.doubleSided());
    }

    private GpuPrimitive residentPrimitive(Primitive primitive, VertexFormat format, boolean iris) {
        RenderSystem.assertOnRenderThread();
        if (closed || !gpuResident) throw new IllegalStateException("model GPU residency is unavailable");
        var cache = iris ? irisGpuMeshes : gpuMeshes;
        var pending = iris ? pendingIris : pendingNative;
        GpuPrimitive cached = cache.get(primitive);
        if (cached != null && cached.format().equals(format)) {
            if (iris) irisCacheHits++;
            return cached;
        }
        if (cached != null) {
            if (iris) irisLayoutInvalidations++;
            cache.remove(primitive);
            retireGeometry(cached, iris);
        }
        PendingGeometry build = pending.get(primitive);
        if (build != null && !build.format.equals(format)) {
            pending.remove(primitive);
            if (iris) irisLayoutInvalidations++;
            retireGeometry(build.storage, iris);
            build = null;
        }
        int total = primitive.indices.length;
        while (build == null || build.completedVertices < total) {
            int offset = build == null ? 0 : build.completedVertices;
            int count = ModelGeometryChunks.nextCount(total, offset, format.getVertexSize(), iris,
                    RESIDENCY.budget().maxUploadBytesPerFrame());
            long chunkBytes = (long) count * (format.getVertexSize() + (iris ? 8 : 4));
            if (!ModelIrisUploadBudget.INSTANCE.tryAcquire(chunkBytes)) return null;
            long started = System.nanoTime();
            try {
                if (build == null) {
                    long bytes = Math.multiplyExact((long) total, format.getVertexSize() + (iris ? 8L : 4L));
                    if (!RESIDENCY.reserveAdditionalGpu(this, bytes))
                        throw new ModelGpuBudgetException("Resident geometry budget exhausted: " + RESIDENCY.snapshot());
                    build = new PendingGeometry(format, iris, bytes);
                    long allocationStarted = System.nanoTime();
                    try { build.storage = allocateGeometry(total, format, iris, bytes); }
                    catch (RuntimeException | Error failure) { RESIDENCY.releaseAdditionalGpu(this, bytes); throw failure; }
                    finally { ModelStaticRenderer.metrics().residentGeometryAllocation(System.nanoTime() - allocationStarted); }
                    pending.put(primitive, build);
                    if (iris) irisGpuBytes += bytes; else nativeGpuBytes += bytes;
                }
                uploadGeometryChunk(primitive, build, count);
                build.completedVertices += count;
                if (iris) irisGeometryChunks++;
            } catch (RuntimeException | Error failure) {
                PendingGeometry failed = pending.remove(primitive);
                if (failed != null) retireGeometry(failed.storage, iris);
                throw failure;
            } finally {
                ModelIrisUploadBudget.INSTANCE.recordElapsed(System.nanoTime() - started);
            }
        }
        pending.remove(primitive);
        cache.put(primitive, build.storage);
        ModelStaticRenderer.metrics().residentGeometryBuild(build.buildNanos);
        if (iris) irisCacheBuilds++;
        return build.storage;
    }

    private void retireGeometry(GpuPrimitive mesh, boolean iris) {
        if (iris) irisGpuBytes -= mesh.bytes(); else nativeGpuBytes -= mesh.bytes();
        // Pending uploads and old-layout draws can still be in flight.
        RenderSystem.queueFencedTask(() -> { mesh.close(); RESIDENCY.releaseAdditionalGpu(this, mesh.bytes()); });
    }

    private GpuPrimitive allocateGeometry(int count, VertexFormat format, boolean iris, long bytes) {
        GpuBuffer vertex = null, index = null, reversed = null;
        try {
            var device = RenderSystem.getDevice();
            vertex = device.createBuffer(() -> "GeometryNode resident vertices " + identity,
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, (long) count * format.getVertexSize());
            index = device.createBuffer(() -> "GeometryNode resident indices " + identity,
                    GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, (long) count * Integer.BYTES);
            if (iris) reversed = device.createBuffer(() -> "GeometryNode resident mirrored indices " + identity,
                    GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, (long) count * Integer.BYTES);
            return new GpuPrimitive(vertex, index, reversed, count, bytes, format);
        } catch (RuntimeException | Error failure) {
            if (vertex != null) vertex.close(); if (index != null) index.close(); if (reversed != null) reversed.close();
            throw failure;
        }
    }

    private void uploadGeometryChunk(Primitive primitive, PendingGeometry build, int count) {
        long buildStarted = System.nanoTime();
        int offset = build.completedVertices;
        VertexFormat format = build.format;
        try (ByteBufferBuilder arena = new ByteBufferBuilder(Math.max(8192, count * format.getVertexSize()))) {
            // Preserve Iris's tangent/mid-UV generation. A chunk contains only complete triangles.
            BufferBuilder builder = new BufferBuilder(arena, VertexFormat.Mode.TRIANGLES, format);
            PoseStack.Pose pose = new PoseStack().last();
            if (build.iris) {
                for (int i = offset; i < offset + count; i++) emitIrisVertex(primitive, pose, primitive.indices[i], builder);
            } else {
                for (int i = offset; i < offset + count; i += 3) emitStaticTriangle(primitive, pose, i, builder);
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                ByteBuffer vertexData = mesh.vertexBuffer();
                if (mesh.drawState().vertexCount() != count || vertexData == null
                        || vertexData.remaining() != (long) count * format.getVertexSize())
                    throw new IllegalStateException("Unexpected resident geometry chunk layout/count");
                int entityAttribute = format.getElementAttributeNames().indexOf("iris_Entity");
                if (build.iris && entityAttribute >= 0) {
                    VertexFormatElement element = format.getElements().get(entityAttribute);
                    if (element.type() != VertexFormatElement.Type.USHORT || element.count() != 4)
                        throw new IllegalStateException("unsupported Iris entity ID vertex layout");
                    ModelIrisGeometryLayout.neutralEntityIds(vertexData, count, format.getVertexSize(), format.getOffset(element));
                }
                ByteBuffer indexData = ModelGeometryChunks.indices(offset, count, false);
                ByteBuffer reversedData = build.iris ? ModelGeometryChunks.indices(offset, count, true) : null;
                long buildElapsed = System.nanoTime() - buildStarted;
                build.buildNanos += buildElapsed;
                ModelStaticRenderer.metrics().residentGeometryChunk(buildElapsed);
                long uploadStarted = System.nanoTime(), uploadedBytes = 0;
                try {
                    var encoder = RenderSystem.getDevice().createCommandEncoder();
                    int vertexBytes = vertexData.remaining();
                    encoder.writeToBuffer(build.storage.vertices().slice((long) offset * format.getVertexSize(), vertexBytes), vertexData);
                    uploadedBytes += vertexBytes;
                    encoder.writeToBuffer(build.storage.indices().slice((long) offset * 4, (long) count * 4), indexData);
                    uploadedBytes += (long) count * 4;
                    if (reversedData != null) {
                        encoder.writeToBuffer(build.storage.indices(true).slice((long) offset * 4, (long) count * 4), reversedData);
                        uploadedBytes += (long) count * 4;
                    }
                } finally {
                    if (uploadedBytes > 0) ModelStaticRenderer.metrics().residentGeometryUpload(uploadedBytes, System.nanoTime() - uploadStarted);
                }
            }
        }
    }

    private static void emitIrisVertex(Primitive primitive, PoseStack.Pose pose, int source, VertexConsumer builder) {
        int at = source * 3, uv = source * 2, color = primitive.colors[source];
        float[] factor = primitive.baseColorFactor;
        int alpha = primitive.material.alphaMode() == GltfAsset.AlphaMode.OPAQUE ? 255
                : Math.clamp(Math.round((color >>> 24) * factor[3]), 0, 255);
        builder.addVertex(pose, primitive.positions[at], primitive.positions[at + 1], primitive.positions[at + 2])
                .setColor(Math.clamp(Math.round(((color >>> 16) & 255) * factor[0]), 0, 255),
                        Math.clamp(Math.round(((color >>> 8) & 255) * factor[1]), 0, 255),
                        Math.clamp(Math.round((color & 255) * factor[2]), 0, 255), alpha)
                .setUv(primitive.baseUv[uv], primitive.baseUv[uv + 1])
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_LIGHT)
                .setNormal(pose, primitive.normals[at], primitive.normals[at + 1], primitive.normals[at + 2]);
    }

    private static void emitStaticTriangle(Primitive primitive, PoseStack.Pose pose, int offset, VertexConsumer out) {
        for (int corner = 0; corner < 3; corner++) {
            int index = primitive.indices[offset + corner], at = index * 3, uv = index * 2;
            int u = Float.floatToRawIntBits(primitive.emissionUv[uv]), v = Float.floatToRawIntBits(primitive.emissionUv[uv + 1]);
            out.addVertex(pose, primitive.positions[at], primitive.positions[at + 1], primitive.positions[at + 2])
                    .setColor(primitive.colors[index]).setUv(primitive.baseUv[uv], primitive.baseUv[uv + 1])
                    .setUv1(u & 65535, u >>> 16).setUv2(v & 65535, v >>> 16).setLineWidth((float) FULL_LIGHT)
                    .setNormal(pose, primitive.normals[at], primitive.normals[at + 1], primitive.normals[at + 2]);
        }
    }

    private Identifier register(String name, ModelTextureBackend.Decoded data, GltfAsset.Sampler sampler) {
        Identifier id = ModelStaticPipelines.id("runtime_model/" + identity + "/" + name);
        var texture = ModelTextureBackend.upload("GeometryNode model " + id, data, sampler, !name.startsWith("material_"));
        try { Minecraft.getInstance().getTextureManager().register(id, texture); textureIds.add(id); textureBytes += data.bytes; }
        catch (RuntimeException | Error e) { texture.close(); throw e; }
        return id;
    }
    private static ModelTextureBackend.Decoded materialData(GltfAsset.Material material) {
        float[] base = material.baseColorFactor(), emission = material.unlit() ? new float[3] : material.emissiveFactor();
        float[] values = {base[0], base[1], base[2], base[3], emission[0], emission[1], emission[2], material.alphaCutoff(), material.alphaMode().ordinal(), material.unlit() ? 1 : 0};
        NativeImage image = new NativeImage(values.length, 1, false);
        for (int i = 0; i < values.length; i++) { int bits = Float.floatToRawIntBits(values[i]); int argb = ((bits >>> 24) & 255) << 24 | (bits & 255) << 16 | ((bits >>> 8) & 255) << 8 | ((bits >>> 16) & 255); image.setPixel(i, 0, argb); }
        return new ModelTextureBackend.Decoded(List.of(image));
    }

    List<Occurrence> occurrences(int scene) { if (closed) return List.of(); int selected = scene < 0 ? source.defaultScene() : scene; if (selected < 0 && !scenes.isEmpty()) selected = 0; if (selected < 0 || selected >= scenes.size()) return List.of(); return scenes.get(selected); }
    ModelTransparentScene transparentScene(int scene) {
        int selected = scene < 0 ? source.defaultScene() : scene;
        if (selected < 0 && !transparentScenes.isEmpty()) selected = 0;
        return closed || selected < 0 || selected >= transparentScenes.size() ? ModelTransparentScene.EMPTY : transparentScenes.get(selected);
    }
    public List<Map<String, Object>> transparentPreparationReport() {
        return transparentScenes.stream().map(ModelTransparentScene::report).toList();
    }
    RenderType renderType(GltfAsset.Material material) { return materials.get(material); }
    RenderType irisRenderType(GltfAsset.Material material) { return irisMaterials.get(material); }
    RenderType irisShadowRenderType(GltfAsset.Material material) { return irisShadowMaterials.get(material); }
    MaterialBinding binding(GltfAsset.Material material) { return bindings.get(material); }
    GpuPrimitive gpuPrimitive(Primitive primitive) { return residentPrimitive(primitive, ModelStaticPipelines.FORMAT, false); }
    public Map<String, Object> irisResidencyReport() {
        return Map.of("primitives", irisGpuMeshes.size(), "gpuBytes", irisGpuBytes,
                "builds", irisCacheBuilds, "cacheHits", irisCacheHits, "layoutInvalidations", irisLayoutInvalidations,
                "pendingPrimitives", pendingIris.size(), "uploadedChunks", irisGeometryChunks);
    }
    public Map<String, Object> irisMaterialReport() {
        List<String> emission = new ArrayList<>();
        for (GltfAsset.Material material : bindings.keySet()) {
            float[] factor = material.emissiveFactor();
            if (!material.unlit() && (factor[0] > 0 || factor[1] > 0 || factor[2] > 0)) emission.add(material.name());
        }
        return Map.of("opaqueAlpha", "image mip alpha forced opaque; vertex/factor alpha ignored",
                "unlit", "EMISSIVE_ENTITIES with scoped material blend; pack-specific pixels pending",
                "emissionProtocol", emission.isEmpty() ? "NOT_REQUIRED" : "DEFERRED_P7_PACK_GBUFFER_PROTOCOL",
                "emissiveMaterials", emission);
    }
    public GltfAsset source() { return source; }
    public long textureBytes() { return textureBytes; }
    public long cpuGeometryBytes() { return cpuGeometryBytes; }
    public long gpuGeometryBytes() { return nativeGpuBytes + irisGpuBytes; }
    public long decodedTextureBytes() { return decodedTextureBytes; }
    public boolean isClosed() { return closed; }
    public boolean hasGpuGeometry() { return (!gpuMeshes.isEmpty() || !irisGpuMeshes.isEmpty()) && !closed; }
    public boolean gpuResident() { return gpuResident && !closed; }
    void touch() { RESIDENCY.touch(this); }
    void evictGpu() {
        if (closed || !gpuResident) return;
        gpuResident = false; materials.clear(); irisMaterials.clear(); irisShadowMaterials.clear(); bindings.clear();
        retireResources();
    }
    private void releaseImmediately() { var manager = Minecraft.getInstance().getTextureManager(); for (Identifier id : textureIds) manager.release(id); textureIds.clear(); textureBytes = 0; }
    private void retireResources() {
        List<Identifier> retired = List.copyOf(textureIds); textureIds.clear(); textureBytes = 0;
        List<GpuPrimitive> meshes = new ArrayList<>(gpuMeshes.values()); meshes.addAll(irisGpuMeshes.values());
        pendingNative.values().forEach(build -> meshes.add(build.storage));
        pendingIris.values().forEach(build -> meshes.add(build.storage));
        pendingNative.clear(); pendingIris.clear();
        gpuMeshes.clear(); irisGpuMeshes.clear(); nativeGpuBytes = irisGpuBytes = 0;
        RenderSystem.queueFencedTask(() -> {
            var manager = Minecraft.getInstance().getTextureManager(); retired.forEach(manager::release);
            meshes.forEach(GpuPrimitive::close); RESIDENCY.release(this);
        });
    }
    @Override public void close() {
        RenderSystem.assertOnRenderThread(); if (closed) return;
        closed = true; gpuResident = false; retireResources();
    }
}
