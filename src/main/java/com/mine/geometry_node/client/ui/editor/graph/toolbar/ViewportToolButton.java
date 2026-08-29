package com.mine.geometry_node.client.ui.editor.graph.toolbar;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;

abstract class ViewportToolButton extends View {
    interface TooltipHost {
        void showToolTooltip(View anchor, String text);
        void hideToolTooltip(View anchor);
    }

    private final TooltipHost mTooltipHost;
    private String mTooltipText;
    private boolean mActive;
    private boolean mHovered;

    ViewportToolButton(Context context, String tooltipText, TooltipHost tooltipHost) {
        super(context);
        this.mTooltipText = tooltipText;
        this.mTooltipHost = tooltipHost;
        setWillNotDraw(false);
        setOnHoverListener(this::handleHover);
    }

    void setToolActive(boolean active) {
        if (mActive == active) return;
        mActive = active;
        invalidate();
    }

    boolean isToolActive() {
        return mActive;
    }

    boolean isToolHovered() {
        return mHovered;
    }

    void setTooltipText(String tooltipText) {
        mTooltipText = tooltipText;
        if (mHovered && mTooltipHost != null && mTooltipText != null && !mTooltipText.isBlank()) {
            mTooltipHost.showToolTooltip(this, mTooltipText);
        }
    }

    private boolean handleHover(View view, MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
            if (!mHovered) {
                mHovered = true;
                invalidate();
            }
            if (mTooltipHost != null && mTooltipText != null && !mTooltipText.isBlank()) {
                mTooltipHost.showToolTooltip(this, mTooltipText);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_HOVER_EXIT) {
            if (mHovered) {
                mHovered = false;
                invalidate();
            }
            if (mTooltipHost != null) {
                mTooltipHost.hideToolTooltip(this);
            }
            return true;
        }
        return false;
    }
}
