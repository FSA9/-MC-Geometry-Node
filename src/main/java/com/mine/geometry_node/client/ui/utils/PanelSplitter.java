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
     * @param isVertical true为垂直分割线(左右拖拽)，false为水平分割线(上下拖拽)
     * 注意：现在统一通过改变相邻元素的 weight 来实现缩放，无需再传入 targetView
     */
    public static View create(Context context, boolean isVertical) {
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
                        performResize(v, dx, true);
                    } else {
                        // 注意这里是向上推为负，向下拉为正，与 dy 方向一致
                        performResize(v, dy, false);
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

    /**
     * 统一的权重缩放处理逻辑
     * @param splitter 分割线 View本身
     * @param delta    移动的差值 (dx 或 dy)
     * @param isWidth  是否在处理宽度 (true 为宽度，false 为高度)
     */
    private static void performResize(View splitter, float delta, boolean isWidth) {
        ViewGroup parent = (ViewGroup) splitter.getParent();
        if (!(parent instanceof LinearLayout)) return;

        int index = parent.indexOfChild(splitter);

        if (index > 0 && index < parent.getChildCount() - 1) {
            View firstView = parent.getChildAt(index - 1);
            View secondView = parent.getChildAt(index + 1);

            LinearLayout.LayoutParams firstParams = (LinearLayout.LayoutParams) firstView.getLayoutParams();
            LinearLayout.LayoutParams secondParams = (LinearLayout.LayoutParams) secondView.getLayoutParams();

            if (firstParams.weight > 0 && secondParams.weight > 0) {
                float totalWeight = firstParams.weight + secondParams.weight;
                float totalSize = isWidth ?
                        (firstView.getWidth() + secondView.getWidth()) :
                        (firstView.getHeight() + secondView.getHeight());

                if (totalSize <= 0) return;

                float dWeight = (delta / totalSize) * totalWeight;
                firstParams.weight += dWeight;
                secondParams.weight -= dWeight;

                float minW = UIConstants.MainUI.WEIGHT_MIN;
                if (firstParams.weight < minW) {
                    secondParams.weight -= (minW - firstParams.weight);
                    firstParams.weight = minW;
                }
                if (secondParams.weight < minW) {
                    firstParams.weight -= (minW - secondParams.weight);
                    secondParams.weight = minW;
                }

                firstView.requestLayout();
                secondView.requestLayout();
            }
        }
    }
}