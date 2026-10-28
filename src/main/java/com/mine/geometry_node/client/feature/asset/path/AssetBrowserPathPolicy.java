package com.mine.geometry_node.client.feature.asset.path;

import com.mine.geometry_node.client.platform.ClientPaths;
import com.mine.geometry_node.core.engine.system.asset.AssetStorageLayout;
import java.io.IOException;
import java.io.UncheckedIOException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AssetBrowserPathPolicy {

    private AssetBrowserPathPolicy() {
    }

    public static File getCommonDir() {
        try {
            return AssetStorageLayout.prepareLocal(ClientPaths.getWorkspaceRoot().toPath()).toFile();
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to migrate the local common asset directory", error);
        }
    }

    public static File getPacksDir() {
        try {
            Path workspace = ClientPaths.getWorkspaceRoot().toPath();
            AssetStorageLayout.prepareLocal(workspace);
            return Files.createDirectories(AssetStorageLayout.packsRoot(workspace)).toFile();
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to prepare the local packs directory", error);
        }
    }

    public static File[] listRootDirectories() {
        File[] roots = File.listRoots();
        return roots != null ? roots : new File[0];
    }

    public static File resolveConfigPath(String path) {
        if (path == null || path.isBlank()) return null;
        String normalizedPath = AssetStorageLayout.migrateLocalReference(path.trim());
        if (!normalizedPath.equals(path.trim())) getCommonDir();
        File file = new File(normalizedPath);
        return file.isAbsolute() ? file : ClientPaths.resolveWorkspacePath(normalizedPath);
    }

    public static String toConfigPath(File file) {
        if (file == null) return "";
        try {
            Path root = ClientPaths.getWorkspaceRoot().toPath().toRealPath();
            Path target = file.toPath().toRealPath();
            if (target.startsWith(root)) {
                return normalizeSeparators(root.relativize(target).toString());
            }
            return target.toString();
        } catch (Exception ignored) {
            try {
                Path root = ClientPaths.getWorkspaceRoot().toPath().toAbsolutePath().normalize();
                Path target = file.toPath().toAbsolutePath().normalize();
                if (target.startsWith(root)) {
                    return normalizeSeparators(root.relativize(target).toString());
                }
                return target.toString();
            } catch (Exception ignoredAgain) {
                return file.getPath();
            }
        }
    }

    public static boolean canPersistQuickAccessPath(String path) {
        File file = resolveConfigPath(path);
        return file != null
                && file.exists()
                && file.isDirectory()
                && !isCommonPath(path)
                && !isPacksPath(path)
                && !isRootPath(path);
    }

    public static boolean isCommonPath(String path) {
        File file = resolveConfigPath(path);
        return file != null && ClientPaths.sameFile(file, getCommonDir());
    }

    public static boolean isPacksPath(String path) {
        File file = resolveConfigPath(path);
        return file != null && ClientPaths.sameFile(file, getPacksDir());
    }

    public static boolean isRootPath(String path) {
        File file = resolveConfigPath(path);
        if (file == null) return false;
        try {
            Path target = file.toPath().toAbsolutePath().normalize();
            for (File root : listRootDirectories()) {
                if (target.equals(root.toPath().toAbsolutePath().normalize())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String normalizeSeparators(String path) {
        return path == null ? "" : path.replace(File.separatorChar, '/');
    }
}
