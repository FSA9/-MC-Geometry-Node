package com.mine.geometry_node.client.ui.area;

import icyllis.modernui.view.View;

/**
 * Area 布局中承载编辑器内容的窗口协议。
 */
public interface AreaEditorWindow {
    View getView();

    void onShow();

    void onHide();
}
