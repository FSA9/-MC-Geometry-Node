package com.mine.geometry_node.client.model.render.backend.standalone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.joml.Vector4f;
import com.mine.geometry_node.core.engine.system.model.domain.ModelTextureTransform;
import java.nio.ByteBuffer;
import java.util.List;

class ModelMaterialUniformTest {
    @Test
    void std140LayoutContainsSevenHeaderAndTenTransformVec4s() {
        assertEquals(17 * 4 * Float.BYTES, ModelMaterialUniformArena.MATERIAL_BYTES);
    }

    @Test
    void offsetsMatchShaderHeaderAndFiveFullTransforms() {
        ModelTextureTransform transform = new ModelTextureTransform(1, 2, 3, 4, 5);
        ModelMaterialUniform uniform = new ModelMaterialUniform(new Vector4f(1), new Vector4f(2),
                new Vector4f(3), new Vector4f(4), new Vector4f(5), new Vector4f(6), new Vector4f(7),
                List.of(transform, transform, transform, transform, transform));
        ByteBuffer bytes = ModelMaterialUniformArena.encode(uniform);
        assertEquals(6, bytes.getFloat(5 * 16));
        assertEquals(7, bytes.getFloat(6 * 16));
        assertEquals(1, bytes.getFloat(7 * 16));
        assertEquals(3, bytes.getFloat(7 * 16 + 8));
        assertEquals(4, bytes.getFloat(8 * 16));
        assertEquals(5, bytes.getFloat(8 * 16 + 4));
        assertEquals(ModelMaterialUniformArena.MATERIAL_BYTES, bytes.limit());
    }
}
