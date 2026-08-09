package com.mine.geometry_node.client.ui.shell.layer.ephemeral;

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
