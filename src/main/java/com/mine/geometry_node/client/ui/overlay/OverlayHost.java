package com.mine.geometry_node.client.ui.overlay;

import com.mine.geometry_node.client.ui.overlay.modal.ModalOptions;
import com.mine.geometry_node.client.ui.overlay.modal.UiModal;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;

/** Host capability used by reusable overlays; concrete shells own mounting and lifecycle. */
public interface OverlayHost {
    ViewGroup overlayRoot();

    OverlayHandle showModal(UiModal overlay, ModalOptions options);

    OverlayHandle showTooltip(UiOverlay overlay, View owner);

    static OverlayHost find(View anchor) {
        for (View current = anchor; current != null;
             current = current.getParent() instanceof View parent ? parent : null) {
            if (current instanceof OverlayHost host) return host;
        }
        return null;
    }
}
