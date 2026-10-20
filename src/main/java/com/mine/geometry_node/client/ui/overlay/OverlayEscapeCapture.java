package com.mine.geometry_node.client.ui.overlay;

import icyllis.modernui.view.KeyEvent;

/** A focused binding recorder can consume Escape before it dismisses its settings modal. */
public interface OverlayEscapeCapture {
    boolean captureEscape(KeyEvent event);
}
