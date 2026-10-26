package com.mine.geometry_node.client.ui.editor.graph;

import com.mine.geometry_node.client.feature.graph.document.GraphSession;
import com.mine.geometry_node.client.ui.document.GraphSessionViewState;

import java.util.ArrayList;
import java.util.List;

final class ViewportSessionState {
    float viewportX;
    float viewportY;
    float currentScale;
    boolean hasViewportState;
    final List<String> selectedNodeIds = new ArrayList<>();
    final List<String> selectedFrameIds = new ArrayList<>();

    static ViewportSessionState fromSession(GraphSession session) {
        ViewportSessionState state = new ViewportSessionState();
        if (session == null) {
            state.currentScale = 1.0f;
            return state;
        }
        GraphSessionViewState defaults = GraphSessionViewState.of(session);
        state.viewportX = defaults.viewportX;
        state.viewportY = defaults.viewportY;
        state.currentScale = defaults.currentScale;
        state.hasViewportState = defaults.hasViewportState;
        state.selectedNodeIds.addAll(defaults.selectedNodeIds);
        state.selectedFrameIds.addAll(defaults.selectedFrameIds);
        return state;
    }

    void seedSessionDefaults(GraphSession session) {
        if (session == null) return;
        GraphSessionViewState defaults = GraphSessionViewState.of(session);
        if (defaults.hasViewportState) return;
        defaults.viewportX = viewportX;
        defaults.viewportY = viewportY;
        defaults.currentScale = currentScale;
        defaults.hasViewportState = hasViewportState;
        defaults.selectedNodeIds.clear();
        defaults.selectedNodeIds.addAll(selectedNodeIds);
        defaults.selectedFrameIds.clear();
        defaults.selectedFrameIds.addAll(selectedFrameIds);
    }
}
