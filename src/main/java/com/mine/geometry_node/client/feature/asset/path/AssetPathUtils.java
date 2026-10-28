package com.mine.geometry_node.client.feature.asset.path;

import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import com.mine.geometry_node.core.engine.system.asset.AssetRepositoryPath;

/** Display addresses reserve common/ and packs/ after remote:/; wire IDs stay unambiguous. */
public final class AssetPathUtils {
    private AssetPathUtils() {}

    public static String normalizeRemoteDirectory(String directory) {
        return AssetRepositoryPath.parse(trimRemoteRelativePath(directory), true).serialize();
    }

    public static String normalizeRemoteFilePath(String path) {
        return AssetRepositoryPath.parse(trimRemoteRelativePath(path), false).serialize();
    }

    public static boolean isRemotePathInput(String path) {
        return path != null && path.trim().toLowerCase(Locale.ROOT).startsWith("remote:");
    }

    public static String remotePathFromInput(String path) { return normalizeRemoteDirectory(path); }

    public static String formatRemotePath(String directory) {
        AssetRepositoryPath path = AssetRepositoryPath.parse(normalizeRemoteDirectory(directory), true);
        return "remote:/" + (path.isCommon() ? "common" : "packs")
                + (path.isRoot() ? "" : "/" + path.relativePath());
    }

    public static String parentRemoteDirectory(String path) {
        return AssetRepositoryPath.parse(normalizeRemoteDirectory(path), true).parent().serialize();
    }

    public static String resolveRemoteChild(String directory, String child) {
        return AssetRepositoryPath.parse(normalizeRemoteDirectory(directory), true).resolve(child).serialize();
    }

    public static Path resolveDownloadTarget(Path directory, String remotePath) {
        AssetRepositoryPath path = AssetRepositoryPath.parse(remotePath, false);
        Path root = directory.toAbsolutePath().normalize();
        Path target = root.resolve(path.relativePath()).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("download target escapes directory");
        Path current = target.getRoot();
        for (Path segment : target) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("download target must not contain symbolic links");
            }
        }
        return target;
    }

    private static String trimRemoteRelativePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path.trim())) return "";
        String value = path.replace('\\', '/').trim();
        boolean remotePrefix = isRemotePathInput(value);
        boolean display = remotePrefix && value.substring("remote:".length()).startsWith("/");
        if (remotePrefix) {
            value = value.substring("remote:".length());
            while (value.startsWith("/")) value = value.substring(1);
        }
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (display) {
            if (value.equals("common")) return "";
            if (value.startsWith("common/")) return value.substring("common/".length());
            if (value.equals("packs")) return new AssetRepositoryPath(AssetRepositoryPath.Root.WORLD_PACK, "").serialize();
            if (value.startsWith("packs/")) return new AssetRepositoryPath(AssetRepositoryPath.Root.WORLD_PACK,
                    value.substring("packs/".length())).serialize();
        }
        return value;
    }
}
