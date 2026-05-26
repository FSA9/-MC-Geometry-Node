package com.mine.geometry_node.client.ui.viewport.visual;

/**
 * Minimal node endpoint API needed by connection rendering.
 */
public interface ConnectionNodeVisual {
    String getNodeId();

    float getUiX();

    float getUiY();

    void getPortPosition(String portId, boolean isInput, float[] outPos);
}
