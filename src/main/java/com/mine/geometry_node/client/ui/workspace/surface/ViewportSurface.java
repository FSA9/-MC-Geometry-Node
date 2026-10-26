package com.mine.geometry_node.client.ui.workspace.surface;

import com.mine.geometry_node.client.feature.graph.document.GraphSession;

import java.util.List;

/** UI-side viewport context used by the MCP adapter without exposing it to runtime-neutral AI code. */
public interface ViewportSurface extends HistorySurface {
    @Override
    default com.mine.geometry_node.client.history.HistoryTarget historyTarget() {
        GraphSession session = currentGraphSession();
        return session == null ? null : session.editorContext.getCommandManager();
    }

    GraphSession currentGraphSession();

    /** Sessions represented by this viewport's tab strip, including background tabs. */
    List<GraphSession> openGraphSessions();
}
