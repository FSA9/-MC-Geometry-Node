package com.mine.geometry_node.client.ui.editor.graph.properties;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdSetGraphMetadata;
import com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.GraphPropertiesSnapshot;
import com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.GraphPropertiesTarget;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionKind;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionOverview;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.nodes.quest.CreateQuestCondition;

import java.util.List;
import java.util.Objects;
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
        mSession = Objects.requireNonNull(session, "session");
    }

    @Override
    public CompletionStage<GraphPropertiesSnapshot> load() {
        NodeGraph graph = mSession.editorContext.getGraph();
        if (graph == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.completedFuture(new GraphPropertiesSnapshot(
                mSession.tabName,
                graph.getGraphTypeId(),
                graph.comment,
                graph.tags,
                graph.quest,
                QuestConditionOverview.fromGraph(graph)));
    }

    @Override
    public CompletionStage<Void> save(String graphTypeId, String comment, List<String> tags,
                                      QuestDefinition questDefinition) {
        if (mSession.editorContext.getGraph() == null) {
            return CompletableFuture.completedFuture(null);
        }
        mSession.editorContext.getCommandManager().execute(new CmdSetGraphMetadata(
                mSession.editorContext.getGraphController(),
                graphTypeId,
                comment,
                List.copyOf(tags),
                questDefinition));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void setChangeListener(Runnable listener) {
        if (mListening) {
            mSession.editorContext.removeListener(this);
            mListening = false;
        }
        mChangeListener = listener;
        if (listener != null) {
            mSession.editorContext.addListener(this);
            mListening = true;
        }
    }

    @Override
    public void onGraphMetadataChanged() {
        notifyChanged();
    }

    @Override
    public void onNodeAdded(NodeData nodeData) {
        notifyChanged();
    }

    @Override
    public void onNodeRemoved(String nodeId) {
        notifyChanged();
    }

    @Override
    public void onNodeStructureChanged(NodeData nodeData) {
        if (isQuestConditionNode(nodeData)) notifyChanged();
    }

    @Override
    public void onConnectionAdded(String outNode, String outPort, String inNode, String inPort) {
        notifyChanged();
    }

    @Override
    public void onConnectionRemoved(String outNode, String outPort, String inNode, String inPort) {
        notifyChanged();
    }

    @Override
    public void onGraphConnectionsRebuildRequested() {
        notifyChanged();
    }

    @Override
    public void onGraphReloaded() {
        notifyChanged();
    }

    private static boolean isQuestConditionNode(NodeData nodeData) {
        if (nodeData == null || nodeData.type == null) return false;
        if (CreateQuestCondition.TYPE_ID.equals(nodeData.type)) return true;
        for (QuestConditionKind kind : QuestConditionKind.all()) {
            if (kind.nodeTypeId().equals(nodeData.type)) return true;
        }
        return false;
    }

    private void notifyChanged() {
        if (mChangeListener != null) mChangeListener.run();
    }
}
