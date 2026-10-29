package com.mine.geometry_node.client.integration.graph;

import com.mine.geometry_node.client.ui.components.integration.graph.properties.GraphPropertiesSnapshot;
import com.mine.geometry_node.client.ui.components.integration.graph.properties.GraphPropertiesTarget;
import com.mine.geometry_node.client.feature.graph.persistence.GraphTagIO;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphFileReference;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphFileRegistry;
import com.mine.geometry_node.client.ui.document.DocumentManager;
import com.mine.geometry_node.client.feature.graph.document.GraphSession;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Exposes a local graph file, preferring its open document session, through the shared properties target contract.
 */
public final class GraphFilePropertiesTarget implements GraphPropertiesTarget {
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-GraphProperties-IO");
        thread.setDaemon(true);
        return thread;
    });

    private final GraphFileReference mFileReference;
    private final Runnable mOnSaved;

    public GraphFilePropertiesTarget(Path file, Runnable onSaved) {
        mFileReference = GraphFileRegistry.INSTANCE.reference(file);
        mOnSaved = onSaved;
    }

    @Override
    public CompletionStage<GraphPropertiesSnapshot> load() {
        GraphSessionPropertiesTarget sessionTarget = openSessionTarget();
        if (sessionTarget != null) {
            return sessionTarget.load();
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                GraphTagIO.GraphMetadata metadata = GraphTagIO.readMetadata(mFileReference);
                return new GraphPropertiesSnapshot(
                        mFileReference.requireActivePath().getFileName().toString(),
                        metadata.graphTypeId(),
                        normalizeComment(metadata.comment()),
                        metadata.tags(),
                        metadata.questDefinition());
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, IO_EXECUTOR);
    }

    @Override
    public CompletionStage<Void> save(String graphTypeId, String comment, List<String> tags,
                                      QuestDefinition questDefinition) {
        GraphSessionPropertiesTarget sessionTarget = openSessionTarget();
        if (sessionTarget != null) {
            return sessionTarget.save(graphTypeId, comment, tags, questDefinition);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                GraphTagIO.writeMetadata(mFileReference, graphTypeId, comment, tags, questDefinition);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, IO_EXECUTOR);
    }

    @Override
    public String normalizeComment(String comment) {
        return comment != null ? comment.trim() : "";
    }

    @Override
    public void onSaveSucceeded(GraphPropertiesSnapshot snapshot) {
        if (mOnSaved != null) mOnSaved.run();
    }

    private GraphSessionPropertiesTarget openSessionTarget() {
        GraphSession session = DocumentManager.INSTANCE.findSession(mFileReference);
        return session != null ? new GraphSessionPropertiesTarget(session) : null;
    }
}
