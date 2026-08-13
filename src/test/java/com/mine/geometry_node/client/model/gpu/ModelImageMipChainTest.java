package com.mine.geometry_node.client.model.gpu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelImageMipChainTest {
    @Test
    void generatesGpuSafeChainAndIncludesOddDimensionTailPixels() {
        byte[] rgba = new byte[3 * 3 * 4];
        for (int y = 0; y < 3; y++) {
            int offset = (y * 3 + 2) * 4;
            rgba[offset] = (byte) 255;
            rgba[offset + 3] = (byte) 255;
        }
        List<DecodedModelImage> levels = ModelImageMipChain.generate(new DecodedModelImage(3, 3, rgba));
        assertEquals(2, levels.size());
        assertEquals(1, levels.get(1).width());
        assertTrue((levels.get(1).rgba()[0] & 0xFF) > 80,
                "the third red texel must participate in the 3-to-1 downsample");
    }

    @Test
    void stopsRectangularChainBeforeMinecraftWouldExposeAZeroSizedMipAxis() {
        List<DecodedModelImage> levels = ModelImageMipChain.generate(
                new DecodedModelImage(32, 8, new byte[32 * 8 * 4]));

        assertEquals(List.of("32x8", "16x4", "8x2", "4x1"), levels.stream()
                .map(level -> level.width() + "x" + level.height())
                .toList());
    }

    @Test
    void storesAndAveragesColorInLinearSpaceAndAlphaLinearly() {
        byte[] rgba = {
                (byte) 255, 0, 0, (byte) 255, 0, 0, 0, 0,
                (byte) 255, 0, 0, (byte) 255, 0, 0, 0, 0
        };
        List<DecodedModelImage> levels = ModelImageMipChain.generate(new DecodedModelImage(2, 2, rgba));
        assertEquals(255, levels.getFirst().rgba()[0] & 0xFF);
        byte[] result = levels.get(1).rgba();
        assertEquals(128, result[0] & 0xFF, 1);
        assertEquals(128, result[3] & 0xFF, 1);
    }

    @Test
    void linearizesSrgbBaseLevelBeforeHardwareFiltering() {
        byte[] rgba = {(byte) 128, (byte) 128, (byte) 128, (byte) 255};
        byte[] linear = ModelImageMipChain.generate(new DecodedModelImage(1, 1, rgba)).getFirst().rgba();
        assertEquals(55, linear[0] & 0xFF, 1);
    }
}
