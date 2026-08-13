package com.mine.geometry_node.client.model.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record LocalModelAssetRequest(Path path, long sourceSize, long sourceLastModified) {
    public LocalModelAssetRequest {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        path = path.toAbsolutePath().normalize();
        if (sourceSize < 0 || sourceLastModified < 0) throw new IllegalArgumentException("source revision must not be negative");
    }

    public static LocalModelAssetRequest inspect(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        return new LocalModelAssetRequest(normalized, Files.size(normalized),
                Files.getLastModifiedTime(normalized).toMillis());
    }
}
