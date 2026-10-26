package com.mine.geometry_node.client.feature.graph.document;

import com.mine.geometry_node.client.feature.graph.edit.FrameBoundsPolicy;
import com.mine.geometry_node.client.feature.asset.task.AssetTaskContext;
import com.mine.geometry_node.client.feature.asset.model.AssetTypeRegistry;
import com.mine.geometry_node.client.feature.graph.persistence.GraphJsonIO;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphDocumentStore;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphFileReference;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphFileRegistry;
import com.mine.geometry_node.core.node.document.NodeGraph;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public final class GraphDocumentLoader {
    private final FrameBoundsPolicy frameBoundsPolicy;

    public GraphDocumentLoader(FrameBoundsPolicy frameBoundsPolicy) {
        this.frameBoundsPolicy = Objects.requireNonNull(frameBoundsPolicy, "frameBoundsPolicy");
    }

    public GraphSession loadGraphSession(File file, AssetTaskContext context) throws Exception {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("graph file does not exist");
        }
        if (!AssetTypeRegistry.INSTANCE.isType(file, AssetTypeRegistry.GRAPH_ID)) {
            throw new IllegalArgumentException("only .json graph files can be opened");
        }

        context.progress("读取图纸", 0, 2);
        context.checkCancelled();
        GraphFileReference reference = GraphFileRegistry.INSTANCE.reference(file.toPath());
        for (int attempt = 0; attempt < 3; attempt++) {
            GraphDocumentStore.ReadSnapshot snapshot = GraphDocumentStore.INSTANCE.readSnapshot(reference);
            String content = snapshot.content().trim();

            context.progress("解析图纸", 1, 2);
            context.checkCancelled();
            NodeGraph graph = (content.isEmpty() || content.equals("{}"))
                    ? new NodeGraph()
                    : GraphJsonIO.fromJson(content);

            context.checkCancelled();
            if (GraphDocumentStore.INSTANCE.claimDocument(reference, snapshot.revision())) {
                try {
                    return new GraphSession(reference, graph, frameBoundsPolicy);
                } catch (RuntimeException | Error failure) {
                    reference.releaseDocument();
                    throw failure;
                }
            }
        }
        throw new IOException("graph changed repeatedly while it was being opened");
    }
}
