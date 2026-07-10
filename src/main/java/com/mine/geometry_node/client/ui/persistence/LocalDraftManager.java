package com.mine.geometry_node.client.ui.persistence;

import com.mine.geometry_node.core.engine.graph.storage.GraphPathMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class LocalDraftManager {
    private static Path getDraftFolder() {
        return AssetBrowserPathPolicy.getLocalDraftsDir().toPath();
    }

    public static void saveDraft(String graphId, String jsonContent) {
        try {
            Path folder = getDraftFolder();
            // 1. 将 ID (A:B/C) 转化为相对路径 (A/B/C.json)
            Path relativePath = GraphPathMapper.idToRelativePath(graphId);
            Path resolved = folder.toAbsolutePath().normalize().resolve(relativePath).normalize();
            if (!resolved.startsWith(folder.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("invalid draft path: " + graphId);
            }
            File file = resolved.toFile();

            // 2. 自动创建所有缺失的父级文件夹 (关键！)
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                System.err.println("[LocalDraftManager] 无法创建子目录: " + parentDir);
                return;
            }

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(jsonContent);
            }

            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§a[本地保存]§r 成功保存至: " + relativePath));
            }
        } catch (Exception e) {
            System.err.println("[LocalDraftManager] 保存失败: " + e.getMessage());
        }
    }

    public static List<String> getAllDraftNames() {
        List<String> names = new ArrayList<>();
        try {
            Path root = getDraftFolder();
            if (Files.exists(root) && Files.isDirectory(root)) {
                // 深度递归遍历所有文件
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".json"))
                            .forEach(p -> names.add(GraphPathMapper.pathToId(root, p)));
                }
            }
        } catch (Exception e) {
            System.err.println("[LocalDraftManager] 遍历草稿失败: " + e.getMessage());
        }
        return names;
    }

    public static String readDraft(String graphId) {
        try {
            Path root = getDraftFolder();
            Path relativePath = GraphPathMapper.idToRelativePath(graphId);
            Path resolved = root.toAbsolutePath().normalize().resolve(relativePath).normalize();
            if (!resolved.startsWith(root.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("invalid draft path: " + graphId);
            }
            File file = resolved.toFile();

            if (file.exists()) {
                return Files.readString(file.toPath());
            }
        } catch (Exception e) {
            System.err.println("[LocalDraftManager] 读取失败: " + graphId);
        }
        return null;
    }
}
