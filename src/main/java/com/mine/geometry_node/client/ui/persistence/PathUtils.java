package com.mine.geometry_node.client.ui.persistence;

import java.io.File;

public class PathUtils {

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
        return new File(getWorkspaceRoot(), "geometry_nodes" + File.separator + "local_drafts");
    }
}