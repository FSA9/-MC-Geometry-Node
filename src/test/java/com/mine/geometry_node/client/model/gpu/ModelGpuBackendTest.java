package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.api.*;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ModelGpuBackendTest {
    @Test
    void combinesPrimitivesWithTheSameLayoutIntoOneIndexedBufferPair() {
        ModelGpuUploadPlan plan = new ModelGpuUploadPlanner().plan(twoTriangleModel(), List.of());

        assertEquals(1, plan.layoutGroups().size());
        ModelGpuLayoutGroupPlan group = plan.layoutGroups().getFirst();
        assertEquals(6, group.vertexCount());
        assertEquals(6, group.indexCount());
        assertArrayEquals(new long[]{0, 1, 2, 3, 4, 5}, uint32Values(group.indexData()));
        assertEquals(2, plan.drawRanges().size());
        assertEquals(0, plan.drawRanges().get(0).firstIndex());
        assertEquals(3, plan.drawRanges().get(1).firstIndex());
    }

    @Test
    void interleavesCanonicalAttributesWithoutChangingTheirValues() {
        ModelGpuUploadPlan plan = new ModelGpuUploadPlanner().plan(oneTexturedTriangleModel(), List.of());
        ModelGpuLayoutGroupPlan group = plan.layoutGroups().getFirst();

        assertEquals(20, group.vertexStride());
        ByteBuffer bytes = ByteBuffer.wrap(group.vertexData()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0.0F, bytes.getFloat(0));
        assertEquals(0.0F, bytes.getFloat(4));
        assertEquals(0.0F, bytes.getFloat(8));
        assertEquals(0.25F, bytes.getFloat(12));
        assertEquals(0.75F, bytes.getFloat(16));
    }

    @Test
    void projectsSelectedSourceUvSetAndIgnoresUnusedGeneralAttributes() {
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        ModelAttributeSemantic uv1 = ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.TEXCOORD, 1);
        ModelAttributeSemantic color1 = ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.COLOR, 1);
        Map<ModelAttributeSemantic, ModelVertexAttribute> attributes = new java.util.LinkedHashMap<>();
        attributes.put(ModelAttributeSemantic.POSITION, new ModelVertexAttribute(ModelAttributeSemantic.POSITION,
                ModelComponentType.FLOAT32, 3, false, 3, floatBytes(0, 0, 0, 1, 0, 0, 0, 1, 0)));
        attributes.put(ModelAttributeSemantic.TANGENT, new ModelVertexAttribute(ModelAttributeSemantic.TANGENT,
                ModelComponentType.FLOAT32, 4, false, 3, floatBytes(1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1)));
        attributes.put(uv1, new ModelVertexAttribute(uv1, ModelComponentType.FLOAT32, 2, false, 3,
                floatBytes(0.2F, 0.8F, 1, 0, 0, 1)));
        attributes.put(color1, new ModelVertexAttribute(color1, ModelComponentType.FLOAT32, 4, false, 3,
                floatBytes(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)));
        ModelPrimitive primitive = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES, attributes, indices(), 0, bounds);
        ModelMaterial material = new ModelMaterial("uv1", 1, 1, 1, 1,
                new ModelTextureInfo(0, 1, ModelTextureTransform.identity()), ModelAlphaMode.OPAQUE, 0.5F, false,
                0, 0, 0, ModelTextureInfo.absent());
        ModelDefinition definition = modelWithMaterial(primitive, bounds, material);

        ModelGpuLayoutGroupPlan group = new ModelGpuUploadPlanner().plan(definition,
                List.of(new DecodedModelImage(1, 1, new byte[]{0, 0, 0, (byte) 255}))).layoutGroups().getFirst();

        assertTrue(group.layout().elements().stream().anyMatch(element ->
                element.semantic().equals(ModelAttributeSemantic.TEXCOORD_0)));
        assertFalse(group.layout().elements().stream().anyMatch(element ->
                element.semantic().equals(ModelAttributeSemantic.TANGENT) || element.semantic().equals(color1)));
        assertEquals(0.2F, ByteBuffer.wrap(group.vertexData()).order(ByteOrder.LITTLE_ENDIAN).getFloat(12));
        assertEquals(Map.of(1, 0), new ModelGpuUploadPlanner().plan(definition,
                List.of(new DecodedModelImage(1, 1, new byte[]{0, 0, 0, (byte) 255})))
                .drawRanges().getFirst().physicalUvSlots());
    }

    @Test
    void projectsTwoReferencedUvSetsAndRejectsAdditionalSkinSets() {
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        ModelAttributeSemantic uv1 = ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.TEXCOORD, 1);
        Map<ModelAttributeSemantic, ModelVertexAttribute> attributes = new java.util.LinkedHashMap<>();
        attributes.put(ModelAttributeSemantic.POSITION, new ModelVertexAttribute(ModelAttributeSemantic.POSITION,
                ModelComponentType.FLOAT32, 3, false, 3, floatBytes(0, 0, 0, 1, 0, 0, 0, 1, 0)));
        attributes.put(ModelAttributeSemantic.TEXCOORD_0, new ModelVertexAttribute(ModelAttributeSemantic.TEXCOORD_0,
                ModelComponentType.FLOAT32, 2, false, 3, floatBytes(0, 0, 1, 0, 0, 1)));
        attributes.put(uv1, new ModelVertexAttribute(uv1, ModelComponentType.FLOAT32, 2, false, 3,
                floatBytes(0, 0, 1, 0, 0, 1)));
        ModelPrimitive primitive = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES, attributes, indices(), 0, bounds);
        ModelMaterial material = new ModelMaterial("twoUv", 1, 1, 1, 1,
                new ModelTextureInfo(0, 0, ModelTextureTransform.identity()), ModelAlphaMode.OPAQUE, 0.5F, false,
                0, 0, 0, new ModelTextureInfo(0, 1, ModelTextureTransform.identity()));
        ModelGpuLayoutGroupPlan multiUv = new ModelGpuUploadPlanner().plan(
                modelWithMaterial(primitive, bounds, material),
                List.of(new DecodedModelImage(1, 1, new byte[]{0, 0, 0, (byte) 255})))
                .layoutGroups().getFirst();
        assertTrue(multiUv.layout().elements().stream().anyMatch(element ->
                element.semantic().equals(ModelAttributeSemantic.TEXCOORD_0)));
        assertTrue(multiUv.layout().elements().stream().anyMatch(element ->
                element.semantic().equals(ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.TEXCOORD, 1))));

        ModelAttributeSemantic joints1 = ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.JOINTS, 1);
        ModelAttributeSemantic weights1 = ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.WEIGHTS, 1);
        attributes.put(joints1, new ModelVertexAttribute(joints1, ModelComponentType.UINT16, 4, false, 3,
                new byte[24]));
        attributes.put(weights1, new ModelVertexAttribute(weights1, ModelComponentType.FLOAT32, 4, false, 3,
                floatBytes(1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0)));
        ModelPrimitive extraSkin = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES, attributes, indices(), 0, bounds);
        assertThrows(IllegalArgumentException.class, () -> new ModelGpuUploadPlanner().plan(
                modelWithMaterial(extraSkin, bounds, ModelMaterial.defaultMaterial()), List.of()));
    }

    @Test
    void packsNormalsAndColorsIntoTheNativeCompactGpuFormat() {
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        ModelVertexAttribute positions = new ModelVertexAttribute(ModelAttributeSemantic.POSITION,
                ModelComponentType.FLOAT32, 3, false, 3, floatBytes(0, 0, 0, 1, 0, 0, 0, 1, 0));
        ModelVertexAttribute normals = new ModelVertexAttribute(ModelAttributeSemantic.NORMAL,
                ModelComponentType.FLOAT32, 3, false, 3, floatBytes(0, 1, 0, 0, 1, 0, 0, 1, 0));
        ModelVertexAttribute colors = new ModelVertexAttribute(ModelAttributeSemantic.COLOR_0,
                ModelComponentType.FLOAT32, 3, false, 3, floatBytes(1, 0.5F, 0, 1, 1, 1, 0, 0, 0));
        ModelPrimitive primitive = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES,
                Map.of(ModelAttributeSemantic.POSITION, positions, ModelAttributeSemantic.NORMAL, normals,
                        ModelAttributeSemantic.COLOR_0, colors), indices(), 0, bounds);
        ModelDefinition definition = definition(List.of(new ModelMesh("mesh", List.of(primitive), bounds)),
                List.of(node(0, bounds)), bounds, List.of());

        ModelGpuLayoutGroupPlan group = new ModelGpuUploadPlanner().plan(definition, List.of()).layoutGroups().getFirst();

        assertEquals(20, group.vertexStride());
        assertEquals(ModelComponentType.INT8, group.layout().elements().get(1).componentType());
        assertEquals(ModelComponentType.UINT8, group.layout().elements().get(2).componentType());
        byte[] bytes = group.vertexData();
        assertArrayEquals(new byte[]{0, 127, 0}, new byte[]{bytes[12], bytes[13], bytes[14]});
        assertArrayEquals(new byte[]{(byte) 255, (byte) 128, 0, (byte) 255},
                new byte[]{bytes[15], bytes[16], bytes[17], bytes[18]});
        assertEquals(0, bytes[19]);
    }

    @Test
    void expandsCanonicalJointIndicesToExactGpuFloats() {
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        ModelVertexAttribute positions = new ModelVertexAttribute(ModelAttributeSemantic.POSITION,
                ModelComponentType.FLOAT32, 3, false, 3, floatBytes(0, 0, 0, 1, 0, 0, 0, 1, 0));
        ByteBuffer jointBytes = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        for (short value : new short[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}) jointBytes.putShort(value);
        ModelVertexAttribute joints = new ModelVertexAttribute(ModelAttributeSemantic.JOINTS_0,
                ModelComponentType.UINT16, 4, false, 3, jointBytes.array());
        ModelVertexAttribute weights = new ModelVertexAttribute(ModelAttributeSemantic.WEIGHTS_0,
                ModelComponentType.FLOAT32, 4, false, 3,
                floatBytes(1, 0, 0, 0, 0.5F, 0.5F, 0, 0, 0.25F, 0.25F, 0.25F, 0.25F));
        ModelPrimitive primitive = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES,
                Map.of(ModelAttributeSemantic.POSITION, positions, ModelAttributeSemantic.JOINTS_0, joints,
                        ModelAttributeSemantic.WEIGHTS_0, weights), indices(), 0, bounds);
        ModelDefinition definition = definition(List.of(new ModelMesh("mesh", List.of(primitive), bounds)),
                List.of(node(0, bounds)), bounds, List.of());

        ModelGpuLayoutGroupPlan group = new ModelGpuUploadPlanner().plan(definition, List.of()).layoutGroups().getFirst();
        ByteBuffer gpu = ByteBuffer.wrap(group.vertexData()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(44, group.vertexStride());
        assertEquals(0.0F, gpu.getFloat(12));
        assertEquals(1.0F, gpu.getFloat(16));
        assertEquals(2.0F, gpu.getFloat(20));
        assertEquals(3.0F, gpu.getFloat(24));
        assertEquals(1.0F, gpu.getFloat(28));
        assertEquals(4.0F, gpu.getFloat(44 + 12));
    }

    @Test
    void repeatedAcquireSharesOneUploadAndLastLeaseClosesIt() {
        FakeDevice device = new FakeDevice();
        ImmediateRenderThread renderThread = new ImmediateRenderThread();
        ModelGpuRepository repository = new ModelGpuRepository(device, renderThread);
        ModelGpuUploadPlan plan = new ModelGpuUploadPlanner().plan(twoTriangleModel(), List.of());

        ModelGpuLease first = repository.acquire(plan).join();
        ModelGpuLease second = repository.acquire(plan).join();

        assertSame(first.resource(), second.resource());
        assertEquals(2, device.createdBuffers.size());
        assertEquals(1, repository.cachedResourceCount());
        first.close();
        assertTrue(device.createdBuffers.stream().noneMatch(FakeBuffer::isClosed));
        second.close();
        assertTrue(device.createdBuffers.stream().allMatch(FakeBuffer::isClosed));
        assertEquals(0, repository.cachedResourceCount());
    }

    @Test
    void drawStateChangesDoNotCreateOrUploadGeometry() {
        FakeDevice device = new FakeDevice();
        ModelGpuRepository repository = new ModelGpuRepository(device, new ImmediateRenderThread());
        ModelGpuLease lease = repository.acquire(new ModelGpuUploadPlanner().plan(twoTriangleModel(), List.of())).join();
        int uploadCount = device.createdBuffers.size();

        new ModelGpuDrawState(identity(), 0, 1, 1, 1, 1);
        new ModelGpuDrawState(identity(), 0x00F000F0, 0.5F, 0.25F, 1, 0.75F);

        assertEquals(uploadCount, device.createdBuffers.size());
        lease.close();
    }

    @Test
    void imageDecodeRunsOnTheProvidedWorkerBeforePlanning() {
        AtomicReference<Thread> decoderThread = new AtomicReference<>();
        Executor worker = task -> {
            Thread thread = new Thread(task, "model-image-worker");
            thread.start();
            try { thread.join(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        };
        ModelDefinition base = oneTexturedTriangleModel();
        ModelTextureInfo textureInfo = new ModelTextureInfo(0, ModelTextureTransform.identity());
        ModelDefinition definition = new ModelDefinition(base.source(), base.scenes(), base.defaultScene(),
                base.nodes(), base.meshes(),
                List.of(new ModelMaterial("textured", 1, 1, 1, 1, textureInfo,
                        ModelAlphaMode.OPAQUE, 0.5F, false, 0, 0, 0, ModelTextureInfo.absent())),
                List.of(new ModelTexture("texture", 0)),
                List.of(new ModelImageSource("image/png", 1, 1, new byte[]{1})),
                base.animations(), base.bounds());
        ModelGpuPreparationService service = new ModelGpuPreparationService(worker, ignored -> {
            decoderThread.set(Thread.currentThread());
            return new DecodedModelImage(1, 1, new byte[]{1, 2, 3, 4});
        });

        ModelGpuUploadPlan plan = service.prepare(definition).join();

        assertEquals("model-image-worker", decoderThread.get().getName());
        assertEquals(1, plan.images().size());
        assertEquals(0, plan.images().getFirst().base().rgba()[0],
                "color linearization must finish before the render-thread upload boundary");
    }

    @Test
    void texturePlansCarryExplicitColorUsageAndSafeRectangularMips() {
        ModelDefinition definition = texturedDefinition(new ModelTextureSampler(
                ModelTextureWrap.REPEAT, ModelTextureWrap.REPEAT,
                ModelTextureFilter.LINEAR_MIPMAP_LINEAR, ModelTextureFilter.LINEAR));
        byte[] pixels = new byte[8 * 2 * 4];
        java.util.Arrays.fill(pixels, (byte) 255);

        ModelGpuImagePlan image = new ModelGpuUploadPlanner().plan(definition,
                List.of(new DecodedModelImage(8, 2, pixels))).images().getFirst();

        assertEquals(0, image.imageIndex());
        assertEquals(ModelTextureColorSpace.SRGB_COLOR, image.key().colorSpace());
        assertEquals(List.of(8, 4), image.levels().stream().map(DecodedModelImage::width).toList());
        assertEquals(List.of(2, 1), image.levels().stream().map(DecodedModelImage::height).toList());
    }

    @Test
    void cancelledQueuedUploadLeavesNoPendingOrLiveDiagnosticResource() {
        FakeDevice device = new FakeDevice();
        QueuedRenderThread render = new QueuedRenderThread();
        ModelGpuRepository repository = new ModelGpuRepository(device, render);
        CompletableFuture<ModelGpuLease> acquire = repository.acquire(
                new ModelGpuUploadPlanner().plan(twoTriangleModel(), List.of()));

        assertEquals(1, repository.diagnostics().pendingUploads());
        repository.close();
        render.drain();

        assertTrue(acquire.isCompletedExceptionally());
        assertEquals(0, repository.diagnostics().pendingUploads());
        assertEquals(0, repository.diagnostics().liveResources());
        assertEquals(1, repository.diagnostics().cancelledUploads());
    }

    private static ModelDefinition twoTriangleModel() {
        ModelPrimitive first = primitive(floatBytes(0, 0, 0, 1, 0, 0, 0, 1, 0));
        ModelPrimitive second = primitive(floatBytes(2, 0, 0, 3, 0, 0, 2, 1, 0));
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(3, 1, 0));
        List<ModelMesh> meshes = List.of(new ModelMesh("first", List.of(first), first.bounds()),
                new ModelMesh("second", List.of(second), second.bounds()));
        List<ModelNode> nodes = List.of(node(0, first.bounds()), node(1, second.bounds()));
        return definition(meshes, nodes, bounds, List.of());
    }

    private static ModelDefinition oneTexturedTriangleModel() {
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        ModelVertexAttribute positions = new ModelVertexAttribute(ModelAttributeSemantic.POSITION,
                ModelComponentType.FLOAT32, 3, false, 3, floatBytes(0, 0, 0, 1, 0, 0, 0, 1, 0));
        ModelVertexAttribute uv = new ModelVertexAttribute(ModelAttributeSemantic.TEXCOORD_0,
                ModelComponentType.FLOAT32, 2, false, 3, floatBytes(0.25F, 0.75F, 1, 0, 0, 1));
        ModelPrimitive primitive = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES,
                Map.of(ModelAttributeSemantic.POSITION, positions, ModelAttributeSemantic.TEXCOORD_0, uv),
                indices(), 0, bounds);
        return definition(List.of(new ModelMesh("mesh", List.of(primitive), bounds)), List.of(node(0, bounds)), bounds, List.of());
    }

    private static ModelDefinition modelWithMaterial(ModelPrimitive primitive, ModelBounds bounds,
                                                     ModelMaterial material) {
        return new ModelDefinition(asset(), List.of(new ModelScene("default", List.of(0), Optional.of(bounds))), 0,
                List.of(node(0, bounds)), List.of(new ModelMesh("mesh", List.of(primitive), bounds)),
                List.of(material), material.baseColorTexture().textureIndex() < 0
                        ? List.of() : List.of(new ModelTexture("texture", 0, ModelTextureSampler.gltfDefault())),
                material.baseColorTexture().textureIndex() < 0
                        ? List.of() : List.of(new ModelImageSource("image/png", 1, 1, new byte[]{1})),
                List.of(), bounds);
    }

    private static ModelPrimitive primitive(byte[] positions) {
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        return new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES,
                Map.of(ModelAttributeSemantic.POSITION, new ModelVertexAttribute(ModelAttributeSemantic.POSITION,
                        ModelComponentType.FLOAT32, 3, false, 3, positions)), indices(), 0, bounds);
    }

    private static ModelNode node(int mesh, ModelBounds bounds) {
        return new ModelNode("node", ModelTransform.Trs.IDENTITY, mesh, List.of(), Optional.of(bounds));
    }

    private static ModelDefinition definition(List<ModelMesh> meshes, List<ModelNode> nodes,
                                              ModelBounds bounds, List<ModelImageSource> images) {
        return new ModelDefinition(asset(), List.of(new ModelScene("default", List.of(0, 1).subList(0, nodes.size()), Optional.of(bounds))),
                0, nodes, meshes, List.of(ModelMaterial.defaultMaterial()), List.of(), images, List.of(), bounds);
    }

    private static ModelDefinition withImages(ModelDefinition definition, List<ModelImageSource> images) {
        return new ModelDefinition(definition.source(), definition.scenes(), definition.defaultScene(), definition.nodes(),
                definition.meshes(), definition.materials(), definition.textures(), images, definition.animations(), definition.bounds());
    }

    private static ModelDefinition texturedDefinition(ModelTextureSampler sampler) {
        ModelDefinition base = oneTexturedTriangleModel();
        ModelTextureInfo info = new ModelTextureInfo(0, ModelTextureTransform.identity());
        ModelMaterial material = new ModelMaterial("textured", 1, 1, 1, 1, info,
                ModelAlphaMode.OPAQUE, 0.5F, false, 0, 0, 0, ModelTextureInfo.absent());
        return new ModelDefinition(base.source(), base.scenes(), base.defaultScene(), base.nodes(), base.meshes(),
                List.of(material), List.of(new ModelTexture("texture", 0, sampler)),
                List.of(new ModelImageSource("image/png", 8, 2, new byte[]{1})),
                base.animations(), base.bounds());
    }

    private static ModelAssetReference asset() {
        return new ModelAssetReference(ModelSourceKind.MEMORY, "test", "m3-triangle",
                new ModelAssetRevision(1, 0, ""));
    }

    private static ModelIndexBuffer indices() {
        return new ModelIndexBuffer(ModelComponentType.UINT16, 3, new byte[]{0, 0, 1, 0, 2, 0});
    }

    private static byte[] floatBytes(float... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) buffer.putFloat(value);
        return buffer.array();
    }

    private static long[] uint32Values(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long[] values = new long[bytes.length / 4];
        for (int index = 0; index < values.length; index++) values[index] = Integer.toUnsignedLong(buffer.getInt());
        return values;
    }

    private static ModelMatrix4 identity() {
        return new ModelMatrix4(new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
    }

    private static final class ImmediateRenderThread implements RenderThreadDispatcher {
        private boolean executing;
        @Override public boolean isRenderThread() { return executing; }
        @Override public void execute(Runnable task) {
            boolean previous = executing;
            executing = true;
            try { task.run(); } finally { executing = previous; }
        }
    }

    private static final class QueuedRenderThread implements RenderThreadDispatcher {
        private final java.util.ArrayDeque<Runnable> tasks = new java.util.ArrayDeque<>();
        private boolean executing;
        @Override public boolean isRenderThread() { return executing; }
        @Override public void execute(Runnable task) { tasks.addLast(task); }
        void drain() {
            while (!tasks.isEmpty()) {
                executing = true;
                try { tasks.removeFirst().run(); } finally { executing = false; }
            }
        }
    }

    private static final class FakeDevice implements ModelGpuDevice {
        private final List<FakeBuffer> createdBuffers = new ArrayList<>();
        @Override public ModelGpuBuffer createBuffer(String label, ModelGpuBufferKind kind, byte[] data) {
            FakeBuffer buffer = new FakeBuffer(data.length);
            createdBuffers.add(buffer);
            return buffer;
        }
        @Override public ModelGpuTexture createTexture(String label, ModelGpuImagePlan image) {
            return new FakeTexture(image.base().width(), image.base().height());
        }
    }

    private static final class FakeBuffer implements ModelGpuBuffer {
        private final int byteSize;
        private boolean closed;
        private FakeBuffer(int byteSize) { this.byteSize = byteSize; }
        @Override public int byteSize() { return byteSize; }
        @Override public boolean isClosed() { return closed; }
        @Override public void close() { closed = true; }
    }

    private static final class FakeTexture implements ModelGpuTexture {
        private final int width;
        private final int height;
        private boolean closed;
        private FakeTexture(int width, int height) { this.width = width; this.height = height; }
        @Override public int width() { return width; }
        @Override public int height() { return height; }
        @Override public boolean isClosed() { return closed; }
        @Override public void close() { closed = true; }
    }
}
