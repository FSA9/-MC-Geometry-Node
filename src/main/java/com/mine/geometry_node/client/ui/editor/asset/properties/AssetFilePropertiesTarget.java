package com.mine.geometry_node.client.ui.editor.asset.properties;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeRegistry;
import com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.GraphPropertiesSnapshot;
import com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.GraphPropertiesTarget;
import com.mine.geometry_node.client.ui.editor.graph.properties.GraphSessionPropertiesTarget;
import com.mine.geometry_node.client.ui.persistence.GraphTagIO;
import com.mine.geometry_node.client.ui.persistence.graphfile.GraphFileReference;
import com.mine.geometry_node.client.ui.persistence.graphfile.GraphFileRegistry;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Exposes one selected local graph file through the shared properties target contract.
 */
public final class AssetFilePropertiesTarget implements GraphPropertiesTarget {
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-GraphProperties-IO");
        thread.setDaemon(true);
        return thread;
    });

    private final GraphFileReference mFileReference;
    private final Runnable mOnSaved;

    private AssetFilePropertiesTarget(File file, Runnable onSaved) {
        mFileReference = GraphFileRegistry.INSTANCE.reference(file.toPath());
        mOnSaved = onSaved;
    }

    public static AssetFilePropertiesTarget fromSelection(List<AssetEntry> entries, Runnable onSaved) {
        File file = resolveSelectedFile(entries);
        return file != null ? new AssetFilePropertiesTarget(file, onSaved) : null;
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
                        metadata.questDefinition(),
                        metadata.conditionOverview());
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

    private static File resolveSelectedFile(List<AssetEntry> entries) {
        if (entries == null || entries.size() != 1) return null;
        AssetEntry entry = entries.get(0);
        if (entry == null || entry.sourceKind() != AssetSourceKind.LOCAL
                || !AssetTypeRegistry.INSTANCE.isType(entry, AssetTypeRegistry.GRAPH_ID)
                || entry.localFile() == null || !entry.localFile().isFile()) {
            return null;
        }
        return entry.localFile();
    }

    private GraphSessionPropertiesTarget openSessionTarget() {
        GraphSession session = DocumentManager.INSTANCE.findSession(mFileReference);
        return session != null ? new GraphSessionPropertiesTarget(session) : null;
    }
}
