package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiMetrics;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragGesture;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragOperation;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragService;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Captures a card/tree gesture; no repository writes or selection changes happen here. */
final class DataLibraryDragSurface extends LinearLayout {
    private final WorkspaceDragGesture gesture;
    Consumer<MotionEvent> contextMenu;
    private boolean pressed;
    private boolean dragging;
    private boolean secondaryPressed;
    private final Paint rowPaint = new Paint();
    private boolean resultRow;
    private UiActionButton.Palette resultRowPalette;
    boolean shiftPressed;
    boolean ctrlPressed;

    DataLibraryDragSurface(Context context, View source, Supplier<DataLibraryDragPayload> payload,
                           Runnable pressedAction, Runnable finishAction, Consumer<MotionEvent> draggedAction) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        gesture = new WorkspaceDragGesture(getContext(), new WorkspaceDragGesture.Listener() {
            @Override public void onPressed(MotionEvent event) {
                pressedAction.run();
            }

            @Override public void onDragStarted(MotionEvent event) {
                dragging = true;
                MotionEvent cancel = event.copy();
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                try {
                    DataLibraryDragSurface.super.onTouchEvent(cancel);
                } finally {
                    cancel.recycle();
                }
                if (getParent() instanceof ViewGroup parent) parent.requestDisallowInterceptTouchEvent(true);
                setAlpha(0.6f);
                WorkspaceDragService.INSTANCE.begin(payload.get(), WorkspaceDragOperation.CONTEXTUAL,
                        source);
            }

            @Override public void onDragged(MotionEvent event) {
                draggedAction.accept(event);
            }

            @Override public void onReleased(MotionEvent event, boolean moved) {
                setAlpha(1.0f);
                if (moved && !WorkspaceDragService.INSTANCE.drop(event.getRawX(), event.getRawY())) {
                    WorkspaceDragService.INSTANCE.cancelIfSource(source);
                }
                finishAction.run();
            }

            @Override public void onCancelled(MotionEvent event) {
                setAlpha(1.0f);
                WorkspaceDragService.INSTANCE.cancelIfSource(source);
                finishAction.run();
            }
        });
    }

    void setResultRow() {
        resultRow = true;
        setWillNotDraw(false);
        invalidate();
    }

    void setResultRowPalette(UiActionButton.Palette palette) {
        resultRowPalette = palette;
        applyResultRowPalette();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        applyResultRowPalette();
    }

    private void applyResultRowPalette() {
        if (!resultRow || resultRowPalette == null) return;
        int color = !isEnabled() ? resultRowPalette.normal()
                : isPressed() ? resultRowPalette.pressed()
                : isHovered() ? resultRowPalette.hover() : resultRowPalette.normal();
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(color);
        setBackground(background);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!resultRow) return;
        rowPaint.setStyle(Paint.FILL);
        rowPaint.setColor(UiTableStyle.dividerColor(UiThemes.EDITOR));
        canvas.drawRect(0, getHeight() - Math.max(1, UIUtils.dp2pxInt(1)), getWidth(), getHeight(), rowPaint);
        if (isEnabled() && isFocused()) {
            float stroke = Math.max(1, UIUtils.dp2pxInt(UiMetrics.BORDER));
            rowPaint.setStyle(Paint.STROKE);
            rowPaint.setStrokeWidth(stroke);
            rowPaint.setColor(UiThemes.EDITOR.border().focus());
            canvas.drawRect(stroke / 2, stroke / 2, getWidth() - stroke / 2, getHeight() - stroke / 2, rowPaint);
        }
    }

    @Override
    public boolean performClick() {
        return isEnabled() && super.performClick();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        shiftPressed = event.isShiftPressed();
        ctrlPressed = event.isCtrlPressed();
        return super.dispatchTouchEvent(event);
    }

    /** Native dispatch lets an interactive child own its entire gesture before row fallback. */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            secondaryPressed = (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
                    || event.getActionButton() == MotionEvent.BUTTON_SECONDARY;
            if (secondaryPressed) return true;
            if ((event.getButtonState() & MotionEvent.BUTTON_TERTIARY) != 0) return false;
            pressed = true;
            dragging = false;
            gesture.onTouch(this, event);
            requestFocus();
            super.onTouchEvent(event);
            return true;
        }
        if (secondaryPressed) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                secondaryPressed = false;
                if (action == MotionEvent.ACTION_UP && contextMenu != null) contextMenu.accept(event);
            }
            return true;
        }
        if (!pressed) return false;
        gesture.onTouch(this, event);
        if (!dragging) super.onTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pressed = false;
            dragging = false;
        }
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        if (!focused) cancelGesture();
        super.onWindowFocusChanged(focused);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelGesture();
        super.onDetachedFromWindow();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled) cancelGesture();
        super.setEnabled(enabled);
    }

    private void cancelGesture() {
        if (gesture != null) gesture.cancel(this);
        pressed = false;
        dragging = false;
        secondaryPressed = false;
        setPressed(false);
    }
}
