package com.mine.geometry_node.client.ui.document;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.persistence.graphfile.GraphFileReference;
import com.mine.geometry_node.core.node.document.NodeGraph;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GraphSession implements AutoCloseable {
    private final UUID mSessionId = UUID.randomUUID();
    private final GraphFileReference mFileReference;
    public String tabName;
    public boolean isDirty = false;
    public final EditorContext editorContext;

    // --- 相机状态 ---
    public float viewportX = 0;
    public float viewportY = 0;
    public float currentScale = 1.0f;
    public boolean hasViewportState = false;

    // --- 【关键】只存储选中图元的 ID，不再存储 UI 对象 ---
    public final List<String> selectedNodeIds = new ArrayList<>();
    public final List<String> selectedFrameIds = new ArrayList<>();

    // Session owns one claimed graph-file reference and does not hold UI objects.
    public GraphSession(GraphFileReference fileReference, NodeGraph graph) {
        mFileReference = fileReference;
        Path path = fileReference.requireActivePath();
        this.tabName = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        this.editorContext = new EditorContext(graph);
        this.editorContext.getCommandManager().setDirtyListener(() -> {
            if (!isDirty) {
                isDirty = true;
                DocumentManager.INSTANCE.notifyTabChanged();
            }
        });
    }

    public GraphFileReference fileReference() {
        return mFileReference;
    }

    /** Runtime-only identity. It is never persisted and cannot authorize a reopened document. */
    public UUID sessionId() {
        return mSessionId;
    }

    public long revision() {
        return editorContext.getCommandManager().revision();
    }

    public Path filePath() {
        return mFileReference.path();
    }

    public void refreshTabName() {
        Path path = mFileReference.path();
        tabName = path.getFileName() != null ? path.getFileName().toString() : path.toString();
    }

    @Override
    public void close() {
        mFileReference.releaseDocument();
    }
}
