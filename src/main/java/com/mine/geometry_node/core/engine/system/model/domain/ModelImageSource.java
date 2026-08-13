package com.mine.geometry_node.core.engine.system.model.domain;

import java.util.Arrays;
import java.util.Locale;

public final class ModelImageSource {
    private final String mimeType;
    private final int width;
    private final int height;
    private final byte[] encodedData;

    public ModelImageSource(String mimeType, int width, int height, byte[] encodedData) {
        String normalized = mimeType == null ? "" : mimeType.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("image/png") && !normalized.equals("image/jpeg")) throw new IllegalArgumentException("only embedded PNG and JPEG images are supported");
        if (encodedData == null || encodedData.length == 0) throw new IllegalArgumentException("encoded image must not be empty");
        if (width < 1 || height < 1) throw new IllegalArgumentException("image dimensions must be positive");
        this.mimeType = normalized;
        this.width = width;
        this.height = height;
        this.encodedData = Arrays.copyOf(encodedData, encodedData.length);
    }

    public String mimeType() { return mimeType; }
    public int width() { return width; }
    public int height() { return height; }
    public int byteSize() { return encodedData.length; }
    public byte[] encodedData() { return Arrays.copyOf(encodedData, encodedData.length); }
}
