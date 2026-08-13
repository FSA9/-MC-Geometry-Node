package com.mine.geometry_node.client.model.gpu.minecraft;

import com.mine.geometry_node.core.engine.system.model.domain.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GltfSamplerGlConstantsTest {
    @Test
    void preservesEveryGltfSamplerEnumExactly() {
        assertArrayEquals(new int[]{9728, 9729, 9984, 9985, 9986, 9987},
                java.util.Arrays.stream(ModelTextureFilter.values()).mapToInt(GltfSamplerGlConstants::min).toArray());
        assertArrayEquals(new int[]{33071, 33648, 10497},
                java.util.Arrays.stream(ModelTextureWrap.values()).mapToInt(GltfSamplerGlConstants::wrap).toArray());
        assertEquals(9728, GltfSamplerGlConstants.mag(ModelTextureFilter.NEAREST));
        assertEquals(9729, GltfSamplerGlConstants.mag(ModelTextureFilter.LINEAR));
        assertThrows(IllegalArgumentException.class,
                () -> GltfSamplerGlConstants.mag(ModelTextureFilter.LINEAR_MIPMAP_LINEAR));
    }
}
