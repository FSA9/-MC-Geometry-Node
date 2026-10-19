package com.mine.geometry_node.client.ui.components.base.menu;

import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.area.AreaLifecycle;
import com.mine.geometry_node.client.ui.workspace.area.AreaTransientUi;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Matrix;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

/** Shared popup input and lifetime. Subclasses own their content and placement policy. */
public abstract class UiPopupOverlay extends FrameLayout {
    private final UiMenuFocus focus = new UiMenuFocus();
    private final Matrix inputMatrix = new Matrix();
    private final float[] inputPoint = new float[2];
    private AreaLifecycle.Subscription binding = AreaLifecycle.Subscription.NONE;
    private boolean outsidePressed;
    private boolean dismissPending;
    private final Runnable deferredDismiss = () -> {
        if (dismissPending) finishDismiss();
    };

    protected UiPopupOverlay(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnClickListener(view -> dismiss());
    }

    /** Call after dismissing the previous presentation and configuring the content. */
    protected final boolean attachOverlay(View owner, ViewGroup host) {
        if (owner == null || host == null || dismissPending || getParent() != null || host.isInLayout()) return false;
        setVisibility(VISIBLE);
        focus.capture(owner);
        host.addView(this, new FrameLayout.LayoutParams(-1, -1));
        binding = AreaTransientUi.bind(owner, this, this::dismiss);
        requestFocus();
        return true;
    }

    protected abstract boolean containsPopup(float localX, float localY);

    /** Stop listeners and queued content work here. May run again on external detachment. */
    protected void onDismissRequested() {}

    public boolean isOpen() {
        return !dismissPending && getVisibility() == VISIBLE && getParent() != null;
    }

    public void dismiss() {
        dismiss(false);
    }

    private void dismiss(boolean deferRemoval) {
        outsidePressed = false;
        binding.close();
        binding = AreaLifecycle.Subscription.NONE;
        onDismissRequested();
        focus.restoreAndClear(this);
        if (getParent() != null) clearTooltipState(this);
        if (getParent() != null && (deferRemoval || isInLayout())) {
            // Layout and window-focus dispatch both traverse the host's child array.
            if (!dismissPending) {
                dismissPending = true;
                setVisibility(GONE);
                post(deferredDismiss);
            }
            return;
        }
        finishDismiss();
    }

    private void finishDismiss() {
        removeCallbacks(deferredDismiss);
        dismissPending = false;
        if (getParent() instanceof ViewGroup host) host.removeView(this);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(deferredDismiss);
        dismissPending = false;
        outsidePressed = false;
        binding.close();
        binding = AreaLifecycle.Subscription.NONE;
        onDismissRequested();
        focus.restoreAndClear(this);
        clearTooltipState(this);
        super.onDetachedFromWindow();
    }

    /** Rebase once at the overlay boundary so native children receive painted coordinates. */
    private MotionEvent localEvent(MotionEvent source) {
        if (!UIUtils.rootToLocal(this, source.getRawX(), source.getRawY(), inputMatrix, inputPoint)) return null;
        MotionEvent event = MotionEvent.obtain(source.getEventTimeNano(), source.getAction(), source.getActionButton(),
                inputPoint[0], inputPoint[1], source.getModifiers(), source.getButtonState(), 0);
        event.setAxisValue(MotionEvent.AXIS_VSCROLL, source.getAxisValue(MotionEvent.AXIS_VSCROLL));
        event.setAxisValue(MotionEvent.AXIS_HSCROLL, source.getAxisValue(MotionEvent.AXIS_HSCROLL));
        return event;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent source) {
        if (!isOpen()) return false;
        MotionEvent event = localEvent(source);
        if (event == null) return false;
        try {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                outsidePressed = !containsPopup(event.getX(), event.getY());
            }
            if (outsidePressed) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) dismiss();
                else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) outsidePressed = false;
                return true;
            }
            return super.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent source) {
        if (!isOpen()) return false;
        MotionEvent event = localEvent(source);
        if (event == null) return false;
        try {
            return super.dispatchGenericMotionEvent(event);
        } finally {
            event.recycle();
        }
    }

    /** Also supports owner clicks outside the overlay's host, such as a toolbar button. */
    public boolean dismissIfOutside(float rootX, float rootY) {
        if (!isOpen() || !UIUtils.rootToLocal(this, rootX, rootY, inputMatrix, inputPoint)
                || containsPopup(inputPoint[0], inputPoint[1])) return false;
        dismiss();
        return true;
    }

    private static void clearTooltipState(View view) {
        CharSequence text = view.getTooltipText();
        if (view instanceof UiTextInput input) {
            input.clearTooltipPresentation();
        } else if (text != null) {
            view.setTooltipText(null);
            view.setTooltipText(text);
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) clearTooltipState(group.getChildAt(i));
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEY_ESCAPE) {
            dismiss();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (key == KeyEvent.KEY_ESCAPE) {
            dismiss();
            return true;
        }
        return super.onKeyDown(key, event);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) dismiss(true);
    }
}
