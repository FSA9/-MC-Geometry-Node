package com.mine.geometry_node.client.ui.overlay.modal;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import icyllis.modernui.view.View;

public record ModalOptions(
        boolean closeOnOutsideClick,
        boolean closeOnEscape,
        View returnFocusTarget,
        int scrimColor
) {
    public static final int DEFAULT_SCRIM_COLOR = UiThemes.EDITOR.surface().modalScrim();

    public ModalOptions(boolean closeOnOutsideClick, boolean closeOnEscape, View returnFocusTarget) {
        this(closeOnOutsideClick, closeOnEscape, returnFocusTarget, DEFAULT_SCRIM_COLOR);
    }

    public static ModalOptions defaults() {
        return new ModalOptions(false, true, null, DEFAULT_SCRIM_COLOR);
    }
}
