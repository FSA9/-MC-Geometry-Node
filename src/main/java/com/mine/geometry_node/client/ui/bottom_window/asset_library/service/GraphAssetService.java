package com.mine.geometry_node.client.ui.bottom_window.asset_library.service;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.task.AssetTaskContext;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.core.node.NodeGraph;

import java.io.File;
import java.nio.file.Files;
import java.util.Locale;

public final class GraphAssetService {

    public GraphSession loadGraphSession(File file, AssetTaskContext context) throws Exception {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("graph file does not exist");
        }
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new IllegalArgumentException("only .json graph files can be opened");
        }

        context.progress("读取图纸", 0, 2);
        context.checkCancelled();
        String content = Files.readString(file.toPath()).trim();

        context.progress("解析图纸", 1, 2);
        context.checkCancelled();
        NodeGraph graph = (content.isEmpty() || content.equals("{}"))
                ? new NodeGraph()
                : GraphJsonIO.fromJson(content);

        context.checkCancelled();
        return new GraphSession(file.getAbsolutePath(), file.getName(), graph);
    }
}
