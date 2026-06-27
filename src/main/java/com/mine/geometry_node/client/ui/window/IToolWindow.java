package com.mine.geometry_node.client.ui.window;

import icyllis.modernui.view.View;

/**
 * 工具窗口内容接口。
 */
public interface IToolWindow {
    View getView();

    void onShow();

    void onHide();
}
