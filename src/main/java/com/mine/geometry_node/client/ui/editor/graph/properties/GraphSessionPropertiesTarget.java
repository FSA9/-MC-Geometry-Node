package com.mine.geometry_node.client.ui.editor.graph.properties;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdSetGraphMetadata;
import com.mine.geometry_node.client.ui.editor.properties.GraphPropertiesSnapshot;
import com.mine.geometry_node.client.ui.editor.properties.GraphPropertiesTarget;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.core.node.NodeGraph;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Exposes the active graph session through the shared properties target contract.
 */
public final class GraphSessionPropertiesTarget implements GraphPropertiesTarget, EditorContext.EditorListener {
    private final GraphSession mSession;
    private Runnable mChangeListener;
    private boolean mListening;

    public GraphSessionPropertiesTarget(GraphSession session) {
        mSession = session;
    }

    @Override
    public CompletionStage<GraphPropertiesSnapshot> load() {
        NodeGraph graph = mSession != null && mSession.editorContext != null
                ? mSession.editorContext.getGraph()
                : null;
        if (graph == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.completedFuture(new GraphPropertiesSnapshot(
                mSession.tabName,
                graph.getKind(),
                graph.comment,
                graph.tags));
    }

    @Override
    public CompletionStage<Void> save(String comment, List<String> tags) {
        if (mSession == null || mSession.editorContext == null || mSession.editorContext.getGraph() == null) {
            return CompletableFuture.completedFuture(null);
        }
        mSession.editorContext.getCommandManager().execute(new CmdSetGraphMetadata(
                mSession.editorContext.getGraphController(),
                comment,
                List.copyOf(tags)));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void setChangeListener(Runnable listener) {
        if (mListening) {
            mSession.editorContext.removeListener(this);
            mListening = false;
        }
        mChangeListener = listener;
        if (listener != null && mSession != null && mSession.editorContext != null) {
            mSession.editorContext.addListener(this);
            mListening = true;
        }
    }

    @Override
    public void onGraphMetadataChanged() {
        if (mChangeListener != null) mChangeListener.run();
    }
}
