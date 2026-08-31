package com.mine.geometry_node.client.ui.editor.asset;

import com.mine.geometry_node.core.engine.system.asset.AssetRelativePath;

public final class AssetPathUtils {
    private AssetPathUtils() {
    }

    public static String normalizeRemoteDirectory(String directory) {
        String normalized = trimRemoteRelativePath(directory);
        return AssetRelativePath.normalize(normalized, true);
    }

    public static String normalizeRemoteFilePath(String path) {
        return AssetRelativePath.normalize(trimRemoteRelativePath(path), false);
    }

    public static boolean isRemotePathInput(String path) {
        return path != null && path.trim().toLowerCase(java.util.Locale.ROOT).startsWith("remote:");
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
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        boolean remotePrefix = true;
        if (lower.startsWith("remote://")) {
            normalized = normalized.substring("remote://".length());
        } else if (lower.startsWith("remote:/")) {
            normalized = normalized.substring("remote:/".length());
        } else if (lower.startsWith("remote:")) {
            normalized = normalized.substring("remote:".length());
        } else {
            remotePrefix = false;
        }
        while (remotePrefix && normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
