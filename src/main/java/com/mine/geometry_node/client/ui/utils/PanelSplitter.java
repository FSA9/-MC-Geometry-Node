package com.mine.geometry_node.client.ui.utils;

import com.mine.geometry_node.client.ui.UIConstants;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

/**
 * 全局统一的拖拽分割线生成器
 */
public class PanelSplitter {

    /**
     * 创建一个可拖拽的分割线
     *
     * @param context    上下文
     * @param isVertical true为垂直分割线(左右拖拽改变权重)，false为水平分割线(上下拖拽改变targetView高度)
     * @param targetView 仅水平拖拽时需要传入，表示需要改变高度的目标View
     */
    public static View create(Context context, boolean isVertical, View targetView) {
        FrameLayout container = new FrameLayout(context);
        int hitSize = UIUtils.dp2pxInt(UIConstants.MainUI.SPLITTER_HITBOX_SIZE);

        if (isVertical) {
            container.setLayoutParams(new LinearLayout.LayoutParams(hitSize, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            container.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, hitSize));
        }

        View visualLine = new View(context);
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.RECTANGLE);
        drawable.setColor(UIConstants.MainUI.BG_SPLITTER);
        visualLine.setBackground(drawable);

        int visualSize = UIUtils.dp2pxInt(UIConstants.MainUI.SPLITTER_VISUAL_SIZE);

        FrameLayout.LayoutParams lineParams = isVertical
                ? new FrameLayout.LayoutParams(visualSize, ViewGroup.LayoutParams.MATCH_PARENT)
                : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, visualSize);
        lineParams.gravity = Gravity.CENTER;

        container.addView(visualLine, lineParams);

        // 状态保存
        final float[] lastTouch = new float[2];
        final boolean[] isDragging = new boolean[]{false};

        container.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDragging[0] = true;
                    lastTouch[0] = event.getRawX();
                    lastTouch[1] = event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (!isDragging[0]) return false;

                    float rawX = event.getRawX();
                    float rawY = event.getRawY();
                    float dx = rawX - lastTouch[0];
                    float dy = rawY - lastTouch[1];

                    if (isVertical) {
                        performVerticalResize(v, dx);
                    } else {
                        performHorizontalResize(targetView, -dy);
                    }

                    lastTouch[0] = rawX;
                    lastTouch[1] = rawY;
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging[0] = false;
                    return true;
            }
            return false;
        });

        return container;
    }

    private static void performVerticalResize(View splitter, float dx) {
        ViewGroup parent = (ViewGroup) splitter.getParent();
        if (!(parent instanceof LinearLayout)) return;

        int index = parent.indexOfChild(splitter);

        if (index > 0 && index < parent.getChildCount() - 1) {
            View leftView = parent.getChildAt(index - 1);
            View rightView = parent.getChildAt(index + 1);

            LinearLayout.LayoutParams leftParams = (LinearLayout.LayoutParams) leftView.getLayoutParams();
            LinearLayout.LayoutParams rightParams = (LinearLayout.LayoutParams) rightView.getLayoutParams();

            if (leftParams.weight > 0 && rightParams.weight > 0) {
                float totalWeight = leftParams.weight + rightParams.weight;
                float totalWidth = leftView.getWidth() + rightView.getWidth();

                if (totalWidth <= 0) return;

                float dWeight = (dx / totalWidth) * totalWeight;
                leftParams.weight += dWeight;
                rightParams.weight -= dWeight;

                float minW = UIConstants.MainUI.WEIGHT_MIN;
                if (leftParams.weight < minW) {
                    rightParams.weight -= (minW - leftParams.weight);
                    leftParams.weight = minW;
                }
                if (rightParams.weight < minW) {
                    leftParams.weight -= (minW - rightParams.weight);
                    rightParams.weight = minW;
                }

                leftView.requestLayout();
                rightView.requestLayout();
            }
        }
    }

    private static void performHorizontalResize(View targetView, float dy) {
        if (targetView == null) return;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) targetView.getLayoutParams();
        params.height += (int) dy;

        int minHeight = UIUtils.dp2pxInt(UIConstants.MainUI.HEIGHT_BOTTOM_MIN);
        if (params.height < minHeight) params.height = minHeight;

        targetView.setLayoutParams(params);
    }
}