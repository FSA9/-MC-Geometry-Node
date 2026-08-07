package com.mine.geometry_node.client.ui.editor.sidebar.api;

import icyllis.modernui.view.View;

/**
 * One content panel represented by a tab in an editor sidebar.
 */
public interface SidebarPanel {
    View getView();

    default void onSelected() {
    }

    default void onDeselected() {
    }
}
