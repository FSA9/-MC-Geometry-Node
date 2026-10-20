package com.mine.geometry_node.client.ui.overlay.ephemeral;

import icyllis.modernui.view.View;

public record TransientOptions(
        boolean closeOnOutsideClick,
        boolean closeOnEscape,
        View returnFocusTarget
) {
    public static TransientOptions defaults() {
        return new TransientOptions(true, true, null);
    }
}
