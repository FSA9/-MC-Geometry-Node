package com.mine.geometry_node.client.ui.editor.asset.properties;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.properties.GraphPropertiesSnapshot;
import com.mine.geometry_node.client.ui.editor.properties.GraphPropertiesTarget;
import com.mine.geometry_node.client.ui.persistence.GraphTagIO;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.core.engine.quest.model.QuestDefinition;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private final File mFile;
    private final Runnable mOnSaved;

    private AssetFilePropertiesTarget(File file, Runnable onSaved) {
        mFile = file;
        mOnSaved = onSaved;
    }

    public static AssetFilePropertiesTarget fromSelection(List<AssetEntry> entries, Runnable onSaved) {
        File file = resolveSelectedFile(entries);
        return file != null ? new AssetFilePropertiesTarget(file, onSaved) : null;
    }

    @Override
    public CompletionStage<GraphPropertiesSnapshot> load() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GraphTagIO.GraphMetadata metadata = GraphTagIO.readMetadata(mFile);
                return new GraphPropertiesSnapshot(
                        mFile.getName(),
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
        return CompletableFuture.runAsync(() -> {
            try {
                GraphTagIO.writeMetadata(mFile, graphTypeId, comment, tags, questDefinition);
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
        syncOpenSession(mFile, snapshot.graphTypeId(), snapshot.comment(), snapshot.tags(), snapshot.questDefinition());
        if (mOnSaved != null) mOnSaved.run();
    }

    private static File resolveSelectedFile(List<AssetEntry> entries) {
        if (entries == null || entries.size() != 1) return null;
        AssetEntry entry = entries.get(0);
        if (entry == null || entry.sourceKind() != AssetSourceKind.LOCAL
                || !entry.isJsonFile() || entry.localFile() == null || !entry.localFile().isFile()) {
            return null;
        }
        return entry.localFile();
    }

    private static void syncOpenSession(File file, String graphTypeId, String comment, List<String> tags,
                                        QuestDefinition questDefinition) {
        for (GraphSession session : DocumentManager.INSTANCE.getSessions()) {
            if (session == null || session.fileId == null
                    || session.editorContext == null || session.editorContext.getGraph() == null) {
                continue;
            }
            if (!sameFile(file, new File(session.fileId))) continue;
            session.editorContext.getGraph().graphKind = graphTypeId;
            session.editorContext.getGraph().comment = comment;
            session.editorContext.getGraph().tags = new ArrayList<>(tags);
            session.editorContext.getGraph().quest = questDefinition;
            session.editorContext.notifyGraphMetadataChanged();
        }
    }

    private static boolean sameFile(File first, File second) {
        if (first == null || second == null) return false;
        try {
            String firstPath = first.getCanonicalPath();
            String secondPath = second.getCanonicalPath();
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            return windows ? firstPath.equalsIgnoreCase(secondPath) : firstPath.equals(secondPath);
        } catch (Exception ignored) {
            return first.getAbsoluteFile().equals(second.getAbsoluteFile());
        }
    }
}
