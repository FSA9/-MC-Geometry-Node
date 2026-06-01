package com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.drag.AssetDragDropRegistry;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

abstract class AssetDialogBase extends FrameLayout {
    protected final LinearLayout mPanel;
    private final LinearLayout mWindow;
    private float mDragStartRawX;
    private float mDragStartRawY;
    private int mDragStartLeft;
    private int mDragStartTop;
    private boolean mDragging;
    private boolean mRegisteredDragBlocker;

    AssetDialogBase(Context context, String title) {
        super(context);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setBackground(rect(0x33000000, 0));
        setOnClickListener(v -> {});

        mWindow = new LinearLayout(context);
        mWindow.setOrientation(LinearLayout.VERTICAL);
        mWindow.setBackground(rect(0xFF2B2B2B, 6));
        mWindow.setOnClickListener(v -> {});

        LinearLayout titleBar = new LinearLayout(context);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(UIUtils.dp2pxInt(12), 0, UIUtils.dp2pxInt(6), 0);
        titleBar.setBackground(rect(0xFF242424, 6));
        titleBar.setOnTouchListener(this::onTitleBarTouch);

        TextView titleView = label(context, title, 15, 0xFFE6E6E6);
        titleBar.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView close = label(context, "x", 15, 0xFFE6E6E6);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dismiss());
        titleBar.addView(close, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(32), ViewGroup.LayoutParams.MATCH_PARENT));
        mWindow.addView(titleBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(34)));

        mPanel = new LinearLayout(context);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        mPanel.setPadding(UIUtils.dp2pxInt(14), UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(14), UIUtils.dp2pxInt(12));
        mWindow.addView(mPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(520), ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.CENTER;
        addView(mWindow, panelLp);
    }

    public void showIn(ViewGroup parent) {
        ViewGroup host = findWindowHost(parent);
        host.addView(this, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (!mRegisteredDragBlocker) {
            AssetDragDropRegistry.pushModalBlocker();
            mRegisteredDragBlocker = true;
        }
    }

    public void dismiss() {
        releaseDragBlocker();
        if (getParent() instanceof ViewGroup parent) {
            parent.removeView(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseDragBlocker();
        super.onDetachedFromWindow();
    }

    protected TextView label(Context context, String text, float size, int color) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        return tv;
    }

    protected Button button(Context context, String text, int color) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(0xFFFFFFFF);
        button.setBackground(rect(color, 4));
        return button;
    }

    protected ShapeDrawable rect(int color, float radiusDp) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        return drawable;
    }

    private ViewGroup findWindowHost(ViewGroup parent) {
        View current = parent;
        ViewGroup best = parent;
        while (current != null) {
            if (current instanceof FrameLayout frameLayout) {
                best = frameLayout;
            }
            if (!(current.getParent() instanceof View)) {
                break;
            }
            current = (View) current.getParent();
        }
        return best;
    }

    private void releaseDragBlocker() {
        if (!mRegisteredDragBlocker) return;
        AssetDragDropRegistry.popModalBlocker();
        mRegisteredDragBlocker = false;
    }

    private boolean onTitleBarTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragStartRawX = event.getRawX();
                mDragStartRawY = event.getRawY();
                FrameLayout.LayoutParams downLp = (FrameLayout.LayoutParams) mWindow.getLayoutParams();
                ensurePanelHasAbsolutePosition(downLp);
                mDragStartLeft = downLp.leftMargin;
                mDragStartTop = downLp.topMargin;
                mDragging = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging) return true;
                FrameLayout.LayoutParams moveLp = (FrameLayout.LayoutParams) mWindow.getLayoutParams();
                int targetLeft = mDragStartLeft + Math.round(event.getRawX() - mDragStartRawX);
                int targetTop = mDragStartTop + Math.round(event.getRawY() - mDragStartRawY);
                moveLp.gravity = Gravity.TOP | Gravity.LEFT;
                moveLp.leftMargin = clamp(targetLeft, 0, Math.max(0, getWidth() - mWindow.getWidth()));
                moveLp.topMargin = clamp(targetTop, 0, Math.max(0, getHeight() - mWindow.getHeight()));
                mWindow.setLayoutParams(moveLp);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDragging = false;
                return true;
            default:
                return true;
        }
    }

    private void ensurePanelHasAbsolutePosition(FrameLayout.LayoutParams lp) {
        if (lp.gravity == Gravity.CENTER) {
            int left = mWindow.getLeft();
            int top = mWindow.getTop();
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.leftMargin = left;
            lp.topMargin = top;
            mWindow.setLayoutParams(lp);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
