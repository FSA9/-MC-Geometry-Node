package com.mine.geometry_node.client.ui.shell.layer;

/** Describes why an overlay is being asked to close. */
public enum OverlayCloseReason {
    PROGRAMMATIC,
    REPLACED,
    OUTSIDE_CLICK,
    ESCAPE,
    HOST_DESTROYED
}
