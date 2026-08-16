package com.mine.geometry_node.client.model.gpu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class ModelShadowOpacityProjectionTest {
    @Test
    void removesColorAndPreservesAlpha() {
        DecodedModelImage source = new DecodedModelImage(2, 1,
                new byte[]{10, 20, 30, 40, (byte) 250, (byte) 240, (byte) 230, (byte) 220});

        assertArrayEquals(new byte[]{0, 0, 0, 40, 0, 0, 0, (byte) 220},
                ModelShadowOpacityProjection.project(source).rgba());
        assertArrayEquals(new byte[]{10, 20, 30, 40, (byte) 250, (byte) 240, (byte) 230, (byte) 220},
                source.rgba(), "projection must not mutate decoded source data");
    }
}
