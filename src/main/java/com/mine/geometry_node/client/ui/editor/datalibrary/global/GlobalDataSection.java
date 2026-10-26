package com.mine.geometry_node.client.ui.editor.datalibrary.global;

import icyllis.modernui.view.View;

/** A global-data category owns its content and domain bindings, never the host navigation. */
public interface GlobalDataSection {
    View view();
    /** Resumes a retained category after a temporary hide; ignored after disposal. */
    void onShow();
    /** Suspends UI bindings while retaining presentation state; safe to repeat. */
    void onHide();
    /** Permanently releases bindings; idempotent and cannot be reversed by onShow(). */
    void onDispose();
}
