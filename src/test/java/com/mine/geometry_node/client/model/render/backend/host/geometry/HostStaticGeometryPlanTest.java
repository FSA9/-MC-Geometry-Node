package com.mine.geometry_node.client.model.render.backend.host.geometry;

import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HostStaticGeometryPlanTest {
    @Test
    void packsDeterministicStaticSourceAndLogicalEntityQuadOrder() {
        HostEntityGeometry geometry = HostGeometryProjector.project(primitive(true), StaticModelTexture.absent());

        HostStaticGeometryPlan plan = HostStaticGeometryPlan.from(geometry);

        assertEquals(1, plan.triangleCount());
        assertEquals(3, plan.sourceVertexCount());
        assertEquals(4, plan.quadVertexCount());
        assertArrayEquals(new int[]{0, 1, 2, 2}, plan.quadOrder(false));
        assertArrayEquals(new int[]{0, 2, 1, 1}, plan.quadOrder(true));
        assertEquals(3L * HostStaticGeometryPlan.SOURCE_VERTEX_BYTES, plan.sourceByteSize());
        assertEquals(4L * Integer.BYTES, plan.orderByteSize());
        assertEquals(plan.sourceByteSize() + plan.orderByteSize(), plan.contractByteSize(false));
        assertEquals(plan.sourceByteSize() + plan.orderByteSize() * 2, plan.contractByteSize(true));
        assertEquals(4L * HostStaticGeometryPlan.SOURCE_VERTEX_BYTES, plan.expandedQuadByteSize());

        float[] packed = floats(plan.packedSource());
        assertArrayEquals(new float[]{
                0, 0, 0, 0, 0, 1, 0, 0, 1, 0.5F, 0.25F, 1,
                1, 0, 0, 0, 0, 1, 1, 0, 0.75F, 1, 0.5F, 1,
                0, 1, 0, 0, 0, 1, 0, 1, 0.25F, 0.75F, 1, 1
        }, packed);

        byte[] mutation = plan.packedSource();
        mutation[0] = 42;
        assertNotEquals(42, plan.packedSource()[0]);
        int[] orderMutation = plan.quadOrder(false);
        orderMutation[0] = 42;
        assertArrayEquals(new int[]{0, 1, 2, 2}, plan.quadOrder(false));
    }

    @Test
    void generatedFaceNormalsRemainPartOfTheStaticSource() {
        HostEntityGeometry geometry = HostGeometryProjector.project(primitive(false), StaticModelTexture.absent());

        float[] packed = floats(HostStaticGeometryPlan.from(geometry).packedSource());

        for (int vertex = 0; vertex < 3; vertex++) {
            int normal = vertex * HostStaticGeometryPlan.COMPONENTS_PER_VERTEX + 3;
            assertEquals(0, packed[normal]);
            assertEquals(0, packed[normal + 1]);
            assertEquals(1, packed[normal + 2]);
        }
    }

    private static ModelPrimitive primitive(boolean includeNormals) {
        Map<ModelAttributeSemantic, ModelVertexAttribute> attributes = new LinkedHashMap<>();
        attributes.put(ModelAttributeSemantic.POSITION, attribute(ModelAttributeSemantic.POSITION, 3,
                0, 0, 0, 1, 0, 0, 0, 1, 0));
        if (includeNormals) {
            attributes.put(ModelAttributeSemantic.NORMAL, attribute(ModelAttributeSemantic.NORMAL, 3,
                    0, 0, 1, 0, 0, 1, 0, 0, 1));
        }
        attributes.put(ModelAttributeSemantic.COLOR_0, attribute(ModelAttributeSemantic.COLOR_0, 4,
                1, 0.5F, 0.25F, 1, 0.75F, 1, 0.5F, 1, 0.25F, 0.75F, 1, 1));
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        return new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES, attributes,
                new ModelIndexBuffer(ModelComponentType.UINT8, 3, new byte[]{0, 1, 2}), 0, bounds);
    }

    private static ModelVertexAttribute attribute(ModelAttributeSemantic semantic, int components, float... values) {
        ByteBuffer data = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) data.putFloat(value);
        return new ModelVertexAttribute(semantic, ModelComponentType.FLOAT32, components, false,
                values.length / components, data.array());
    }

    private static float[] floats(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] values = new float[bytes.length / Float.BYTES];
        for (int index = 0; index < values.length; index++) values[index] = buffer.getFloat();
        return values;
    }
}
