package com.mine.geometry_node.client.ui.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.Files;

public class ConfigManager {
    public static final ConfigManager INSTANCE = new ConfigManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private AppConfig currentConfig;

    private ConfigManager() {}

    public void initOrLoad() {
        File configFile = PathUtils.getConfigFile();
        if (!configFile.getParentFile().exists()) configFile.getParentFile().mkdirs();

        if (configFile.exists()) {
            try {
                String json = Files.readString(configFile.toPath());
                currentConfig = GSON.fromJson(json, AppConfig.class);
                if (currentConfig == null) currentConfig = new AppConfig();
                if (currentConfig.assetBrowser == null) currentConfig.assetBrowser = new AppConfig.AssetBrowserConfig();
            } catch (Exception e) {
                currentConfig = new AppConfig();
            }
        } else {
            currentConfig = new AppConfig();

            // 【核心修改】：如果是第一次运行（文件不存在），预填充草稿箱和所有的本地磁盘
            currentConfig.assetBrowser.quickAccessPaths.add(PathUtils.getLocalDraftsDir().getAbsolutePath());
            File[] roots = File.listRoots();
            if (roots != null) {
                for (File root : roots) {
                    currentConfig.assetBrowser.quickAccessPaths.add(root.getAbsolutePath());
                }
            }

            save(); // 初始化默认文件
        }

        // 运行时容错校验：自动清理已经被用户在系统里删掉的“死路径”
        currentConfig.assetBrowser.quickAccessPaths.removeIf(pathStr -> {
            File f = new File(pathStr);
            return !f.exists() || !f.isDirectory();
        });
    }

    public void save() {
        try {
            String json = GSON.toJson(currentConfig);
            Files.writeString(PathUtils.getConfigFile().toPath(), json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public AppConfig getConfig() {
        return currentConfig;
    }
}