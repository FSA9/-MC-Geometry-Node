package com.mine.geometry_node.client.ui.document;

import com.mine.geometry_node.client.feature.graph.document.GraphSession;
import com.mine.geometry_node.client.history.HistoryState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** UI defaults shared by a document's viewports; each viewport still owns its independent state. */
public final class GraphSessionViewState {
    private static final Map<GraphSession, GraphSessionViewState> STATES = new WeakHashMap<>();
    private Path namedPath;

    public String tabName;
    public float viewportX;
    public float viewportY;
    public float currentScale = 1.0f;
    public boolean hasViewportState;
    public final List<String> selectedNodeIds = new ArrayList<>();
    public final List<String> selectedFrameIds = new ArrayList<>();

    private GraphSessionViewState(GraphSession session) { refreshTabName(session); }

    public static GraphSessionViewState of(GraphSession session) {
        return STATES.computeIfAbsent(session, GraphSessionViewState::new);
    }

    public static void forget(GraphSession session) { STATES.remove(session); }

    static void discardClosed() {
        STATES.keySet().removeIf(session -> session.editorContext.getCommandManager()
                .historyState().phase() == HistoryState.Phase.CLOSED);
    }

    void refreshFileReference(GraphSession session) {
        if (!session.filePath().equals(namedPath)) refreshTabName(session);
    }

    public void refreshTabName(GraphSession session) {
        Path path = session.filePath();
        namedPath = path;
        tabName = path.getFileName() != null ? path.getFileName().toString() : path.toString();
    }
}
