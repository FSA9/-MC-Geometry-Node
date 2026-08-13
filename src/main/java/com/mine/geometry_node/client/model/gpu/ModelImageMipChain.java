package com.mine.geometry_node.client.model.gpu;

import java.util.ArrayList;
import java.util.List;

/** Converts sRGB color textures to the linear render contract and builds a GPU-safe mip chain. */
public final class ModelImageMipChain {
    private ModelImageMipChain() {}

    public static List<DecodedModelImage> generate(DecodedModelImage source) {
        return prepare(source, true);
    }

    public static List<DecodedModelImage> prepare(DecodedModelImage source, boolean mipmapped) {
        return prepare(source, mipmapped, ModelTextureColorSpace.SRGB_COLOR);
    }

    public static List<DecodedModelImage> prepare(DecodedModelImage source, boolean mipmapped,
                                                   ModelTextureColorSpace colorSpace) {
        List<DecodedModelImage> levels = new ArrayList<>();
        DecodedModelImage current = colorSpace == ModelTextureColorSpace.SRGB_COLOR ? linearize(source) : source;
        levels.add(current);
        if (!mipmapped) return List.copyOf(levels);
        // Minecraft 26.1 derives upload dimensions with an unclamped right shift. A complete
        // rectangular chain would therefore expose a zero-sized axis. Keep the maximal safe
        // prefix until the backend provides independently clamped per-level dimensions.
        while (current.width() > 1 && current.height() > 1) {
            current = colorSpace == ModelTextureColorSpace.NORMAL_VECTOR
                    ? downsampleNormal(current) : downsample(current);
            levels.add(current);
        }
        return List.copyOf(levels);
    }

    static DecodedModelImage downsample(DecodedModelImage source) {
        int width = Math.max(1, source.width() / 2);
        int height = Math.max(1, source.height() / 2);
        byte[] input = source.rgba();
        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int samples = 0;
                double red = 0, green = 0, blue = 0, alpha = 0;
                int startX = x * source.width() / width;
                int endX = Math.max(startX + 1, (x + 1) * source.width() / width);
                int startY = y * source.height() / height;
                int endY = Math.max(startY + 1, (y + 1) * source.height() / height);
                for (int sy = startY; sy < endY; sy++) for (int sx = startX; sx < endX; sx++) {
                    int offset = (sy * source.width() + sx) * 4;
                    red += (input[offset] & 0xFF) / 255.0;
                    green += (input[offset + 1] & 0xFF) / 255.0;
                    blue += (input[offset + 2] & 0xFF) / 255.0;
                    alpha += (input[offset + 3] & 0xFF) / 255.0;
                    samples++;
                }
                int target = (y * width + x) * 4;
                output[target] = unitByte(red / samples);
                output[target + 1] = unitByte(green / samples);
                output[target + 2] = unitByte(blue / samples);
                output[target + 3] = (byte) Math.round(alpha / samples * 255.0);
            }
        }
        return new DecodedModelImage(width, height, output);
    }

    static DecodedModelImage downsampleNormal(DecodedModelImage source) {
        int width = Math.max(1, source.width() / 2);
        int height = Math.max(1, source.height() / 2);
        byte[] input = source.rgba();
        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int samples = 0;
            double nx = 0, ny = 0, nz = 0, alpha = 0;
            int startX = x * source.width() / width;
            int endX = Math.max(startX + 1, (x + 1) * source.width() / width);
            int startY = y * source.height() / height;
            int endY = Math.max(startY + 1, (y + 1) * source.height() / height);
            for (int sy = startY; sy < endY; sy++) for (int sx = startX; sx < endX; sx++) {
                int offset = (sy * source.width() + sx) * 4;
                nx += (input[offset] & 0xFF) / 127.5 - 1.0;
                ny += (input[offset + 1] & 0xFF) / 127.5 - 1.0;
                nz += (input[offset + 2] & 0xFF) / 127.5 - 1.0;
                alpha += (input[offset + 3] & 0xFF) / 255.0;
                samples++;
            }
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length <= 1.0E-12) { nx = 0; ny = 0; nz = 1; }
            else { nx /= length; ny /= length; nz /= length; }
            int target = (y * width + x) * 4;
            output[target] = unitByte(nx * 0.5 + 0.5);
            output[target + 1] = unitByte(ny * 0.5 + 0.5);
            output[target + 2] = unitByte(nz * 0.5 + 0.5);
            output[target + 3] = (byte) Math.round(alpha / samples * 255.0);
        }
        return new DecodedModelImage(width, height, output);
    }

    private static DecodedModelImage linearize(DecodedModelImage source) {
        byte[] result = source.rgba();
        for (int offset = 0; offset < result.length; offset += 4) {
            result[offset] = unitByte(srgbToLinear(result[offset] & 0xFF));
            result[offset + 1] = unitByte(srgbToLinear(result[offset + 1] & 0xFF));
            result[offset + 2] = unitByte(srgbToLinear(result[offset + 2] & 0xFF));
        }
        return new DecodedModelImage(source.width(), source.height(), result);
    }

    private static double srgbToLinear(int encoded) {
        double value = encoded / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static byte unitByte(double value) {
        return (byte) Math.round(Math.max(0, Math.min(1, value)) * 255.0);
    }
}
