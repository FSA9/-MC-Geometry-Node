package com.mine.geometry_node.client.ui.components.composite.overlay;

import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

/** Native fallback host resolution for editors without a registered OverlayHost. */
public final class UiOverlayHosts {
    private UiOverlayHosts() {}

    /** Prefers the outermost FrameLayout; otherwise retains the anchor group or its parent.
     * This does not replace OverlayMount.host(), which resolves the registered overlay root.
     */
    public static ViewGroup findWindowHost(View anchor) {
        if (anchor == null) return null;
        ViewGroup best = anchor instanceof ViewGroup group ? group : null;
        for (View current = anchor; current != null;
             current = current.getParent() instanceof View parent ? parent : null) {
            if (current instanceof FrameLayout frame) best = frame;
        }
        return best != null ? best : anchor.getParent() instanceof ViewGroup parent ? parent : null;
    }
}
