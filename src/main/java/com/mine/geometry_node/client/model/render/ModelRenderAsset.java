package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Immutable CPU preparation plus render-thread-owned textures and resident indexed geometry. */
public final class ModelRenderAsset implements AutoCloseable {
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final ModelResidencyLedger RESIDENCY = new ModelResidencyLedger();
    static final int FULL_LIGHT = LightCoordsUtil.pack(15, 15);

    static final class Primitive {
        final float[] positions, normals, baseUv, emissionUv;
        final int[] colors, indices;
        final GltfAsset.Material material;
        final GltfAsset.Bounds bounds;
        Primitive(GltfAsset.Primitive source, ModelCancellation token) throws IOException {
            positions = source.positions(); normals = source.normals(); indices = source.indices();
            material = Objects.requireNonNull(source.material()); bounds = source.bounds();
            if (normals.length != positions.length) throw new IOException("renderer requires importer-generated normals");
            float[] uv0 = source.uv0(), uv1 = source.uv1();
            baseUv = uv(source.vertexCount(), uv0, uv1, material.baseColorTexture(), token);
            emissionUv = uv(source.vertexCount(), uv0, uv1, material.emissiveTexture(), token);
            float[] color = source.colors(); colors = new int[source.vertexCount()];
            for (int i = 0; i < colors.length; i++) {
                if ((i & 4095) == 0) token.check();
                colors[i] = color.length == 0 ? 0xffffffff : channel(color[i * 4 + 3]) << 24
                        | channel(color[i * 4]) << 16 | channel(color[i * 4 + 1]) << 8 | channel(color[i * 4 + 2]);
            }
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
        long cpuBytes() { return (long) (positions.length + normals.length + baseUv.length + emissionUv.length + colors.length) * 4L + (long) indices.length * 4L; }
        long gpuEstimate() { return Math.addExact((long) indices.length * 64L, (long) indices.length * 4L); }
    }
    record Occurrence(Matrix4f transform, List<Primitive> primitives) {}
    record MaterialBinding(Identifier base, Identifier emission, Identifier data, boolean blend, boolean doubleSided) {}

    static final class GpuPrimitive implements AutoCloseable {
        private GpuBuffer vertices, indices;
        private final int indexCount;
        private final long bytes;
        GpuPrimitive(GpuBuffer vertices, GpuBuffer indices, int indexCount, long bytes) { this.vertices = vertices; this.indices = indices; this.indexCount = indexCount; this.bytes = bytes; }
        GpuBuffer vertices() { return vertices; }
        GpuBuffer indices() { return indices; }
        int indexCount() { return indexCount; }
        long bytes() { return bytes; }
        @Override public void close() {
            GpuBuffer vertex = vertices, index = indices; vertices = null; indices = null;
            if (vertex != null) vertex.close(); if (index != null) index.close();
        }
    }

    public static final class Prepared implements AutoCloseable {
        final GltfAsset source;
        final Map<Integer, ModelTextureBackend.Decoded> images;
        final List<List<Occurrence>> scenes;
        final List<GltfAsset.Material> usedMaterials;
        final long cpuGeometryBytes;
        final long gpuGeometryBytes;
        final int primitiveCount;
        final int occurrenceCount;
        private boolean closed;
        Prepared(GltfAsset source, Map<Integer, ModelTextureBackend.Decoded> images, List<List<Occurrence>> scenes,
                 List<GltfAsset.Material> materials, long cpuGeometryBytes, long gpuGeometryBytes, int primitiveCount, int occurrenceCount) {
            this.source = source; this.images = Map.copyOf(images); this.scenes = List.copyOf(scenes); this.usedMaterials = List.copyOf(materials);
            this.cpuGeometryBytes = cpuGeometryBytes; this.gpuGeometryBytes = gpuGeometryBytes; this.primitiveCount = primitiveCount; this.occurrenceCount = occurrenceCount;
        }
        public GltfAsset source() { return source; }
        public long decodedTextureBytes() { return images.values().stream().mapToLong(d -> d.bytes).sum(); }
        public long cpuGeometryBytes() { return cpuGeometryBytes; }
        public long gpuGeometryBytes() { return gpuGeometryBytes; }
        public int primitiveCount() { return primitiveCount; }
        public int occurrenceCount() { return occurrenceCount; }
        @Override public synchronized void close() { if (closed) return; closed = true; images.values().forEach(ModelTextureBackend.Decoded::close); }
    }

