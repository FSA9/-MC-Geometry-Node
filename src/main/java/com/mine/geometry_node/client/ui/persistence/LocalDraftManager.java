package com.mine.geometry_node.client.ui.persistence;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;

/**
 * [本地草稿管理器]
 * 仅在客户端 (Client) 运行，负责将 UI 中的图纸保存到本地电脑。
 * 路径: .minecraft/geometry_nodes/local_drafts/
 */
public class LocalDraftManager {

    public static void saveDraft(String graphName, String jsonContent) {
        try {
            // 1. 获取客户端的根目录 (.minecraft)
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
            // 2. 构建本地草稿文件夹路径
            File folder = gameDir.resolve("geometry_nodes").resolve("local_drafts").toFile();

            // 确保文件夹存在
            if (!folder.exists() && !folder.mkdirs()) {
                System.err.println("[LocalDraftManager] 无法创建本地草稿目录!");
                return;
            }

            // 3. 处理文件名 (如果没有名字，默认叫 untitled)
            String safeName = (graphName == null || graphName.trim().isEmpty()) ? "untitled" : graphName;
            // 替换掉非法字符，防止系统报错
            safeName = safeName.replaceAll("[\\\\/:*?\"<>|]", "_");

            File file = new File(folder, safeName + ".json");

            // 4. 将 JSON 写入文件
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(jsonContent);
            }

            // 5. 在客户端左下角弹个绿字提示 (只有自己能看见)
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§a[💾 本地保存]§r 成功保存草稿: " + file.getName()), false
                );
            }

            System.out.println("Saved local draft to: " + file.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("[LocalDraftManager] 保存本地草稿失败: " + e.getMessage());
            e.printStackTrace();

            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[💾 保存失败]§r 请查看控制台日志。"), false
                );
            }
        }
    }

    /**
     * 获取本地所有草稿的名称列表 (不带 .json 后缀)
     */
    public static java.util.List<String> getAllDraftNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        try {
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
            File folder = gameDir.resolve("geometry_nodes").resolve("local_drafts").toFile();

            if (folder.exists() && folder.isDirectory()) {
                File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
                if (files != null) {
                    for (File file : files) {
                        names.add(file.getName().replace(".json", ""));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[LocalDraftManager] 读取草稿列表失败: " + e.getMessage());
        }
        return names;
    }

    /**
     * 根据名称读取本地草稿的 JSON 内容
     */
    public static String readDraft(String graphName) {
        try {
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
            File file = gameDir.resolve("geometry_nodes").resolve("local_drafts").resolve(graphName + ".json").toFile();

            if (file.exists()) {
                return java.nio.file.Files.readString(file.toPath());
            }
        } catch (Exception e) {
            System.err.println("[LocalDraftManager] 读取草稿失败: " + graphName);
        }
        return null;
    }
}