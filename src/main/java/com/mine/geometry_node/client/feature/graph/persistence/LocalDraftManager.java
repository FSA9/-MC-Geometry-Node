package com.mine.geometry_node.client.feature.graph.persistence;

import com.mine.geometry_node.core.engine.graph.storage.GraphPathMapper;
import com.mine.geometry_node.core.engine.system.asset.type.AssetTypeCatalog;

import com.mine.geometry_node.client.feature.asset.path.AssetBrowserPathPolicy;

import java.io.File;
import java.io.IOException;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphDocumentStore;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphFileRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class LocalDraftManager {
    private static Path getDraftFolder() {
        return AssetBrowserPathPolicy.getCommonDir().toPath();
    }

    public static Path resolveDraftPath(String graphId) {
        Path folder = getDraftFolder().toAbsolutePath().normalize();
        Path resolved = folder.resolve(GraphPathMapper.idToRelativePath(graphId)).normalize();
        if (!resolved.startsWith(folder)) {
            throw new IllegalArgumentException("invalid draft path: " + graphId);
        }
        return resolved;
    }

    /** Saves a file-owned draft; active documents must be saved through their session. */
    public static Path saveDraft(String graphId, String jsonContent) throws IOException {
        var reference = GraphFileRegistry.INSTANCE.reference(resolveDraftPath(graphId));
        GraphDocumentStore.INSTANCE.writeUnclaimedStringAtomic(reference, jsonContent);
        return reference.path();
    }

    public static List<String> getAllDraftNames() {
        List<String> names = new ArrayList<>();
        try {
            Path root = getDraftFolder();
            if (Files.exists(root) && Files.isDirectory(root)) {
                // 深度递归遍历所有文件
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> AssetTypeCatalog.isType(p, AssetTypeCatalog.GRAPH_TYPE_ID))
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
            Path resolved = resolveDraftPath(graphId);
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
