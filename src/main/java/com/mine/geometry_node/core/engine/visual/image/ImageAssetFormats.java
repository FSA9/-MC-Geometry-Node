package com.mine.geometry_node.core.engine.visual.image;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Shared image-format registry for asset browsing and runtime visuals. */
public final class ImageAssetFormats {
    private ImageAssetFormats() {
    }

    public static boolean isSupportedPath(@Nullable String path) {
        return fromPath(path) != null;
    }

    @Nullable
    public static Format fromPath(@Nullable String path) {
        if (path == null || path.isBlank()) return null;
        String lower = path.toLowerCase(Locale.ROOT);
        for (Format format : Format.values()) {
            for (String extension : format.extensions) {
                if (lower.endsWith(extension)) {
                    return format;
                }
            }
        }
        return null;
    }

    public enum Format {
        PNG(".png"),
        JPEG(".jpg", ".jpeg"),
        BMP(".bmp"),
        GIF(".gif"),
        TGA(".tga");

        private final String[] extensions;

        Format(String... extensions) {
            this.extensions = extensions;
        }
    }
}
