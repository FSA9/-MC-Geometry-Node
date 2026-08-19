package com.mine.geometry_node.client.model.render.backend.host.light.asset;

import com.mine.geometry_node.client.model.render.backend.host.geometry.HostCanonicalPrimitive;
import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HostPreparedLightingAssetTest {
    @Test
    void preservesGeometricAndShadingNormalsAcrossNegativeNonUniformScale() {
        HostCanonicalPrimitive primitive = primitive(
                new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0},
                new float[]{0, 0, 1, 0, 0, 1, 0, 0, 1}, new int[]{0, 1, 2},
                List.of(occurrence(3, -1, new Matrix4f().scale(-2, 3, 1))));
        HostLightingAssetBudget budget = new HostLightingAssetBudget(1 << 20, 1 << 20);

        try (HostPreparedLightingAsset asset = HostPreparedLightingAsset.prepare(List.of(primitive),
                ignored -> material(ModelAlphaMode.OPAQUE), parameters(), budget)) {
            assertEquals(HostPreparedLightingAsset.Status.READY, asset.status(), asset.detail());
            HostLightingSurface surface = asset.surfaces().getFirst();
            assertEquals(-1F, surface.geometricNormal(0, 2), 1.0e-6F);
            assertEquals(1F, surface.shadingNormal(0, 2), 1.0e-6F);
            assertTrue(surface.material().opticalDoubleSided());
        }
        assertEquals(0, budget.diagnostics().residentBytes());
    }

    @Test
    void sharedMeshOccurrencesRemainSeparateReceiverSurfaces() {
        HostCanonicalPrimitive primitive = primitive(
                new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}, null, new int[]{0, 1, 2},
                List.of(occurrence(1, -1, new Matrix4f()),
                        occurrence(2, -1, new Matrix4f().translation(5, 0, 0))));

        try (HostPreparedLightingAsset asset = prepare(primitive, ModelAlphaMode.OPAQUE)) {
            assertEquals(2, asset.surfaces().size());
            assertEquals(0F, asset.surfaces().get(0).position(0, 0));
            assertEquals(5F, asset.surfaces().get(1).position(0, 0));
            assertNotEquals(asset.surfaces().get(0).identity(), asset.surfaces().get(1).identity());
            assertEquals(2, asset.receiverProbes().size());
            assertEquals(2, asset.receiverProbes().sourceTriangles());
            assertEquals(0, asset.receiverProbes().surfaceIndex(0));
            assertEquals(1, asset.receiverProbes().surfaceIndex(1));
        }
    }

    @Test
    void receiverProbeSelectionIsBoundedAndDeterministic() {
        HostCanonicalPrimitive primitive = primitive(
                new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0, 2, 0, 0, 2, 1, 0}, null,
                new int[]{0, 1, 2, 1, 3, 4}, List.of(occurrence(0, -1, new Matrix4f())));
        HostLightingGeometryParameters bounded = new HostLightingGeometryParameters(
                2, 32, 262_144, 1_000_000, 1.0e-18F, 1.0e-5F, 1);
        try (HostPreparedLightingAsset asset = HostPreparedLightingAsset.prepare(List.of(primitive),
                ignored -> material(ModelAlphaMode.OPAQUE), bounded,
                new HostLightingAssetBudget(1 << 20, 1 << 20))) {
            assertTrue(asset.ready(), asset.detail());
            assertEquals(2, asset.receiverProbes().sourceTriangles());
            assertEquals(1, asset.receiverProbes().size());
            assertTrue(asset.receiverProbes().sampled());
            assertEquals(1F / 3F, asset.receiverProbes().position(0, 0), 1.0e-6F);
        }
    }

    @Test
    void exactBvhAndConservativeVoxelPreserveWallAndOpenSpace() {
        HostCanonicalPrimitive wall = primitive(
                new float[]{0, -1, -1, 0, 1, -1, 0, 1, 1, 0, -1, 1}, null,
                new int[]{0, 1, 2, 0, 2, 3}, List.of(occurrence(0, -1, new Matrix4f())));

        try (HostPreparedLightingAsset asset = prepare(wall, ModelAlphaMode.OPAQUE)) {
            assertTrue(asset.bvh().blocked(-1, 0, 0, 1, 0, 0));
            assertFalse(asset.bvh().blocked(-1, 2, 0, 1, 2, 0));
            assertTrue(asset.voxelGrid().occupiedAt(0, 0, 0));
            assertFalse(asset.voxelGrid().occupiedAt(0, 2, 0));
            assertEquals(2, asset.diagnostics().opaqueTriangles());
        }
    }

    @Test
    void closedBoxBlocksExitWhileDoorwayRemainsOpen() {
        HostCanonicalPrimitive box = primitive(
                new float[]{-1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1,
                        -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1}, null,
                new int[]{0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7,
                        0, 1, 5, 0, 5, 4, 3, 7, 6, 3, 6, 2,
                        0, 4, 7, 0, 7, 3, 1, 2, 6, 1, 6, 5},
                List.of(occurrence(0, -1, new Matrix4f())));
        try (HostPreparedLightingAsset asset = prepare(box, ModelAlphaMode.OPAQUE)) {
            assertEquals(HostPreparedLightingAsset.Status.READY, asset.status(), asset.detail());
            assertTrue(asset.bvh().blocked(0, 0, 0, 2, 0, 0));
            assertTrue(asset.bvh().blocked(-2, 0, 0, 2, 0, 0));
        }

        List<Float> positions = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        quad(positions, indices, -2, 2, -2, -0.5F);
        quad(positions, indices, -2, 2, 0.5F, 2);
        quad(positions, indices, -2, -0.5F, -0.5F, 0.5F);
        quad(positions, indices, 0.5F, 2, -0.5F, 0.5F);
        HostCanonicalPrimitive doorway = primitive(toArray(positions), null,
                indices.stream().mapToInt(Integer::intValue).toArray(),
                List.of(occurrence(0, -1, new Matrix4f())));
        try (HostPreparedLightingAsset asset = prepare(doorway, ModelAlphaMode.OPAQUE)) {
            assertFalse(asset.bvh().blocked(-1, 0, 0, 1, 0, 0));
            assertTrue(asset.bvh().blocked(-1, 1, 0, 1, 1, 0));
            assertFalse(asset.voxelGrid().occupiedAt(0, 0, 0));
        }
    }

    @Test
    void maskAndBlendStayTransmissiveUntilCoverageContractsExist() {
        HostCanonicalPrimitive triangle = primitive(
                new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}, null, new int[]{0, 1, 2},
                List.of(occurrence(0, -1, new Matrix4f())));

        try (HostPreparedLightingAsset mask = prepare(triangle, ModelAlphaMode.MASK);
             HostPreparedLightingAsset blend = prepare(triangle, ModelAlphaMode.BLEND)) {
            assertEquals(HostLightingMaterial.MaskCoverage.PENDING,
                    mask.surfaces().getFirst().material().maskCoverage());
            assertEquals(0, mask.bvh().triangleCount());
            assertEquals(0, blend.bvh().triangleCount());
            assertFalse(mask.bvh().blocked(-1, 0.2F, 0, 1, 0.2F, 0));
        }
    }

    @Test
    void skinnedAndOverBudgetAssetsFailClosedWithoutPartialGeometry() {
        HostCanonicalPrimitive skinned = primitive(
                new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}, null, new int[]{0, 1, 2},
                List.of(occurrence(0, 2, new Matrix4f())));
        HostPreparedLightingAsset unsupported = HostPreparedLightingAsset.prepare(List.of(skinned),
                ignored -> material(ModelAlphaMode.OPAQUE));
        assertEquals(HostPreparedLightingAsset.Status.SKINNED_UNSUPPORTED, unsupported.status());
        assertTrue(unsupported.surfaces().isEmpty());

        HostLightingAssetBudget budget = new HostLightingAssetBudget(16, 16);
        HostCanonicalPrimitive rigid = primitive(
                new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}, null, new int[]{0, 1, 2},
                List.of(occurrence(0, -1, new Matrix4f())));
        HostPreparedLightingAsset rejected = HostPreparedLightingAsset.prepare(List.of(rigid),
                ignored -> material(ModelAlphaMode.OPAQUE), parameters(), budget);
        assertEquals(HostPreparedLightingAsset.Status.BUDGET_REJECTED, rejected.status());
        assertTrue(rejected.surfaces().isEmpty());
        assertEquals(1, budget.diagnostics().rejected());
    }

    @Test
    void degenerateTrianglesAreReceiversButNeverOccluders() {
        HostCanonicalPrimitive primitive = primitive(
                new float[]{0, 0, 0, 1, 0, 0, 2, 0, 0}, null, new int[]{0, 1, 2},
                List.of(occurrence(0, -1, new Matrix4f())));
        try (HostPreparedLightingAsset asset = prepare(primitive, ModelAlphaMode.OPAQUE)) {
            assertEquals(1, asset.diagnostics().degenerateTriangles());
            assertEquals(0, asset.bvh().triangleCount());
        }
    }

    private static HostPreparedLightingAsset prepare(HostCanonicalPrimitive primitive, ModelAlphaMode alphaMode) {
        return HostPreparedLightingAsset.prepare(List.of(primitive), ignored -> material(alphaMode), parameters(),
                new HostLightingAssetBudget(1 << 20, 1 << 20));
    }

    private static HostLightingGeometryParameters parameters() {
        return new HostLightingGeometryParameters(2, 32, 262_144, 1_000_000, 1.0e-18F, 1.0e-5F);
    }

    private static StaticModelMaterial material(ModelAlphaMode alphaMode) {
        return new StaticModelMaterial(1, 1, 1, alphaMode == ModelAlphaMode.BLEND ? 0.5F : 1F,
                -1, alphaMode, 0.5F, false);
    }

    private static HostCanonicalPrimitive primitive(float[] positions, float[] normals, int[] indices,
                                                     List<HostCanonicalPrimitive.NodeOccurrence> occurrences) {
        Map<ModelAttributeSemantic, ModelVertexAttribute> attributes = new java.util.LinkedHashMap<>();
        attributes.put(ModelAttributeSemantic.POSITION, attribute(ModelAttributeSemantic.POSITION, positions));
        if (normals != null) attributes.put(ModelAttributeSemantic.NORMAL,
                attribute(ModelAttributeSemantic.NORMAL, normals));
        byte[] encodedIndices = new byte[indices.length];
        for (int index = 0; index < indices.length; index++) encodedIndices[index] = (byte) indices[index];
        ModelBounds bounds = bounds(positions);
        ModelPrimitive source = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES, attributes,
                new ModelIndexBuffer(ModelComponentType.UINT8, indices.length, encodedIndices), 0, bounds);
        return HostCanonicalPrimitive.from(0, 0, source, occurrences);
    }

    private static ModelVertexAttribute attribute(ModelAttributeSemantic semantic, float[] values) {
        ByteBuffer data = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) data.putFloat(value);
        return new ModelVertexAttribute(semantic, ModelComponentType.FLOAT32, 3, false,
                values.length / 3, data.array());
    }

    private static ModelBounds bounds(float[] positions) {
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int offset = 0; offset < positions.length; offset += 3) {
            minX = Math.min(minX, positions[offset]); minY = Math.min(minY, positions[offset + 1]);
            minZ = Math.min(minZ, positions[offset + 2]); maxX = Math.max(maxX, positions[offset]);
            maxY = Math.max(maxY, positions[offset + 1]); maxZ = Math.max(maxZ, positions[offset + 2]);
        }
        return new ModelBounds(new ModelVector3(minX, minY, minZ), new ModelVector3(maxX, maxY, maxZ));
    }

    private static HostCanonicalPrimitive.NodeOccurrence occurrence(int node, int skin, Matrix4f transform) {
        return new HostCanonicalPrimitive.NodeOccurrence(node, skin, transform,
                new ModelBounds(new ModelVector3(-10, -10, -10), new ModelVector3(10, 10, 10)));
    }

    private static void quad(List<Float> positions, List<Integer> indices,
                             float minY, float maxY, float minZ, float maxZ) {
        int base = positions.size() / 3;
        for (float[] vertex : new float[][]{{0, minY, minZ}, {0, maxY, minZ},
                {0, maxY, maxZ}, {0, minY, maxZ}}) {
            positions.add(vertex[0]); positions.add(vertex[1]); positions.add(vertex[2]);
        }
        for (int index : new int[]{0, 1, 2, 0, 2, 3}) indices.add(base + index);
    }

    private static float[] toArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int index = 0; index < result.length; index++) result[index] = values.get(index);
        return result;
    }
}
