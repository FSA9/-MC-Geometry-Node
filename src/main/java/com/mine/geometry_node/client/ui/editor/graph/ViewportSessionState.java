package com.mine.geometry_node.client.ui.editor.graph;

import com.mine.geometry_node.client.ui.session.GraphSession;

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
        state.viewportX = session.viewportX;
        state.viewportY = session.viewportY;
        state.currentScale = session.currentScale;
        state.hasViewportState = session.hasViewportState;
        state.selectedNodeIds.addAll(session.selectedNodeIds);
        state.selectedFrameIds.addAll(session.selectedFrameIds);
        return state;
    }

    void seedSessionDefaults(GraphSession session) {
        if (session == null || session.hasViewportState) {
            return;
        }
        session.viewportX = viewportX;
        session.viewportY = viewportY;
        session.currentScale = currentScale;
        session.hasViewportState = hasViewportState;
        session.selectedNodeIds.clear();
        session.selectedNodeIds.addAll(selectedNodeIds);
        session.selectedFrameIds.clear();
        session.selectedFrameIds.addAll(selectedFrameIds);
    }
}
