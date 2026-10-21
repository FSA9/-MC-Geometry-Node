package com.mine.geometry_node.client.ui.editor.graph.toolbar;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
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

    ViewportToolButton(Context context, String tooltipText, TooltipHost tooltipHost) {
        super(context);
        this.mTooltipText = tooltipText;
        this.mTooltipHost = tooltipHost;
        setWillNotDraw(false);
        setFocusable(true);
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
        return isEnabled() && isHovered();
    }

    void setTooltipText(String tooltipText) {
        mTooltipText = tooltipText;
        if (isToolHovered() && mTooltipHost != null && mTooltipText != null && !mTooltipText.isBlank()) {
            mTooltipHost.showToolTooltip(this, mTooltipText);
        }
    }

    @Override
    public boolean performClick() {
        return isEnabled() && super.performClick();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        setAlpha(isEnabled() ? 1.0f : UiMetrics.DISABLED_ALPHA);
        if (!isEnabled()) {
            setHovered(false);
            setPressed(false);
            if (mTooltipHost != null) mTooltipHost.hideToolTooltip(this);
        }
        invalidate();
    }

    private boolean handleHover(View view, MotionEvent event) {
        if (!isEnabled()) return false;
        int action = event.getAction();
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
            setHovered(true);
            if (mTooltipHost != null && mTooltipText != null && !mTooltipText.isBlank()) {
                mTooltipHost.showToolTooltip(this, mTooltipText);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_HOVER_EXIT) {
            setHovered(false);
            if (mTooltipHost != null) {
                mTooltipHost.hideToolTooltip(this);
            }
            return true;
        }
        return false;
    }
}
