package com.mine.geometry_node.client.ui.common;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

public final class ResizableDivider extends FrameLayout {
    public enum Orientation {
        HORIZONTAL,
        VERTICAL
    }

    public interface DragListener {
        void onDividerDrag(float delta);
    }

    private static final int COLOR_DIVIDER = 0xFF070707;
    private static final int COLOR_DIVIDER_DRAG_FILL = 0x3344AAFF;
    private static final float HITBOX_DP = 8.0f;
    private static final float NORMAL_DP = 1.0f;
    private static final float DRAG_DP = 5.0f;

    private final Orientation mOrientation;
    private final DragListener mListener;
    private final boolean mResizeWeightedSiblings;
    private final View mVisualLine;
    private boolean mDragging;
    private float mLastRaw;

    public ResizableDivider(Context context, Orientation orientation, DragListener listener) {
        this(context, orientation, listener, false);
    }

    private ResizableDivider(Context context, Orientation orientation, DragListener listener, boolean resizeWeightedSiblings) {
        super(context);
        mOrientation = orientation != null ? orientation : Orientation.HORIZONTAL;
        mListener = listener;
        mResizeWeightedSiblings = resizeWeightedSiblings;
        mVisualLine = new View(context);

        setZ(2.0f);
        updateVisual(false);
        addView(mVisualLine, visualParams(false));
        setOnTouchListener(this::handleTouch);
    }

    public static ResizableDivider weighted(Context context, Orientation orientation) {
        return weighted(context, orientation, null);
    }

    public static ResizableDivider weighted(
            Context context, Orientation orientation, DragListener listener) {
        ResizableDivider divider = new ResizableDivider(context, orientation, listener, true);
        divider.setLayoutParams(layoutParams(orientation));
        return divider;
    }

    public static LinearLayout.LayoutParams layoutParams(Orientation orientation) {
        int hit = UIUtils.dp2pxInt(HITBOX_DP);
        if (orientation == Orientation.HORIZONTAL) {
            return new LinearLayout.LayoutParams(hit, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, hit);
    }

    private boolean handleTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                mDragging = true;
                mLastRaw = rawCoordinate(event);
                updateVisual(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!mDragging) {
                    return false;
                }
                float raw = rawCoordinate(event);
                float delta = raw - mLastRaw;
                mLastRaw = raw;
                dispatchDrag(delta);
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mDragging = false;
                updateVisual(false);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void dispatchDrag(float delta) {
        if (mResizeWeightedSiblings) {
            resizeWeightedSiblings(delta);
        }
        if (mListener != null) {
            mListener.onDividerDrag(delta);
        }
    }

    private void resizeWeightedSiblings(float delta) {
        if (!(getParent() instanceof LinearLayout parent)) {
            return;
        }

        int index = parent.indexOfChild(this);
        if (index <= 0 || index >= parent.getChildCount() - 1) {
            return;
        }

        View firstView = parent.getChildAt(index - 1);
        View secondView = parent.getChildAt(index + 1);
        if (!(firstView.getLayoutParams() instanceof LinearLayout.LayoutParams firstParams)
                || !(secondView.getLayoutParams() instanceof LinearLayout.LayoutParams secondParams)) {
            return;
        }

        float totalWeight = firstParams.weight + secondParams.weight;
        if (totalWeight <= 0.0f) {
            return;
        }

        float totalSize = mOrientation == Orientation.HORIZONTAL
                ? firstView.getWidth() + secondView.getWidth()
                : firstView.getHeight() + secondView.getHeight();
        if (totalSize <= 0.0f) {
            return;
        }

        float minWeight = Math.min(UIConstants.MainUI.WEIGHT_MIN, totalWeight * 0.45f);
        float nextFirst = firstParams.weight + (delta / totalSize) * totalWeight;
        nextFirst = Math.max(minWeight, Math.min(totalWeight - minWeight, nextFirst));

        firstParams.weight = nextFirst;
        secondParams.weight = totalWeight - nextFirst;
        firstView.setLayoutParams(firstParams);
        secondView.setLayoutParams(secondParams);
        parent.requestLayout();
    }

    private float rawCoordinate(MotionEvent event) {
        return mOrientation == Orientation.HORIZONTAL ? event.getRawX() : event.getRawY();
    }

    private void updateVisual(boolean dragging) {
        setBackground(dragging ? rect(COLOR_DIVIDER_DRAG_FILL) : null);
        if (mVisualLine != null) {
            mVisualLine.setBackground(rounded(
                    dragging ? UIConstants.ViewPort.Selection.CLR_BORDER : COLOR_DIVIDER,
                    dragging ? 3.0f : 0.0f
            ));
            mVisualLine.setLayoutParams(visualParams(dragging));
        }
    }

    private FrameLayout.LayoutParams visualParams(boolean dragging) {
        int normal = UIUtils.dp2pxInt(NORMAL_DP);
        int drag = UIUtils.dp2pxInt(DRAG_DP);
        int thickness = dragging ? drag : normal;
        FrameLayout.LayoutParams params = mOrientation == Orientation.HORIZONTAL
                ? new FrameLayout.LayoutParams(thickness, ViewGroup.LayoutParams.MATCH_PARENT)
                : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, thickness);
        params.gravity = Gravity.CENTER;
        return params;
    }

    private static ShapeDrawable rect(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static ShapeDrawable rounded(int color, float radiusDp) {
        ShapeDrawable drawable = rect(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        return drawable;
    }
}
