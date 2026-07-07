package com.mine.geometry_node.client.ui.persistence;

import java.io.File;
import java.nio.file.Path;

public class PathUtils {
    public static final String LOCAL_DRAFTS_RELATIVE_PATH = "geometry_nodes/local_drafts";

    /**
     * 获取基础工作空间根目录
     */
    public static File getWorkspaceRoot() {
        try {
            if (net.minecraft.client.Minecraft.getInstance() != null && net.minecraft.client.Minecraft.getInstance().gameDirectory != null) {
                return net.minecraft.client.Minecraft.getInstance().gameDirectory.getCanonicalFile();
            }
        } catch (Throwable ignored) {}

        try {
            return new File(System.getProperty("user.dir")).getCanonicalFile();
        } catch (Exception e) {
            return new File(System.getProperty("user.dir"));
        }
    }

    /**
     * 获取全局配置目录 (.config)
     */
    public static File getConfigDir() {
        return new File(getWorkspaceRoot(), ".config");
    }

    /**
     * 获取具体的配置文件
     */
    public static File getConfigFile() {
        return new File(getConfigDir(), "geometry_node_config.json");
    }

    /**
     * 获取草稿箱目录
     */
    public static File getLocalDraftsDir() {
        File dir = resolveGameRelativePath(LOCAL_DRAFTS_RELATIVE_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 将配置中的路径解析为本地文件。
     * 绝对路径保持原义；相对路径固定相对于当前游戏版本文件夹。
     */
    public static File resolveConfigPath(String path) {
        if (path == null || path.isBlank()) return null;
        File file = new File(path.trim());
        return file.isAbsolute() ? file : resolveGameRelativePath(path);
    }

    /**
     * 生成适合写入配置的路径。
     * 位于当前游戏目录下的路径会保存为相对路径，避免换机器后失效。
     */
    public static String toConfigPath(File file) {
        if (file == null) return "";
        try {
            Path root = getWorkspaceRoot().toPath().toRealPath();
            Path target = file.toPath().toRealPath();
            if (target.startsWith(root)) {
                return normalizeSeparators(root.relativize(target).toString());
            }
            return target.toString();
        } catch (Exception ignored) {
            try {
                Path root = getWorkspaceRoot().toPath().toAbsolutePath().normalize();
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

    public static String normalizeConfigPath(String path) {
        File file = resolveConfigPath(path);
        return file != null ? toConfigPath(file) : "";
    }

    public static boolean isLocalDraftsPath(String path) {
        File file = resolveConfigPath(path);
        return file != null && sameFile(file, getLocalDraftsDir());
    }

    public static boolean isRootPath(String path) {
        File file = resolveConfigPath(path);
        if (file == null) return false;
        try {
            Path target = file.toPath().toAbsolutePath().normalize();
            for (File root : listRoots()) {
                if (target.equals(root.toPath().toAbsolutePath().normalize())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static File[] listRoots() {
        File[] roots = File.listRoots();
        return roots != null ? roots : new File[0];
    }

    private static File resolveGameRelativePath(String path) {
        return new File(getWorkspaceRoot(), path.replace('/', File.separatorChar));
    }

    private static boolean sameFile(File left, File right) {
        try {
            return left.getCanonicalFile().equals(right.getCanonicalFile());
        } catch (Exception ignored) {
            return left.getAbsoluteFile().equals(right.getAbsoluteFile());
        }
    }

    private static String normalizeSeparators(String path) {
        return path == null ? "" : path.replace(File.separatorChar, '/');
    }
}
