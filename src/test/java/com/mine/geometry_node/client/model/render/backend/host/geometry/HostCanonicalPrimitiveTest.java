package com.mine.geometry_node.client.model.render.backend.host.geometry;

import com.mine.geometry_node.core.engine.system.model.domain.ModelAttributeSemantic;
import com.mine.geometry_node.core.engine.system.model.domain.ModelBounds;
import com.mine.geometry_node.core.engine.system.model.domain.ModelComponentType;
import com.mine.geometry_node.core.engine.system.model.domain.ModelIndexBuffer;
import com.mine.geometry_node.core.engine.system.model.domain.ModelPrimitive;
import com.mine.geometry_node.core.engine.system.model.domain.ModelPrimitiveTopology;
import com.mine.geometry_node.core.engine.system.model.domain.ModelVector3;
import com.mine.geometry_node.core.engine.system.model.domain.ModelVertexAttribute;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HostCanonicalPrimitiveTest {
    @Test
    void decodesStableTopologyAndDefensivelyExposesArrays() {
        byte[] positions = ByteBuffer.allocate(9 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(0).putFloat(0).putFloat(0)
                .putFloat(2).putFloat(0).putFloat(0)
                .putFloat(0).putFloat(3).putFloat(0).array();
        byte[] normals = ByteBuffer.allocate(9 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(0).putFloat(0).putFloat(1).putFloat(0).putFloat(0).putFloat(1)
                .putFloat(0).putFloat(0).putFloat(1).array();
        byte[] uv = ByteBuffer.allocate(6 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(0).putFloat(0).putFloat(1).putFloat(0).putFloat(0).putFloat(1).array();
        ModelPrimitive source = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES,
                Map.of(ModelAttributeSemantic.POSITION, new ModelVertexAttribute(ModelAttributeSemantic.POSITION,
                                ModelComponentType.FLOAT32, 3, false, 3, positions),
                        ModelAttributeSemantic.NORMAL, new ModelVertexAttribute(ModelAttributeSemantic.NORMAL,
                                ModelComponentType.FLOAT32, 3, false, 3, normals),
                        ModelAttributeSemantic.TEXCOORD_0, new ModelVertexAttribute(ModelAttributeSemantic.TEXCOORD_0,
                                ModelComponentType.FLOAT32, 2, false, 3, uv),
                        ModelAttributeSemantic.COLOR_0, new ModelVertexAttribute(ModelAttributeSemantic.COLOR_0,
                                ModelComponentType.UINT8, 4, true, 3,
                                new byte[]{-1, 0, 0, -1, 0, -1, 0, -1, 0, 0, -1, -1})),
                new ModelIndexBuffer(ModelComponentType.UINT8, 3, new byte[]{2, 0, 1}), 4,
                new ModelBounds(ModelVector3.ZERO, new ModelVector3(2, 3, 0)));

        HostCanonicalPrimitive primitive = HostCanonicalPrimitive.from(7, 5, source);
        float[] firstPositions = primitive.positions();
        int[] firstIndices = primitive.indices();
        firstPositions[0] = 99;
        firstIndices[0] = 99;

        assertEquals(new HostCanonicalPrimitive.Identity(7, 5, 4), primitive.identity());
        assertEquals(3, primitive.vertexCount());
        assertEquals(1, primitive.triangleCount());
        assertArrayEquals(new float[]{0, 0, 0, 2, 0, 0, 0, 3, 0}, primitive.positions());
        assertArrayEquals(new int[]{2, 0, 1}, primitive.indices());
        assertArrayEquals(new int[]{1}, primitive.occurrencesForCanonicalVertex(0));
        assertArrayEquals(new int[]{2}, primitive.occurrencesForCanonicalVertex(1));
        assertArrayEquals(new int[]{0}, primitive.occurrencesForCanonicalVertex(2));
        assertEquals(2, primitive.canonicalIndexAtOccurrence(0));
        assertNotNull(primitive.attribute(ModelAttributeSemantic.NORMAL));
        assertArrayEquals(new float[]{0, 0, 1, 0, 0, 1, 0, 0, 1},
                primitive.attribute(ModelAttributeSemantic.NORMAL).values());
        assertArrayEquals(new float[]{0, 0, 1, 0, 0, 1},
                primitive.attribute(ModelAttributeSemantic.TEXCOORD_0).values());
        assertArrayEquals(new float[]{1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1},
                primitive.attribute(ModelAttributeSemantic.COLOR_0).values());
        assertEquals(source.bounds(), primitive.bounds());
    }
}
