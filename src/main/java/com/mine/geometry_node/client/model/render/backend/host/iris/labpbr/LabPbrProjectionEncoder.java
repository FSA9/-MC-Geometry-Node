package com.mine.geometry_node.client.model.render.backend.host.iris.labpbr;

import com.mojang.blaze3d.platform.NativeImage;

/** Pure channel conversion from glTF metallic-roughness material inputs to LabPBR auxiliaries. */
public final class LabPbrProjectionEncoder {
    private static final float ENDPOINT_EPSILON = 1.0E-6F;

    private LabPbrProjectionEncoder() {}

    public static int specular(int mrArgb, float metallicFactor, float roughnessFactor) {
        float roughness = channel(mrArgb, 8) * roughnessFactor;
        float metallic = channel(mrArgb, 0) * metallicFactor;
        int smoothness = byteValue(1.0F - (float) Math.sqrt(clamp01(roughness)));
        int reflectance = metallic >= 1.0F - ENDPOINT_EPSILON ? 255 : 10;
        return argb(0, smoothness, reflectance, 0);
    }

    public static int normal(int normalArgb, int occlusionArgb, float normalScale, float occlusionStrength) {
        float x = (channel(normalArgb, 16) * 2 - 1) * normalScale;
        // glTF tangent-space normals are OpenGL +Y; LabPBR stores DirectX -Y.
        float y = -((channel(normalArgb, 8) * 2 - 1) * normalScale);
        float z = Math.max(0, channel(normalArgb, 0) * 2 - 1);
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length > 1.0E-8F) { x /= length; y /= length; }
        else { x = 0; y = 0; }
        float ao = 1 + (channel(occlusionArgb, 16) - 1) * clamp01(occlusionStrength);
        return argb(255, byteValue(x * 0.5F + 0.5F), byteValue(y * 0.5F + 0.5F), byteValue(ao));
    }

    public static NativeImage buildSpecular(NativeImage mr, int width, int height,
                                            float metallic, float roughness) {
        NativeImage output = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            output.setPixel(x, y, specular(sample(mr, x, y, width, height, 0xFFFFFFFF), metallic, roughness));
        }
        return output;
    }

    /** True only when every effective glTF metallic sample is exactly an encodable endpoint. */
    public static boolean metallicEndpointsOnly(NativeImage mr, float metallicFactor) {
        float factor = clamp01(metallicFactor);
        if (factor <= ENDPOINT_EPSILON) return true;
        if (mr == null) return factor >= 1.0F - ENDPOINT_EPSILON;
        for (int y = 0; y < mr.getHeight(); y++) {
            for (int x = 0; x < mr.getWidth(); x++) {
                if (!metallicEndpoint(mr.getPixel(x, y), factor)) return false;
            }
        }
        return true;
    }

    static boolean metallicPixelEndpointsOnly(int[] pixels, float metallicFactor) {
        float factor = clamp01(metallicFactor);
        if (factor <= ENDPOINT_EPSILON) return true;
        if (pixels == null) return factor >= 1.0F - ENDPOINT_EPSILON;
        for (int pixel : pixels) if (!metallicEndpoint(pixel, factor)) return false;
        return true;
    }

    private static boolean metallicEndpoint(int pixel, float factor) {
        float metallic = channel(pixel, 0) * factor;
        return metallic <= ENDPOINT_EPSILON || metallic >= 1.0F - ENDPOINT_EPSILON;
    }

    public static NativeImage buildNormal(NativeImage normal, NativeImage ao, int width, int height,
                                           float normalScale, float aoStrength) {
        if (normal == null && ao == null) return null;
        NativeImage output = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            output.setPixel(x, y, normal(sample(normal, x, y, width, height, 0xFF8080FF),
                    sample(ao, x, y, width, height, 0xFFFFFFFF), normalScale, aoStrength));
        }
        return output;
    }

    private static int sample(NativeImage image, int x, int y, int width, int height, int fallback) {
        if (image == null) return fallback;
        return image.getPixel(Math.min(image.getWidth() - 1, x * image.getWidth() / width),
                Math.min(image.getHeight() - 1, y * image.getHeight() / height));
    }
    private static float channel(int argb, int shift) { return ((argb >>> shift) & 255) / 255F; }
    private static int byteValue(float value) { return Math.round(clamp01(value) * 255); }
    private static float clamp01(float value) { return Math.max(0, Math.min(1, value)); }
    private static int argb(int a, int r, int g, int b) { return a << 24 | r << 16 | g << 8 | b; }
}
