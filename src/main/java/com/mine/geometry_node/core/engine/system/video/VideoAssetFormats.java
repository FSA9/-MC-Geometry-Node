package com.mine.geometry_node.core.engine.system.video;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Shared video-container suffix registry for asset recognition. */
public final class VideoAssetFormats {
    private VideoAssetFormats() {
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
                if (lower.endsWith(extension)) return format;
            }
        }
        return null;
    }

    public enum Format {
        MP4(".mp4"),
        M4V(".m4v"),
        MOV(".mov"),
        WEBM(".webm"),
        MKV(".mkv"),
        AVI(".avi"),
        OGV(".ogv"),
        THREE_GP(".3gp");

        private final String[] extensions;

        Format(String... extensions) {
            this.extensions = extensions;
        }
    }
}
