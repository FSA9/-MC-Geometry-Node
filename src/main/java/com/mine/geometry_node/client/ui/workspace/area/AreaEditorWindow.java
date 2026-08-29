package com.mine.geometry_node.client.ui.workspace.area;

import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceId;
import icyllis.modernui.view.View;

/**
 * Area 布局中承载编辑器内容的窗口协议。
 */
public interface AreaEditorWindow {
    View getView();

    default UiSurfaceId surfaceId() {
        return null;
    }

    void onShow();

    void onHide();

    default void onDispose() {
        onHide();
    }
}
