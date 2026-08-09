package com.mine.geometry_node.core.engine.system.asset;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** One validated relative asset path and its opaque binary content. */
public record RemoteAssetFile(String targetPath, byte[] content) {
    public static final int MAX_CONTENT_BYTES = 32 * 1024 * 1024;

    public RemoteAssetFile {
        targetPath = targetPath != null ? targetPath : "";
        content = content != null ? Arrays.copyOf(content, content.length) : new byte[0];
        if (content.length > MAX_CONTENT_BYTES) {
            throw new IllegalArgumentException("asset exceeds transfer limit: " + targetPath);
        }
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    public String utf8Text() {
        return new String(content, StandardCharsets.UTF_8);
    }
}
