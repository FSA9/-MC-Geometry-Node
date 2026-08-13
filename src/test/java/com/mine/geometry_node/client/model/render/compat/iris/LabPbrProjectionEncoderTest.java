package com.mine.geometry_node.client.model.render.compat.iris;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LabPbrProjectionEncoderTest {
    @Test void roughnessControlsSmoothnessAndOtherChannelsRemainAtDefault() {
        int smoothDielectric = LabPbrProjectionEncoder.specular(0xFF0000FF, 0, 0);
        int roughMetal = LabPbrProjectionEncoder.specular(0xFFFFFFFF, 1, 1);
        assertEquals(255, red(smoothDielectric));
        assertEquals(10, green(smoothDielectric));
        assertEquals(0, red(roughMetal));
        assertEquals(255, green(roughMetal));
        assertEquals(0, alpha(roughMetal));
    }

    @Test void normalScaleRenormalizesXyAndOcclusionStrengthMixesFromWhite() {
        int unchangedAo = LabPbrProjectionEncoder.normal(0xFFFF80FF, 0xFF000000, 0, 0);
        int fullAo = LabPbrProjectionEncoder.normal(0xFFFF80FF, 0xFF000000, 1, 1);
        assertEquals(128, red(unchangedAo), 1);
        assertEquals(255, blue(unchangedAo));
        assertEquals(0, blue(fullAo));
    }

    @Test void roughnessEncodingKeepsContinuousEndpointsDistinct() {
        int smooth = LabPbrProjectionEncoder.specular(0xFF000000, 0, 1);
        int middle = LabPbrProjectionEncoder.specular(0xFF008000, 0, 1);
        int rough = LabPbrProjectionEncoder.specular(0xFF00FF00, 0, 1);
        assertTrue(red(smooth) > red(middle));
        assertTrue(red(middle) > red(rough));
    }

    @Test void metallicEndpointsUseLabPbrDielectricAndArbitraryMetalCodes() {
        assertEquals(10, green(LabPbrProjectionEncoder.specular(0xFF000000, 1, 1)));
        assertEquals(255, green(LabPbrProjectionEncoder.specular(0xFF0000FF, 1, 1)));
        assertEquals(10, green(LabPbrProjectionEncoder.specular(0xFF000080, 1, 1)));
    }

    @Test void zeroNormalScaleProducesFlatTangentNormal() {
        int encoded = LabPbrProjectionEncoder.normal(0xFF20E0FF, 0xFFFFFFFF, 0, 1);
        assertEquals(128, red(encoded), 1);
        assertEquals(128, green(encoded), 1);
    }

    @Test void metallicEndpointScanUsesEffectiveTextureTimesFactor() {
        NativeImage endpoints = new NativeImage(2, 1, false);
        try {
            endpoints.setPixel(0, 0, 0xFF000000);
            endpoints.setPixel(1, 0, 0xFF0000FF);
            assertTrue(LabPbrProjectionEncoder.metallicEndpointsOnly(endpoints, 1));
            assertFalse(LabPbrProjectionEncoder.metallicEndpointsOnly(endpoints, 0.5F));
            assertTrue(LabPbrProjectionEncoder.metallicEndpointsOnly(endpoints, 0));
            endpoints.setPixel(1, 0, 0xFF000080);
            assertFalse(LabPbrProjectionEncoder.metallicEndpointsOnly(endpoints, 1));
        } finally {
            endpoints.close();
        }
        assertTrue(LabPbrProjectionEncoder.metallicEndpointsOnly(null, 0));
        assertTrue(LabPbrProjectionEncoder.metallicEndpointsOnly(null, 1));
        assertFalse(LabPbrProjectionEncoder.metallicEndpointsOnly(null, 0.5F));
    }

    @Test void convertsGltfPositiveYToLabPbrNegativeY() {
        int encoded = LabPbrProjectionEncoder.normal(0xFF80FFFF, 0xFFFFFFFF, 1, 1);
        assertTrue(green(encoded) < 128);
    }

    private static int alpha(int c) { return c >>> 24; }
    private static int red(int c) { return c >>> 16 & 255; }
    private static int green(int c) { return c >>> 8 & 255; }
    private static int blue(int c) { return c & 255; }
}
