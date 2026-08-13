package com.mine.geometry_node.client.model.gpu;

import java.util.Arrays;

public final class DecodedModelImage {
    private final int width;
    private final int height;
    private final byte[] rgba;

    public DecodedModelImage(int width, int height, byte[] rgba) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("image dimensions must be positive");
        long expected = Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
        if (rgba == null || rgba.length != expected) throw new IllegalArgumentException("RGBA byte length does not match image dimensions");
        this.width = width;
        this.height = height;
        this.rgba = Arrays.copyOf(rgba, rgba.length);
    }

    public int width() { return width; }
    public int height() { return height; }
    public int byteSize() { return rgba.length; }
    public byte[] rgba() { return Arrays.copyOf(rgba, rgba.length); }
}