    private final GltfAsset source;
    private final List<List<Occurrence>> scenes;
    private final IdentityHashMap<GltfAsset.Material, RenderType> materials = new IdentityHashMap<>();
    private final IdentityHashMap<GltfAsset.Material, MaterialBinding> bindings = new IdentityHashMap<>();
    private final IdentityHashMap<Primitive, GpuPrimitive> gpuMeshes = new IdentityHashMap<>();
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
        Objects.requireNonNull(source); token.check(); Map<Integer, ModelTextureBackend.Decoded> images = new LinkedHashMap<>();
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
            token.check(); return new Prepared(source, images, scenes, usedMaterials, geometryBytes, gpuBytes, primitiveCount, occurrenceCount);
        } catch (IOException | RuntimeException | Error e) { images.values().forEach(ModelTextureBackend.Decoded::close); throw e; }
    }

    /** Prepared stays reusable until its repository lease closes; GPU work runs only on the render thread. */
    public static ModelRenderAsset create(Prepared prepared) { RenderSystem.assertOnRenderThread(); synchronized (prepared) { if (prepared.closed) throw new IllegalStateException("prepared asset closed"); return new ModelRenderAsset(prepared); } }

    private ModelRenderAsset(Prepared prepared) {
        source = prepared.source; scenes = prepared.scenes; cpuGeometryBytes = prepared.cpuGeometryBytes; gpuGeometryBytes = prepared.gpuGeometryBytes; decodedTextureBytes = prepared.decodedTextureBytes();
        if (!RESIDENCY.reserve(this, cpuGeometryBytes + decodedTextureBytes, gpuGeometryBytes + decodedTextureBytes, decodedTextureBytes)) throw new IllegalStateException("P4 model residency budget exhausted: " + RESIDENCY.snapshot());
        boolean registered = false;
        try {
            Map<Integer, Identifier> uploaded = new HashMap<>();
            for (var entry : prepared.images.entrySet()) uploaded.put(entry.getKey(), register("texture_" + entry.getKey(), entry.getValue(), source.textures().get(entry.getKey()).sampler()));
            Identifier white; try (var pixel = ModelTextureBackend.solid(0xffffffff)) { white = register("white", pixel, ModelTextureBackend.WHITE_SAMPLER); }
            int number = 0;
            for (GltfAsset.Material material : prepared.usedMaterials) {
                Identifier base = material.baseColorTexture() == null ? white : uploaded.get(material.baseColorTexture().textureIndex());
                Identifier emission = material.emissiveTexture() == null || material.unlit() ? white : uploaded.get(material.emissiveTexture().textureIndex());
                Identifier data; try (var decoded = materialData(material)) { data = register("material_" + number, decoded, ModelTextureBackend.WHITE_SAMPLER); }
                boolean blend = material.alphaMode() == GltfAsset.AlphaMode.BLEND;
                RenderSetup setup = RenderSetup.builder(ModelStaticPipelines.get(blend, material.doubleSided()))
                        .withTexture("BaseTexture", base).withTexture("EmissionTexture", emission).withTexture("MaterialData", data).useLightmap().bufferSize(65536).createRenderSetup();
                materials.put(material, RenderType.create("geometry_node_model_" + identity + "_" + number, setup));
                bindings.put(material, new MaterialBinding(base, emission, data, blend, material.doubleSided())); number++;
            }
            Set<Primitive> unique = Collections.newSetFromMap(new IdentityHashMap<>());
            scenes.forEach(scene -> scene.forEach(occurrence -> occurrence.primitives().forEach(unique::add)));
            for (Primitive primitive : unique) gpuMeshes.put(primitive, uploadGeometry(primitive));
            gpuResident = true;
            registered = true;
        } catch (RuntimeException | Error e) { gpuMeshes.values().forEach(GpuPrimitive::close); gpuMeshes.clear(); releaseImmediately(); RESIDENCY.release(this); closed = true; throw e; }
        if (!registered) throw new IllegalStateException("model asset was not registered");
    }

    private GpuPrimitive uploadGeometry(Primitive primitive) {
        GpuBuffer vertex = null, index = null;
        long initialCapacity = Math.min(4L * 1024 * 1024, Math.max(8192L, (long) primitive.indices.length * 3L * 64L));
        try (ByteBufferBuilder arena = new ByteBufferBuilder((int) initialCapacity)) {
            BufferBuilder builder = new BufferBuilder(arena, VertexFormat.Mode.TRIANGLES, ModelStaticPipelines.FORMAT);
            PoseStack.Pose identityPose = new PoseStack().last();
            for (int offset = 0; offset < primitive.indices.length; offset += 3) emitStaticTriangle(primitive, identityPose, offset, builder);
            try (MeshData mesh = builder.buildOrThrow()) {
                ByteBuffer vertexData = mesh.vertexBuffer();
                int vertexCount = mesh.drawState().vertexCount();
                if (vertexData == null || !vertexData.hasRemaining() || vertexCount <= 0) throw new IllegalStateException("empty model primitive geometry");
                vertex = RenderSystem.getDevice().createBuffer(() -> "GeometryNode P4 model vertices " + identity, GpuBuffer.USAGE_VERTEX, vertexData);
                long indexBytes = Math.multiplyExact((long) vertexCount, 4L);
                if (indexBytes > Integer.MAX_VALUE) throw new IllegalStateException("P4 primitive index buffer exceeds direct buffer limit: " + indexBytes);
                ByteBuffer indexData = ByteBuffer.allocateDirect((int) indexBytes).order(ByteOrder.nativeOrder());
                for (int i = 0; i < vertexCount; i++) indexData.putInt(i); indexData.flip();
                index = RenderSystem.getDevice().createBuffer(() -> "GeometryNode P4 model indices " + identity, GpuBuffer.USAGE_INDEX, indexData);
                return new GpuPrimitive(vertex, index, vertexCount, (long) vertexData.remaining() + indexData.remaining());
            }
        } catch (RuntimeException | Error e) { if (vertex != null) vertex.close(); if (index != null) index.close(); throw e; }
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
        float[] base = material.baseColorFactor(), emission = material.emissiveFactor();
        float[] values = {base[0], base[1], base[2], base[3], emission[0], emission[1], emission[2], material.alphaCutoff(), material.alphaMode().ordinal(), material.unlit() ? 1 : 0};
        NativeImage image = new NativeImage(values.length, 1, false);
        for (int i = 0; i < values.length; i++) { int bits = Float.floatToRawIntBits(values[i]); int argb = ((bits >>> 24) & 255) << 24 | (bits & 255) << 16 | ((bits >>> 8) & 255) << 8 | ((bits >>> 16) & 255); image.setPixel(i, 0, argb); }
        return new ModelTextureBackend.Decoded(List.of(image));
    }

    List<Occurrence> occurrences(int scene) { if (closed) return List.of(); int selected = scene < 0 ? source.defaultScene() : scene; if (selected < 0 && !scenes.isEmpty()) selected = 0; if (selected < 0 || selected >= scenes.size()) return List.of(); return scenes.get(selected); }
    RenderType renderType(GltfAsset.Material material) { return materials.get(material); }
    MaterialBinding binding(GltfAsset.Material material) { return bindings.get(material); }
    GpuPrimitive gpuPrimitive(Primitive primitive) { return gpuMeshes.get(primitive); }
    public GltfAsset source() { return source; }
    public long textureBytes() { return textureBytes; }
    public long cpuGeometryBytes() { return cpuGeometryBytes; }
    public long gpuGeometryBytes() { return gpuGeometryBytes; }
    public long decodedTextureBytes() { return decodedTextureBytes; }
    public boolean isClosed() { return closed; }
    public boolean hasGpuGeometry() { return !gpuMeshes.isEmpty() && !closed; }
    public boolean gpuResident() { return gpuResident && !closed; }
    void touch() { RESIDENCY.touch(this); }
    void evictGpu() { if (closed) return; List<GpuPrimitive> old = List.copyOf(gpuMeshes.values()); gpuMeshes.clear(); old.forEach(GpuPrimitive::close); materials.clear(); bindings.clear(); gpuResident = false; releaseImmediately(); }
    private void releaseImmediately() { var manager = Minecraft.getInstance().getTextureManager(); for (Identifier id : textureIds) manager.release(id); textureIds.clear(); textureBytes = 0; }
    @Override public void close() { RenderSystem.assertOnRenderThread(); if (closed) return; closed = true; gpuResident = false; List<Identifier> retired = List.copyOf(textureIds); textureIds.clear(); textureBytes = 0; List<GpuPrimitive> meshes = List.copyOf(gpuMeshes.values()); gpuMeshes.clear(); RenderSystem.queueFencedTask(() -> { var manager = Minecraft.getInstance().getTextureManager(); retired.forEach(manager::release); meshes.forEach(GpuPrimitive::close); RESIDENCY.release(this); }); }
}
