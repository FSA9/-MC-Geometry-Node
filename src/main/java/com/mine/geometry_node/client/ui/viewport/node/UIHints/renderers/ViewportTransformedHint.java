package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

/** Hint whose raw Minecraft rendering must follow the viewport's current window-space transform. */
public interface ViewportTransformedHint {
    void setViewportTransform(float scale, float windowLeftPx, float windowTopPx);

    default void setViewportTransform(float scale, float windowLeftPx, float windowTopPx, long previewOrder) {
        setViewportTransform(scale, windowLeftPx, windowTopPx);
    }
}
