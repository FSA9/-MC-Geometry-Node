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

    public WorkspaceDragGesture(Context context, Listener listener) {
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.listener = listener;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (event == null || listener == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                downX = event.getRawX();
                downY = event.getRawY();
                moved = false;
                dragging = false;
                disallowParentIntercept(view, true);
                listener.onPressed(event);
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!dragging && distanceFromDown(event) >= touchSlop) {
                    moved = true;
                    dragging = true;
                    listener.onDragStarted(event);
                }
                if (dragging) listener.onDragged(event);
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                listener.onReleased(event, moved);
                disallowParentIntercept(view, false);
                moved = false;
                dragging = false;
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                listener.onCancelled(event);
                disallowParentIntercept(view, false);
                moved = false;
                dragging = false;
                return true;
            }
            default -> {
                return true;
            }
        }
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
