package com.mine.geometry_node.core.engine.system.model;

import com.mine.geometry_node.core.engine.system.model.identity.*;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.importer.*;
import com.mine.geometry_node.core.engine.system.model.importer.protocol.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ModelM1ContractTest {
    private static final String IMPORTER_ID = "geometry_node:memory_triangle";

    @Test
    void importsProgrammaticTriangleThroughTheRegistry() {
        ModelImportSource source = source();
        ModelImporterRegistry registry = registry(input -> triangle(input.asset(), validNodes(), new int[]{0, 1, 2}));

        ModelImportResult result = registry.importModel(IMPORTER_ID, source, ModelImportContext.defaults());

        ModelImportResult.Success success = assertInstanceOf(ModelImportResult.Success.class, result);
        assertEquals(1, success.definition().meshes().size());
        assertEquals(1, success.definition().meshes().getFirst().primitives().getFirst().triangleCount());
        assertEquals(List.of(IMPORTER_ID), registry.registeredIds());
    }

    @Test
    void publishedDataDefensivelyCopiesMutableInputs() {
        byte[] positionBytes = floatBytes(0, 0, 0, 1, 0, 0, 0, 1, 0);
        ModelVertexAttribute attribute = new ModelVertexAttribute(ModelAttributeSemantic.POSITION,
                ModelComponentType.FLOAT32, 3, false, 3, positionBytes);
        positionBytes[0] = 99;

        byte[] firstRead = attribute.data();
        firstRead[0] = 77;
        ByteBuffer readOnly = attribute.readOnlyData();

        assertEquals(0, attribute.data()[0]);
        assertTrue(readOnly.isReadOnly());
        assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, (byte) 1));
    }

    @Test
    void rejectsCyclicNodeHierarchy() {
        List<ModelNode> nodes = List.of(
                new ModelNode("a", ModelTransform.Trs.IDENTITY, 0, List.of(1), Optional.empty()),
                new ModelNode("b", ModelTransform.Trs.IDENTITY, -1, List.of(0), Optional.empty()));
        ModelImportResult result = registry(input -> triangle(input.asset(), nodes, new int[]{0, 1, 2}))
                .importModel(IMPORTER_ID, source(), ModelImportContext.defaults());

        ModelImportResult.Failure failure = assertInstanceOf(ModelImportResult.Failure.class, result);
        assertEquals(ModelImportErrorCode.INVALID_HIERARCHY, failure.failure().code());
    }

    @Test
    void rejectsOutOfRangeIndices() {
        ModelImportResult result = registry(input -> triangle(input.asset(), validNodes(), new int[]{0, 1, 3}))
                .importModel(IMPORTER_ID, source(), ModelImportContext.defaults());

        ModelImportResult.Failure failure = assertInstanceOf(ModelImportResult.Failure.class, result);
        assertEquals(ModelImportErrorCode.INVALID_INDEX, failure.failure().code());
        assertEquals(3L, failure.failure().actualValue());
    }

    @Test
    void publicationGateRejectsNonFiniteCanonicalAttributes() {
        ModelImportResult result = registry(input -> triangleWithPositions(input.asset(),
                        floatBytes(0, 0, 0, Float.NaN, 0, 0, 0, 1, 0)))
                .importModel(IMPORTER_ID, source(), ModelImportContext.defaults());

        ModelImportResult.Failure failure = assertInstanceOf(ModelImportResult.Failure.class, result);
        assertEquals(ModelImportErrorCode.INVALID_ATTRIBUTE, failure.failure().code());
        assertTrue(failure.failure().location().contains("POSITION"));
    }

    @Test
    void rejectsModelsThatExceedTheSharedBudget() {
        ModelImportBudget defaults = ModelImportBudget.DEFAULT;
        ModelImportBudget twoVertices = new ModelImportBudget(
                defaults.maxSourceBytes(), defaults.maxBufferViews(), defaults.maxAccessors(),
                defaults.maxScenes(), defaults.maxNodes(), defaults.maxNodeDepth(),
                defaults.maxMeshes(), defaults.maxPrimitives(), 2L, defaults.maxIndices(), defaults.maxTriangles(),
                defaults.maxMaterials(), defaults.maxTextures(), defaults.maxImages(), defaults.maxImageDimension(),
                defaults.maxEncodedImageBytes(), defaults.maxDecodedImageBytes(),
                defaults.maxAnimations(), defaults.maxAnimationChannels(), defaults.maxAnimationKeyframes(),
                defaults.maxAttributeBytes());
        ModelImportContext context = new ModelImportContext(twoVertices, ModelCancellationToken.NONE, null);

        ModelImportResult result = registry(input -> triangle(input.asset(), validNodes(), new int[]{0, 1, 2}))
                .importModel(IMPORTER_ID, source(), context);

        ModelImportResult.Failure failure = assertInstanceOf(ModelImportResult.Failure.class, result);
        assertEquals(ModelImportErrorCode.LIMIT_EXCEEDED, failure.failure().code());
        assertEquals("vertices", failure.failure().location());
    }

    @Test
    void localPreviewBudgetIsBoundedButCoversHighComplexityLocalAssets() {
        ModelImportBudget preview = ModelImportBudget.LOCAL_PREVIEW;
        assertEquals(512L << 20, preview.maxSourceBytes());
        assertTrue(preview.maxVertices() >= 6_651_810L);
        assertTrue(preview.maxIndices() >= 34_203_516L);
        assertTrue(preview.maxTriangles() >= 11_401_172L);
        assertTrue(ModelImportBudget.DEFAULT.maxSourceBytes() < preview.maxSourceBytes());
    }

    @Test
    void observesCancellationBeforeInvokingTheImporter() {
        ModelCancellationSource cancellation = new ModelCancellationSource();
        cancellation.cancel();
        boolean[] invoked = {false};
        ModelImporterRegistry registry = registry(input -> {
            invoked[0] = true;
            return triangle(input.asset(), validNodes(), new int[]{0, 1, 2});
        });

        ModelImportResult result = registry.importModel(IMPORTER_ID, source(),
                new ModelImportContext(ModelImportBudget.DEFAULT, cancellation.token(), null));

        ModelImportResult.Failure failure = assertInstanceOf(ModelImportResult.Failure.class, result);
        assertEquals(ModelImportErrorCode.CANCELLED, failure.failure().code());
        assertFalse(invoked[0]);
    }

    @Test
    void importerCanClaimBudgetBeforeAllocating() {
        ModelImporterRegistry registry = new ModelImporterRegistry();
        registry.register(new ModelImporter() {
            @Override public String id() { return IMPORTER_ID; }

            @Override
            public ModelDefinition importModel(ModelImportSource source, ModelImportSession session)
                    throws ModelImportException {
                session.budgetTracker().claim(ModelBudgetResource.VERTICES,
                        session.budget().maxVertices() + 1L, "fixture.vertices");
                fail("budget claim should have failed");
                return null;
            }
        });

        ModelImportResult result = registry.importModel(IMPORTER_ID, source(), ModelImportContext.defaults());

        ModelImportResult.Failure failure = assertInstanceOf(ModelImportResult.Failure.class, result);
        assertEquals(ModelImportErrorCode.LIMIT_EXCEEDED, failure.failure().code());
        assertEquals("fixture.vertices", failure.failure().location());
    }

    private static ModelImporterRegistry registry(Function<ModelImportSource, ModelDefinition> factory) {
        ModelImporterRegistry registry = new ModelImporterRegistry();
        registry.register(new ModelImporter() {
            @Override public String id() { return IMPORTER_ID; }
            @Override public ModelDefinition importModel(ModelImportSource source, ModelImportSession session) {
                return factory.apply(source);
            }
        });
        return registry;
    }

    private static ModelImportSource source() {
        byte[] content = {1};
        ModelAssetReference asset = new ModelAssetReference(ModelSourceKind.MEMORY, "test", "triangle.memory",
                new ModelAssetRevision(content.length, 0L, ""));
        return new ModelImportSource(asset, content);
    }

    private static List<ModelNode> validNodes() {
        return List.of(
                new ModelNode("root", ModelTransform.Trs.IDENTITY, -1, List.of(1), Optional.empty()),
                new ModelNode("mesh", ModelTransform.Trs.IDENTITY, 0, List.of(), Optional.of(
                        new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0)))));
    }

    private static ModelDefinition triangle(ModelAssetReference source, List<ModelNode> nodes, int[] indices) {
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        ModelVertexAttribute positions = new ModelVertexAttribute(ModelAttributeSemantic.POSITION,
                ModelComponentType.FLOAT32, 3, false, 3,
                floatBytes(0, 0, 0, 1, 0, 0, 0, 1, 0));
        ModelPrimitive primitive = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES,
                Map.of(ModelAttributeSemantic.POSITION, positions), unsignedShortIndices(indices), 0, bounds);
        return new ModelDefinition(source, List.of(new ModelScene("default", List.of(0), Optional.of(bounds))), 0,
                nodes, List.of(new ModelMesh("triangle", List.of(primitive), bounds)),
                List.of(ModelMaterial.defaultMaterial()), List.of(), List.of(), List.of(), bounds);
    }

    private static ModelDefinition triangleWithPositions(ModelAssetReference source, byte[] positionsData) {
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        ModelVertexAttribute positions = new ModelVertexAttribute(ModelAttributeSemantic.POSITION,
                ModelComponentType.FLOAT32, 3, false, 3, positionsData);
        ModelPrimitive primitive = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES,
                Map.of(ModelAttributeSemantic.POSITION, positions), unsignedShortIndices(new int[]{0, 1, 2}), 0, bounds);
        return new ModelDefinition(source, List.of(new ModelScene("default", List.of(0), Optional.of(bounds))), 0,
                List.of(new ModelNode("mesh", ModelTransform.Trs.IDENTITY, 0, List.of(), Optional.of(bounds))),
                List.of(new ModelMesh("triangle", List.of(primitive), bounds)),
                List.of(ModelMaterial.defaultMaterial()), List.of(), List.of(), List.of(), bounds);
    }

    private static ModelIndexBuffer unsignedShortIndices(int[] indices) {
        ByteBuffer buffer = ByteBuffer.allocate(indices.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int index : indices) buffer.putShort((short) index);
        return new ModelIndexBuffer(ModelComponentType.UINT16, indices.length, buffer.array());
    }

    private static byte[] floatBytes(float... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) buffer.putFloat(value);
        return buffer.array();
    }
}
