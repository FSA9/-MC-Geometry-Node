package com.mine.geometry_node.core.engine.system.asset.preview.generator;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewLimits;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Bounded decoder for uncompressed and RLE true-color/grayscale TGA assets. */
final class TgaImageDecoder {
    private TgaImageDecoder() {
    }

    static BufferedImage read(Path source) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(source))) {
            int idLength = read(input);
            int colorMapType = read(input);
            int imageType = read(input);
            skipFully(input, 5);
            skipFully(input, 4);
            int width = readLe16(input);
            int height = readLe16(input);
            int depth = read(input);
            int descriptor = read(input);
            boolean trueColor = imageType == 2 || imageType == 10;
            boolean grayscale = imageType == 3 || imageType == 11;
            boolean rle = imageType == 10 || imageType == 11;
            if (colorMapType != 0 || (!trueColor && !grayscale)
                    || (trueColor && depth != 24 && depth != 32)
                    || (grayscale && depth != 8)
                    || !AssetPreviewLimits.validImageSourceDimensions(width, height)) {
                throw new IOException("Unsupported or oversized TGA image");
            }
            skipFully(input, idLength);
            int[] pixels = new int[Math.multiplyExact(width, height)];
            int cursor = 0;
            while (cursor < pixels.length) {
                int count = 1;
                boolean repeated = false;
                if (rle) {
                    int header = read(input);
                    count = (header & 0x7F) + 1;
                    repeated = (header & 0x80) != 0;
                }
                if (cursor + count > pixels.length) throw new IOException("Invalid TGA RLE packet");
                if (repeated) {
                    int color = readPixel(input, depth, grayscale);
                    for (int i = 0; i < count; i++) pixels[cursor++] = color;
                } else {
                    for (int i = 0; i < count; i++) pixels[cursor++] = readPixel(input, depth, grayscale);
                }
            }

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            boolean topOrigin = (descriptor & 0x20) != 0;
            boolean rightOrigin = (descriptor & 0x10) != 0;
            for (int sourceY = 0; sourceY < height; sourceY++) {
                int targetY = topOrigin ? sourceY : height - 1 - sourceY;
                for (int sourceX = 0; sourceX < width; sourceX++) {
                    int targetX = rightOrigin ? width - 1 - sourceX : sourceX;
                    image.setRGB(targetX, targetY, pixels[sourceY * width + sourceX]);
                }
            }
            return image;
        }
    }

    private static int readPixel(InputStream input, int depth, boolean grayscale) throws IOException {
        if (grayscale) {
            int value = read(input);
            return 0xFF000000 | value << 16 | value << 8 | value;
        }
        int blue = read(input);
        int green = read(input);
        int red = read(input);
        int alpha = depth == 32 ? read(input) : 255;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int readLe16(InputStream input) throws IOException {
        return read(input) | read(input) << 8;
    }

    private static int read(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) throw new EOFException("Unexpected end of TGA image");
        return value;
    }

    private static void skipFully(InputStream input, int bytes) throws IOException {
        for (int remaining = bytes; remaining > 0; ) {
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= Math.toIntExact(skipped);
            } else {
                read(input);
                remaining--;
            }
        }
    }
}
