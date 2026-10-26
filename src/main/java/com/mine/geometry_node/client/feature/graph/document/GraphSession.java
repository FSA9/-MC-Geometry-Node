package com.mine.geometry_node.client.feature.graph.document;

import com.mine.geometry_node.client.feature.graph.edit.EditorContext;
import com.mine.geometry_node.client.feature.graph.edit.FrameBoundsPolicy;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphFileReference;
import com.mine.geometry_node.core.node.document.NodeGraph;
import java.nio.file.Path;
import java.util.UUID;

public class GraphSession implements AutoCloseable {
    private final UUID mSessionId = UUID.randomUUID();
    private final GraphFileReference mFileReference;
    public boolean isDirty = false;
    private boolean closed;
    public final EditorContext editorContext;

    // Session owns one claimed graph-file reference and does not hold UI objects.
    public GraphSession(GraphFileReference fileReference, NodeGraph graph, FrameBoundsPolicy frameBoundsPolicy) {
        mFileReference = fileReference;
        fileReference.requireActivePath();
        this.editorContext = new EditorContext(graph, mSessionId.toString(), frameBoundsPolicy);
        this.editorContext.getCommandManager().subscribeHistory(state -> {
            if (isDirty != state.dirty()) {
                isDirty = state.dirty();
                GraphDocumentManager.INSTANCE.notifyChanged();
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

    @Override
    public void close() {
        if (closed) return;
        editorContext.getCommandManager().close();
        closed = true;
        mFileReference.releaseDocument();
    }
}
