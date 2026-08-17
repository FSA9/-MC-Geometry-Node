package com.mine.geometry_node.client.model.render.backend.host.iris.labpbr;

import com.mine.geometry_node.client.model.gpu.DecodedModelImage;
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
        int[] endpoints = {0xFF000000, 0xFF0000FF};
        assertTrue(LabPbrProjectionEncoder.metallicPixelEndpointsOnly(endpoints, 1));
        assertFalse(LabPbrProjectionEncoder.metallicPixelEndpointsOnly(endpoints, 0.5F));
        assertTrue(LabPbrProjectionEncoder.metallicPixelEndpointsOnly(endpoints, 0));
        assertFalse(LabPbrProjectionEncoder.metallicPixelEndpointsOnly(new int[]{0xFF000000, 0xFF000080}, 1));
        assertTrue(LabPbrProjectionEncoder.metallicPixelEndpointsOnly(null, 0));
        assertTrue(LabPbrProjectionEncoder.metallicPixelEndpointsOnly(null, 1));
        assertFalse(LabPbrProjectionEncoder.metallicPixelEndpointsOnly(null, 0.5F));
    }

    @Test void convertsGltfPositiveYToLabPbrNegativeY() {
        int encoded = LabPbrProjectionEncoder.normal(0xFF80FFFF, 0xFFFFFFFF, 1, 1);
        assertTrue(green(encoded) < 128);
    }

    @Test void decodedImageProjectionMatchesScalarChannelEncoding() {
        DecodedModelImage mr = new DecodedModelImage(2, 1,
                new byte[]{0, (byte) 128, 0, (byte) 255, 0, (byte) 255, (byte) 128, (byte) 255});

        DecodedModelImage projected = LabPbrProjectionEncoder.buildDecodedSpecular(mr, 2, 1, 1, 1);
        byte[] rgba = projected.rgba();

        assertEquals(LabPbrProjectionEncoder.specular(0xFF008000, 1, 1), argb(rgba, 0));
        assertEquals(LabPbrProjectionEncoder.specular(0xFF00FF80, 1, 1), argb(rgba, 4));
        assertFalse(LabPbrProjectionEncoder.decodedMetallicEndpointsOnly(mr, 1));
    }

    private static int argb(byte[] rgba, int offset) {
        return (rgba[offset + 3] & 255) << 24 | (rgba[offset] & 255) << 16
                | (rgba[offset + 1] & 255) << 8 | rgba[offset + 2] & 255;
    }

    private static int alpha(int c) { return c >>> 24; }
    private static int red(int c) { return c >>> 16 & 255; }
    private static int green(int c) { return c >>> 8 & 255; }
    private static int blue(int c) { return c & 255; }
}
