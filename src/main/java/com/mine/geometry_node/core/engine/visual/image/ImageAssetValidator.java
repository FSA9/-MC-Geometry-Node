package com.mine.geometry_node.core.engine.visual.image;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ImageAssetValidator {
    public static final int MAX_ENCODED_BYTES = 1_000_000;
    public static final int MAX_DIMENSION = 4096;
    public static final long MAX_PIXELS = 4_194_304L;

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private ImageAssetValidator() {
    }

    public static Dimensions validateImage(byte[] data) throws IOException {
        if (data == null || data.length < 10) {
            throw new IOException("Image data is incomplete");
        }
        if (data.length > MAX_ENCODED_BYTES) {
            throw new IOException("Image exceeds " + MAX_ENCODED_BYTES + " encoded bytes");
        }

        Dimensions dimensions;
        if (hasPrefix(data, PNG_SIGNATURE)) {
            dimensions = readPng(data);
        } else if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
            dimensions = readJpeg(data);
        } else if (startsWithAscii(data, "GIF87a") || startsWithAscii(data, "GIF89a")) {
            dimensions = new Dimensions(readUnsignedShortLe(data, 6), readUnsignedShortLe(data, 8));
        } else if (data[0] == 'B' && data[1] == 'M') {
            dimensions = readBmp(data);
        } else {
            dimensions = readTga(data);
        }

        validateDimensions(dimensions);
        return dimensions;
    }

    private static Dimensions readPng(byte[] data) throws IOException {
        if (data.length < 24) {
            throw new IOException("PNG data is incomplete");
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) {
                throw new IOException("File is not a PNG image");
            }
        }
        if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') {
            throw new IOException("PNG is missing its IHDR chunk");
        }

        return new Dimensions(readPositiveIntBe(data, 16), readPositiveIntBe(data, 20));
    }

    private static Dimensions readJpeg(byte[] data) throws IOException {
        int offset = 2;
        while (offset + 3 < data.length) {
            while (offset < data.length && data[offset] != (byte) 0xFF) offset++;
            while (offset < data.length && data[offset] == (byte) 0xFF) offset++;
            if (offset >= data.length) break;
            int marker = data[offset++] & 0xFF;
            if (marker == 0xD8 || marker == 0xD9 || marker == 0x01 || marker >= 0xD0 && marker <= 0xD7) {
                continue;
            }
            if (offset + 1 >= data.length) break;
            int length = readUnsignedShortBe(data, offset);
            if (length < 2 || offset + length > data.length) {
                throw new IOException("JPEG segment is invalid");
            }
            if (isJpegStartOfFrame(marker)) {
                if (length < 7) throw new IOException("JPEG frame header is incomplete");
                return new Dimensions(readUnsignedShortBe(data, offset + 5), readUnsignedShortBe(data, offset + 3));
            }
            offset += length;
        }
        throw new IOException("JPEG dimensions are missing");
    }

    private static Dimensions readBmp(byte[] data) throws IOException {
        if (data.length < 26) throw new IOException("BMP data is incomplete");
        int dibSize = readIntLe(data, 14);
        if (dibSize == 12) {
            return new Dimensions(readUnsignedShortLe(data, 18), readUnsignedShortLe(data, 20));
        }
        if (dibSize < 40 || data.length < 26) throw new IOException("BMP header is unsupported");
        int width = readIntLe(data, 18);
        int height = readIntLe(data, 22);
        if (width <= 0 || height == 0 || height == Integer.MIN_VALUE) {
            throw new IOException("BMP dimensions are invalid");
        }
        return new Dimensions(width, Math.abs(height));
    }

    private static Dimensions readTga(byte[] data) throws IOException {
        if (data.length < 18) throw new IOException("Unsupported image format");
        int imageType = data[2] & 0xFF;
        if (imageType != 1 && imageType != 2 && imageType != 3
                && imageType != 9 && imageType != 10 && imageType != 11) {
            throw new IOException("Unsupported image format");
        }
        return new Dimensions(readUnsignedShortLe(data, 12), readUnsignedShortLe(data, 14));
    }

    private static void validateDimensions(Dimensions dimensions) throws IOException {
        int width = dimensions.width;
        int height = dimensions.height;
        if (width <= 0 || height <= 0) {
            throw new IOException("Image dimensions must be positive");
        }
        if (width > MAX_DIMENSION || height > MAX_DIMENSION || (long) width * height > MAX_PIXELS) {
            throw new IOException("Image dimensions exceed the supported limit: " + width + "x" + height);
        }
    }

    public static String contentId(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean isJpegStartOfFrame(int marker) {
        return marker >= 0xC0 && marker <= 0xC3
                || marker >= 0xC5 && marker <= 0xC7
                || marker >= 0xC9 && marker <= 0xCB
                || marker >= 0xCD && marker <= 0xCF;
    }

    private static boolean hasPrefix(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] data, String prefix) {
        if (data.length < prefix.length()) return false;
        for (int i = 0; i < prefix.length(); i++) {
            if ((data[i] & 0xFF) != prefix.charAt(i)) return false;
        }
        return true;
    }

    private static int readPositiveIntBe(byte[] data, int offset) throws IOException {
        requireBytes(data, offset, 4);
        int value = (data[offset] & 0xFF) << 24
                | (data[offset + 1] & 0xFF) << 16
                | (data[offset + 2] & 0xFF) << 8
                | data[offset + 3] & 0xFF;
        if (value <= 0) {
            throw new IOException("PNG dimensions must be positive");
        }
        return value;
    }

    private static int readIntLe(byte[] data, int offset) throws IOException {
        requireBytes(data, offset, 4);
        return data[offset] & 0xFF
                | (data[offset + 1] & 0xFF) << 8
                | (data[offset + 2] & 0xFF) << 16
                | data[offset + 3] << 24;
    }

    private static int readUnsignedShortBe(byte[] data, int offset) throws IOException {
        requireBytes(data, offset, 2);
        return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
    }

    private static int readUnsignedShortLe(byte[] data, int offset) throws IOException {
        requireBytes(data, offset, 2);
        return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8;
    }

    private static void requireBytes(byte[] data, int offset, int count) throws IOException {
        if (offset < 0 || count < 0 || offset + count > data.length) {
            throw new IOException("Image header is incomplete");
        }
    }

    public record Dimensions(int width, int height) {
    }
}
