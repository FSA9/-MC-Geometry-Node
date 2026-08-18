package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.runtime.StaticModelRenderMetadata;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.identity.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HostDrawPlanTest {
    @Test
    void compilesSelectedSceneInStableNodeOrderAndSharesProjectedGeometry() {
        ModelDefinition definition = definition();

        HostDrawPlan plan = HostDrawPlan.compile(definition, StaticModelRenderMetadata.from(definition));

        assertEquals(2, plan.draws().size());
        assertEquals(0, plan.draws().get(0).nodeIndex());
        assertEquals(1, plan.draws().get(1).nodeIndex());
        assertSame(plan.draws().get(0).geometry(), plan.draws().get(1).geometry());
        assertSame(plan.draws().get(0).canonicalPrimitive(), plan.draws().get(1).canonicalPrimitive());
        assertNotSame(plan.draws().get(0).modelTransform(), plan.draws().get(0).modelTransform());
        assertNotNull(plan.draws().get(0).modelBounds());
        assertEquals(1, plan.canonicalPrimitives().size());
        assertEquals(new com.mine.geometry_node.client.model.render.backend.host.geometry.HostCanonicalPrimitive.Identity(
                0, 0, 0), plan.canonicalPrimitives().getFirst().identity());
        assertEquals(2, plan.canonicalPrimitives().getFirst().nodeOccurrences().size());
        assertEquals(0, plan.draws().get(0).canonicalOccurrence().nodeIndex());
        assertEquals(1, plan.draws().get(1).canonicalOccurrence().nodeIndex());
        assertEquals(-2, plan.draws().get(0).modelTransform().m00());
        assertEquals(3, plan.draws().get(0).modelTransform().m11());
        assertEquals(4, plan.draws().get(0).modelTransform().m22());
        assertEquals(5, plan.draws().get(1).modelTransform().m30());
        assertEquals(definition.bounds(), plan.draws().get(0).localBounds());
        assertEquals(8, plan.requiredVertices());
        assertEquals(0.5F, plan.draws().get(0).localCenter().x());
        assertEquals(0.5F, plan.draws().get(0).localCenter().y());
        assertEquals(0, plan.draws().get(0).localCenter().z());
        assertEquals(plan.requiredVertices(), HostDrawPlan.requiredVertices(
                definition, StaticModelRenderMetadata.from(definition)));
    }

    private static ModelDefinition definition() {
        ModelAssetReference asset = new ModelAssetReference(ModelSourceKind.MEMORY, "test", "host-plan",
                new ModelAssetRevision(1, 0, ""));
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        ModelVertexAttribute positions = new ModelVertexAttribute(ModelAttributeSemantic.POSITION,
                ModelComponentType.FLOAT32, 3, false, 3, new byte[36]);
        ModelPrimitive primitive = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES,
                Map.of(ModelAttributeSemantic.POSITION, positions),
                new ModelIndexBuffer(ModelComponentType.UINT8, 3, new byte[]{0, 1, 2}), 0, bounds);
        ModelMesh mesh = new ModelMesh("mesh", List.of(primitive), bounds);
        List<ModelNode> nodes = List.of(
                new ModelNode("first", new ModelTransform.Trs(ModelVector3.ZERO, ModelQuaternion.IDENTITY,
                        new ModelVector3(-2, 3, 4)), 0, List.of(), Optional.of(bounds)),
                new ModelNode("second", new ModelTransform.Trs(new ModelVector3(5, 0, 0), ModelQuaternion.IDENTITY,
                        ModelVector3.ONE), 0, List.of(), Optional.of(bounds)),
                new ModelNode("outside", ModelTransform.Trs.IDENTITY, 0, List.of(), Optional.of(bounds)));
        return new ModelDefinition(asset, List.of(new ModelScene("scene", List.of(0, 1), Optional.of(bounds))), 0,
                nodes, List.of(mesh), List.of(ModelMaterial.defaultMaterial()), List.of(), List.of(), List.of(), bounds);
    }

}
