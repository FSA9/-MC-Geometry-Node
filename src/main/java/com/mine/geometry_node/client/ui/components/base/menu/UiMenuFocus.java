package com.mine.geometry_node.client.ui.components.base.menu;

import com.mine.geometry_node.client.ui.workspace.area.AreaLifecycle;
import icyllis.modernui.view.View;

/** Remembers the source focus and transfers it before a focused popup is removed. */
public final class UiMenuFocus {
    private View anchor;
    private View returnFocus;
    private AreaLifecycle area;

    public void capture(View owner) {
        anchor = owner;
        area = AreaLifecycle.find(owner);
        View focused = owner.getRootView().findFocus();
        returnFocus = focused != null && AreaLifecycle.find(focused) == area ? focused : null;
    }

    public void restoreAndClear(View popup) {
        try {
            if (!popup.hasFocus() || anchor == null || anchor.getRootView() != popup.getRootView()
                    || AreaLifecycle.find(anchor) != area || area != null && !area.isActive()) return;
            // Native removal otherwise searches the entire root for a replacement.
            // A deliberate focus change outside the popup must always take precedence.
            if (returnFocus != null && returnFocus.getRootView() == popup.getRootView()
                    && AreaLifecycle.find(returnFocus) == area && returnFocus.requestFocus()) return;
            for (View candidate = anchor; candidate != null;
                 candidate = candidate.getParent() instanceof View parent ? parent : null) {
                if (AreaLifecycle.find(candidate) != area) break;
                if (candidate.requestFocus()) return;
            }
        } finally {
            anchor = returnFocus = null;
            area = null;
        }
    }
}
