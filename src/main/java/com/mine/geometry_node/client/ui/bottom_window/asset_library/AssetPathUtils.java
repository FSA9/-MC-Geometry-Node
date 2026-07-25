package com.mine.geometry_node.client.ui.bottom_window.asset_library;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AssetPathUtils {
    private AssetPathUtils() {
    }

    public static String normalizeRemoteDirectory(String directory) {
        String normalized = trimRemoteRelativePath(directory);
        if (normalized.isEmpty()) return "";

        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) continue;
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    public static String normalizeRemoteFilePath(String path) {
        String normalized = trimRemoteRelativePath(path);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("file path must not be empty");
        }
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("invalid path: " + path);
            }
        }
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new IllegalArgumentException("only .json graph files can be transferred: " + path);
        }
        return normalized;
    }

    public static boolean isRemotePathInput(String path) {
        return path != null && path.trim().toLowerCase(Locale.ROOT).startsWith("remote:");
    }

    public static String remotePathFromInput(String path) {
        return normalizeRemoteDirectory(path);
    }

    public static String formatRemotePath(String directory) {
        String normalized = normalizeRemoteDirectory(directory);
        return "remote:/" + (normalized.isEmpty() ? "" : normalized);
    }

    private static String trimRemoteRelativePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path.trim())) return "";
        String normalized = path.replace('\\', '/').trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("remote://")) {
            normalized = normalized.substring("remote://".length());
        } else if (lower.startsWith("remote:/")) {
            normalized = normalized.substring("remote:/".length());
        } else if (lower.startsWith("remote:")) {
            normalized = normalized.substring("remote:".length());
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
