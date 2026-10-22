package com.mine.geometry_node.client.ui.components.base.overlay;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.widget.FrameLayout;

/**
 * An input-owning layer: children get the first opportunity to handle the pointer,
 * then the layer stops dispatch from falling through to covered siblings.
 * Placement, dismissal, focus and lifetime belong to the presenting component.
 */
public class UiInputBarrier extends FrameLayout {
    public UiInputBarrier(Context context) {
        super(context);
    }

    /** Non-interactive presentations such as tooltips opt out of pointer ownership. */
    protected boolean blocksPointerInput() {
        return true;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (!blocksPointerInput()) return false;
        super.dispatchTouchEvent(event);
        return true;
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (!blocksPointerInput()) return false;
        super.dispatchGenericMotionEvent(event);
        return true;
    }

    @Override public PointerIcon onResolvePointerIcon(MotionEvent event) {
        if (!blocksPointerInput()) return null;
        PointerIcon icon = super.onResolvePointerIcon(event);
        // A null result would make ModernUI ask siblings below this layer.
        return icon != null ? icon : PointerIcon.getSystemIcon(PointerIcon.TYPE_DEFAULT);
    }
}
