package com.mine.geometry_node.core.engine.system.visual.image;

import com.mine.geometry_node.core.engine.system.asset.ServerAssetPaths;

/** Distinguishes server assets selected through remote:/ from client-local paths. */
public record ImagePathReference(Source source, String path) {
    public static ImagePathReference parse(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("image path must not be empty");
        }

        String value = rawPath.trim();
        if (value.length() > 4096) {
            throw new IllegalArgumentException("image path is too long");
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("remote:")) {
            String relative = value.substring("remote:".length()).replace('\\', '/');
            while (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            relative = ServerAssetPaths.normalizeRelativePath(relative, false);
            requireSupportedImage(relative);
            return new ImagePathReference(Source.SERVER, relative);
        }

        requireSupportedImage(value);
        return new ImagePathReference(Source.LOCAL, value);
    }

    private static void requireSupportedImage(String path) {
        if (!ImageAssetFormats.isSupportedPath(path)) {
            throw new IllegalArgumentException("unsupported image format: " + path);
        }
    }

    public enum Source {
        SERVER("server"),
        LOCAL("local");

        private final String id;

        Source(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }
}
