package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.gpu.ModelGpuBuffer;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityGeometry;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostGeometryProjector;
import com.mine.geometry_node.client.model.runtime.StaticModelRenderMetadata;
import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.identity.*;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HostPreparedArtifactStaticVariantTest {
    @Test
    void keySeparatesInstancesPoseLightWindingAndLayout() {
        Object instance = new Object();
        Object layout = new Object();
        Matrix4f pose = new Matrix4f().scale(2, 1, 1);
        Matrix3f normal = new Matrix3f().scaling(0.5F, 1, 1);
        HostStaticVariantKey base = new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, layout, 3);

        assertEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(new Object(), 7, pose, normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 8, pose, normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, new Matrix4f(pose).translate(1, 0, 0), normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0, false, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                1, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0x00f000f0, true, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0x00f000f0, false, 0.5F, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, new Object(), 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 3, 2, layout, 3));
    }

    @Test
    void publishesAtMostFourVariantsAndUsesAccessOrderForEviction() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        Object instance = new Object();
        Object layout = new Object();
        long generation = artifact.staticGeneration();
        HostStaticVariantKey[] keys = new HostStaticVariantKey[5];
        HostStaticGeometryVariant[] variants = new HostStaticGeometryVariant[5];
        for (int index = 0; index < 4; index++) {
            keys[index] = key(instance, layout, index);
            variants[index] = variant(artifact, 8);
            assertTrue(artifact.publishStaticVariant(geometry, keys[index], generation, variants[index]).published());
        }

        assertSame(variants[0], artifact.staticVariant(geometry, keys[0], generation));
        keys[4] = key(instance, layout, 4);
        variants[4] = variant(artifact, 8);
        HostPreparedArtifact.StaticVariantPublication publication =
                artifact.publishStaticVariant(geometry, keys[4], generation, variants[4]);

        assertTrue(publication.published());
        assertEquals(List.of(variants[1]), publication.retired());
        assertSame(variants[0], artifact.staticVariant(geometry, keys[0], generation));
        assertNull(artifact.staticVariant(geometry, keys[1], generation));
        publication.retired().forEach(HostStaticGeometryVariant::close);
        artifact.closeStaticVariants();
        for (HostStaticGeometryVariant variant : variants) assertTrue(variant.isClosed());
    }

    @Test
    void staleAndDuplicatePublicationReturnUnownedCandidateForFencedRetirement() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        HostStaticVariantKey key = key(new Object(), new Object(), 1);
        long generation = artifact.staticGeneration();
        HostStaticGeometryVariant first = variant(artifact, 8);
        assertTrue(artifact.publishStaticVariant(geometry, key, generation, first).published());

        HostStaticGeometryVariant duplicate = variant(artifact, 8);
        HostPreparedArtifact.StaticVariantPublication duplicateResult =
                artifact.publishStaticVariant(geometry, key, generation, duplicate);
        assertFalse(duplicateResult.published());
        assertEquals(List.of(duplicate), duplicateResult.retired());

        List<HostStaticGeometryVariant> detached = artifact.detachStaticVariants();
        HostStaticGeometryVariant stale = variant(artifact, 8);
        HostPreparedArtifact.StaticVariantPublication staleResult =
                artifact.publishStaticVariant(geometry, key, generation, stale);
        assertFalse(staleResult.published());
        assertEquals(List.of(stale), staleResult.retired());

        duplicateResult.retired().forEach(HostStaticGeometryVariant::close);
        staleResult.retired().forEach(HostStaticGeometryVariant::close);
        detached.forEach(HostStaticGeometryVariant::close);
        artifact.closeStaticVariants();
    }

    @Test
    void variantCloseReleasesBuffersAndReservationExactlyOnce() {
        HostPreparedArtifact artifact = artifact();
        long before = HostStaticVariantBudget.INSTANCE.artifactBytes(artifact);
        FakeBuffer vertices = new FakeBuffer(5);
        FakeBuffer indices = new FakeBuffer(3);
        HostStaticVariantBudget.Reservation reservation = artifact.reserveStaticVariant(8);
        assertNotNull(reservation);
        HostStaticGeometryVariant variant = new HostStaticGeometryVariant(vertices, indices, 4, 6, reservation);
        assertEquals(before + 8, HostStaticVariantBudget.INSTANCE.artifactBytes(artifact));

        variant.close();
        variant.close();

        assertEquals(1, vertices.closeCount);
        assertEquals(1, indices.closeCount);
        assertEquals(before, HostStaticVariantBudget.INSTANCE.artifactBytes(artifact));
        artifact.closeStaticVariants();
    }

    @Test
    void lruCanBeDetachedBeforeRetryWhenArtifactBudgetIsFull() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        Object instance = new Object();
        Object layout = new Object();
        HostStaticVariantKey firstKey = key(instance, layout, 1);
        HostStaticVariantKey requestedKey = key(instance, layout, 2);
        long generation = artifact.staticGeneration();
        int fullBudget = Math.toIntExact(HostStaticVariantBudget.PER_ARTIFACT_BYTES);
        HostStaticGeometryVariant first = variant(artifact, fullBudget);
        assertTrue(artifact.publishStaticVariant(geometry, firstKey, generation, first).published());
        assertNull(artifact.reserveStaticVariant(1));

        List<HostStaticGeometryVariant> detached = artifact.detachLeastRecentlyUsedStaticVariant(
                geometry, requestedKey, generation);

        assertEquals(List.of(first), detached);
        assertNull(artifact.staticVariant(geometry, firstKey, generation));
        assertNull(artifact.reserveStaticVariant(1), "reservation remains charged until fenced retirement");
        detached.forEach(HostStaticGeometryVariant::close);
        HostStaticVariantBudget.Reservation retry = artifact.reserveStaticVariant(1);
        assertNotNull(retry);
        retry.close();
        artifact.closeStaticVariants();
    }

    @Test
    void budgetRetryDoesNotRetireAnotherGeometryAndCreateARebuildCycle() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry residentGeometry = geometry();
        HostEntityGeometry requestedGeometry = geometry();
        Object instance = new Object();
        Object layout = new Object();
        long generation = artifact.staticGeneration();
        HostStaticVariantKey residentKey = key(instance, layout, 1);
        HostStaticVariantKey requestedKey = key(instance, layout, 2);
        HostStaticGeometryVariant resident = variant(
                artifact, Math.toIntExact(HostStaticVariantBudget.PER_ARTIFACT_BYTES));
        assertTrue(artifact.publishStaticVariant(
                residentGeometry, residentKey, generation, resident).published());

        List<HostStaticGeometryVariant> detached = artifact.detachStaticVariantForBudget(
                requestedGeometry, requestedKey, generation);

        assertTrue(detached.isEmpty());
        assertSame(resident, artifact.staticVariant(residentGeometry, residentKey, generation));
        artifact.closeStaticVariants();
    }

    private static HostPreparedArtifact artifact() {
        ModelDefinition definition = emptyDefinition();
        return HostPreparedArtifact.prepare(definition, StaticModelRenderMetadata.from(definition));
    }

    private static HostStaticGeometryVariant variant(HostPreparedArtifact artifact, int bytes) {
        HostStaticVariantBudget.Reservation reservation = artifact.reserveStaticVariant(bytes);
        assertNotNull(reservation);
        return new HostStaticGeometryVariant(new FakeBuffer(bytes), null, 4, 6, reservation);
    }

    private static HostStaticVariantKey key(Object instance, Object layout, long revision) {
        return new HostStaticVariantKey(instance, revision, new Matrix4f(), new Matrix3f(),
                0, (int) revision, false, 1, 1, 1, 1, 0, 1, layout, 1);
    }

    private static HostEntityGeometry geometry() {
        Map<ModelAttributeSemantic, ModelVertexAttribute> attributes = new LinkedHashMap<>();
        attributes.put(ModelAttributeSemantic.POSITION, attribute(3, 0, 0, 0, 1, 0, 0, 0, 1, 0));
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        ModelPrimitive primitive = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES, attributes,
                new ModelIndexBuffer(ModelComponentType.UINT8, 3, new byte[]{0, 1, 2}), 0, bounds);
        return HostGeometryProjector.project(primitive, StaticModelTexture.absent());
    }

    private static ModelVertexAttribute attribute(int components, float... values) {
        ByteBuffer data = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) data.putFloat(value);
        return new ModelVertexAttribute(ModelAttributeSemantic.POSITION, ModelComponentType.FLOAT32, components,
                false, values.length / components, data.array());
    }

    private static ModelDefinition emptyDefinition() {
        ModelAssetReference asset = new ModelAssetReference(ModelSourceKind.MEMORY, "test", "static-variant",
                new ModelAssetRevision(1, 0, ""));
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, ModelVector3.ONE);
        return new ModelDefinition(asset, List.of(new ModelScene("scene", List.of(0), Optional.of(bounds))), 0,
                List.of(new ModelNode("root", ModelTransform.Trs.IDENTITY, -1, List.of(), Optional.empty())),
                List.of(), List.of(ModelMaterial.defaultMaterial()), List.of(), List.of(), List.of(), bounds);
    }

    private static final class FakeBuffer implements ModelGpuBuffer {
        private final int bytes;
        private int closeCount;

        private FakeBuffer(int bytes) { this.bytes = bytes; }
        @Override public int byteSize() { return bytes; }
        @Override public boolean isClosed() { return closeCount > 0; }
        @Override public void close() { closeCount++; }
    }
}
