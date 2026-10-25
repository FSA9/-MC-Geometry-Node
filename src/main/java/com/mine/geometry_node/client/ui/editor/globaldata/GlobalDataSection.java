package com.mine.geometry_node.client.ui.editor.globaldata;

import icyllis.modernui.view.View;

/** A global-data category owns its content and domain bindings, never the host navigation. */
public interface GlobalDataSection {
    View view();
    void onShow();
    void onHide();
    void onDispose();
}
