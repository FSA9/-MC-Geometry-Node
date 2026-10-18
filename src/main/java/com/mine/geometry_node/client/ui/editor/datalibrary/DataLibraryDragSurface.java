package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.components.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.table.UiTableStyle;
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
        gesture = new WorkspaceDragGesture(getContext(), new WorkspaceDragGesture.Listener() {
            @Override public void onPressed(MotionEvent event) {
                pressedAction.run();
            }

            @Override public void onDragStarted(MotionEvent event) {
                dragging = true;
                MotionEvent cancel = event.copy();
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                try {
                    dispatchToChildren(cancel);
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

    private boolean dispatchToChildren(MotionEvent event) {
        return super.dispatchTouchEvent(event);
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
        rowPaint.setColor(UiTableStyle.DIVIDER_COLOR);
        canvas.drawRect(0, getHeight() - Math.max(1, UIUtils.dp2pxInt(1)), getWidth(), getHeight(), rowPaint);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        shiftPressed = event.isShiftPressed();
        ctrlPressed = event.isCtrlPressed();
        if (action == MotionEvent.ACTION_DOWN) {
            secondaryPressed = (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
                    || event.getActionButton() == MotionEvent.BUTTON_SECONDARY;
            if (secondaryPressed) return true;
            if ((event.getButtonState() & MotionEvent.BUTTON_TERTIARY) != 0) return false;
            pressed = true;
            dragging = false;
            gesture.onTouch(this, event);
            dispatchToChildren(event);
            return true;
        }
        if (secondaryPressed) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                secondaryPressed = false;
                if (action == MotionEvent.ACTION_UP && contextMenu != null) contextMenu.accept(event);
            }
            return true;
        }
        if (!pressed) return dispatchToChildren(event);
        gesture.onTouch(this, event);
        if (!dragging) dispatchToChildren(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pressed = false;
            dragging = false;
        }
        return true;
    }
}
