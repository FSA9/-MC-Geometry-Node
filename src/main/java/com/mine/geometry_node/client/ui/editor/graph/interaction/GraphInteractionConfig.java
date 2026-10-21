package com.mine.geometry_node.client.ui.editor.graph.interaction;

import com.mine.geometry_node.client.ui.editor.graph.node.NodeUiMetrics;

/** Graph-camera limits and gesture thresholds, independent of visual theme selection. */
public final class GraphInteractionConfig {
    private GraphInteractionConfig() {}

    public static final float ZOOM_MIN = 0.4f;
    public static final float ZOOM_MAX = 10.0f;
    public static final float ZOOM_SENSITIVITY = 0.1f;
    /** Logical UI distance; convert to physical pixels before comparing pointer movement. */
    public static final float TOUCH_SLOP_DP = 5.0f;
    public static final float MIN_DRAG_DISTANCE = 0.1f;
    private static final float PORT_HITBOX_RADIUS_RATIO = 1.1f;
    public static final float PORT_HITBOX_RADIUS = NodeUiMetrics.PORT_VISUAL_RADIUS * PORT_HITBOX_RADIUS_RATIO;
}
