package com.mine.geometry_node.client.ui.workspace.drag;

import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.core.Context;

/** Shared touch-slop gesture adapter for workspace drag sources. */
public final class WorkspaceDragGesture implements View.OnTouchListener {
    public interface Listener {
        void onPressed(MotionEvent event);
        void onDragStarted(MotionEvent event);
        void onDragged(MotionEvent event);
        void onReleased(MotionEvent event, boolean moved);
        void onCancelled(MotionEvent event);
    }

    private final float touchSlop;
    private final Listener listener;
    private float downX;
    private float downY;
    private boolean moved;
    private boolean dragging;
    private boolean active;
    private boolean mayDrag;

    public WorkspaceDragGesture(Context context, Listener listener) {
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.listener = listener;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (event == null || listener == null) return false;
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN && !active) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                if (active) cancel(view);
                downX = event.getRawX();
                downY = event.getRawY();
                moved = false;
                dragging = false;
                active = true;
                int buttons = event.getButtonState() | event.getActionButton();
                mayDrag = (buttons & (MotionEvent.BUTTON_SECONDARY | MotionEvent.BUTTON_TERTIARY)) == 0;
                disallowParentIntercept(view, true);
                listener.onPressed(event);
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!mayDrag) return true;
                if (!dragging && distanceFromDown(event) >= touchSlop) {
                    moved = true;
                    dragging = true;
                    listener.onDragStarted(event);
                }
                if (dragging) listener.onDragged(event);
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                active = false;
                disallowParentIntercept(view, false);
                boolean wasMoved = moved;
                moved = false;
                dragging = false;
                listener.onReleased(event, wasMoved);
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                active = false;
                disallowParentIntercept(view, false);
                moved = false;
                dragging = false;
                listener.onCancelled(event);
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    /** Retire a source on detach, disable or window focus loss without leaving a live drag. */
    public void cancel(View view) {
        if (!active) return;
        MotionEvent event = MotionEvent.obtain(System.nanoTime(), MotionEvent.ACTION_CANCEL, downX, downY, 0);
        try { onTouch(view, event); } finally { event.recycle(); }
    }

    private float distanceFromDown(MotionEvent event) {
        float dx = event.getRawX() - downX;
        float dy = event.getRawY() - downY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static void disallowParentIntercept(View view, boolean disallow) {
        if (view.getParent() instanceof ViewGroup parent) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }
}
