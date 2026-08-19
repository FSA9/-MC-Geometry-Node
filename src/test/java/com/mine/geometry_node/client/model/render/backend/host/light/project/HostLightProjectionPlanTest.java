package com.mine.geometry_node.client.model.render.backend.host.light.project;

import com.mine.geometry_node.client.model.render.backend.host.entity.HostDrawPlan;
import com.mine.geometry_node.client.model.render.backend.host.light.asset.HostPreparedLightingAsset;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightBinding;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightFieldIdentity;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostScalarLightField;
import com.mine.geometry_node.client.model.runtime.ModelDimensionId;
import com.mine.geometry_node.client.model.runtime.ModelInstanceId;
import com.mine.geometry_node.client.model.runtime.StaticModelRenderMetadata;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.identity.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HostLightProjectionPlanTest {
    @Test
    void sharedMeshOccurrencesProjectOnlyFromTheirOwnReceiverSurface() {
        ModelDefinition definition = definition();
        StaticModelRenderMetadata metadata = StaticModelRenderMetadata.from(definition);
        HostDrawPlan drawPlan = HostDrawPlan.compile(definition, metadata);

        try (HostPreparedLightingAsset lighting = HostPreparedLightingAsset.prepare(
                drawPlan.canonicalPrimitives(), metadata::material)) {
            assertTrue(lighting.ready(), lighting.detail());
            assertEquals(2, lighting.receiverProbes().size());
            HostLightProjectionPlan projection = HostLightProjectionPlan.build(drawPlan, lighting);
            assertTrue(projection.ready(), projection.detail());
            assertEquals(2, projection.projectedDraws());

            HostScalarLightField field = new HostScalarLightField(
                    new HostLightFieldIdentity(new ModelInstanceId("projection"), "asset", 1,
                            new ModelDimensionId("minecraft:overworld"), 2, 3, 4),
                    new int[]{0x00100010, 0x00200020}, null);
            HostLightBinding first = projection.binding(drawPlan.draws().get(0), field, 0x00D00050);
            HostLightBinding second = projection.binding(drawPlan.draws().get(1), field, 0x00E00060);

            assertEquals(HostLightBinding.Mode.FIELD, first.identity().mode());
            assertEquals(field.identity().fieldId(), first.identity().fieldId());
            assertEquals(0x00D00000, first.identity().constantPackedLight());
            assertEquals(0x00E00000, second.identity().constantPackedLight());
            assertEquals(0x00D00010, first.packedLight(0));
            assertEquals(0x00E00020, second.packedLight(0));
        }
    }

    @Test
    void mismatchedFieldSizeFallsBackWithoutPublishingPartialProjection() {
        ModelDefinition definition = definition();
        StaticModelRenderMetadata metadata = StaticModelRenderMetadata.from(definition);
        HostDrawPlan drawPlan = HostDrawPlan.compile(definition, metadata);

        try (HostPreparedLightingAsset lighting = HostPreparedLightingAsset.prepare(
                drawPlan.canonicalPrimitives(), metadata::material)) {
            HostLightProjectionPlan projection = HostLightProjectionPlan.build(drawPlan, lighting);
            HostScalarLightField incomplete = new HostScalarLightField(
                    new HostLightFieldIdentity(new ModelInstanceId("projection"), "asset", 1,
                            new ModelDimensionId("minecraft:overworld"), 2, 3, 4),
                    new int[]{0x00100010}, null);

            HostLightBinding binding = projection.binding(drawPlan.draws().get(0), incomplete, 0x00300030);
            assertEquals(HostLightBinding.Mode.CONSTANT, binding.identity().mode());
            assertEquals(0x00300030, binding.packedLight(0));
        }
    }

    private static ModelDefinition definition() {
        ModelAssetReference asset = new ModelAssetReference(ModelSourceKind.MEMORY, "test", "light-projection",
                new ModelAssetRevision(1, 0, ""));
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        ByteBuffer positionBytes = ByteBuffer.allocate(9 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) positionBytes.putFloat(value);
        ModelVertexAttribute positions = new ModelVertexAttribute(ModelAttributeSemantic.POSITION,
                ModelComponentType.FLOAT32, 3, false, 3, positionBytes.array());
        ModelPrimitive primitive = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES,
                Map.of(ModelAttributeSemantic.POSITION, positions),
                new ModelIndexBuffer(ModelComponentType.UINT8, 3, new byte[]{0, 1, 2}), 0, bounds);
        ModelMesh mesh = new ModelMesh("mesh", List.of(primitive), bounds);
        List<ModelNode> nodes = List.of(
                new ModelNode("first", ModelTransform.Trs.IDENTITY, 0, List.of(), Optional.of(bounds)),
                new ModelNode("second", new ModelTransform.Trs(new ModelVector3(5, 0, 0),
                        ModelQuaternion.IDENTITY, ModelVector3.ONE), 0, List.of(), Optional.of(bounds)));
        return new ModelDefinition(asset, List.of(new ModelScene("scene", List.of(0, 1), Optional.of(bounds))), 0,
                nodes, List.of(mesh), List.of(ModelMaterial.defaultMaterial()), List.of(), List.of(), List.of(), bounds);
    }
}
