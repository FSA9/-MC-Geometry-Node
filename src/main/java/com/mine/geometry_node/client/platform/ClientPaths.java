package com.mine.geometry_node.client.platform;

import java.io.File;

public class ClientPaths {
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
     * 获取本模组配置目录（游戏目录下的 config/geometry）
     */
    public static File getConfigDir() {
        return new File(new File(getWorkspaceRoot(), "config"), "geometry");
    }

    /**
     * 获取具体的配置文件
     */
    public static File getConfigFile() {
        return new File(getConfigDir(), "geometry_node_config.json");
    }

    public static File getEditorSessionFile() {
        return new File(getConfigDir(), "geometry_node_editor_session.json");
    }

    public static File resolveWorkspacePath(String path) {
        return new File(getWorkspaceRoot(), path.replace('/', File.separatorChar));
    }

    public static boolean sameFile(File left, File right) {
        try {
            return left.getCanonicalFile().equals(right.getCanonicalFile());
        } catch (Exception ignored) {
            return left.getAbsoluteFile().equals(right.getAbsoluteFile());
        }
    }
}
