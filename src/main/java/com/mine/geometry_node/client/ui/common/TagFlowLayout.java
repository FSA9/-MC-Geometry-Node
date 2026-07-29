package com.mine.geometry_node.client.ui.common;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;

public class TagFlowLayout extends ViewGroup {
    private final int mHorizontalGap;
    private final int mVerticalGap;

    public TagFlowLayout(Context context) {
        this(context, 6, 6);
    }

    public TagFlowLayout(Context context, float horizontalGapDp, float verticalGapDp) {
        super(context);
        mHorizontalGap = UIUtils.dp2pxInt(horizontalGapDp);
        mVerticalGap = UIUtils.dp2pxInt(verticalGapDp);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int availableWidth = widthMode == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE / 4
                : Math.max(0, widthSize - getPaddingLeft() - getPaddingRight());

        int lineWidth = 0;
        int lineHeight = 0;
        int contentWidth = 0;
        int contentHeight = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;

            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();

            boolean wrap = lineWidth > 0 && lineWidth + mHorizontalGap + childWidth > availableWidth;
            if (wrap) {
                contentWidth = Math.max(contentWidth, lineWidth);
                contentHeight += lineHeight + mVerticalGap;
                lineWidth = childWidth;
                lineHeight = childHeight;
            } else {
                lineWidth += lineWidth > 0 ? mHorizontalGap + childWidth : childWidth;
                lineHeight = Math.max(lineHeight, childHeight);
            }
        }

        if (lineWidth > 0 || lineHeight > 0) {
            contentWidth = Math.max(contentWidth, lineWidth);
            contentHeight += lineHeight;
        }

        int measuredWidth = contentWidth + getPaddingLeft() + getPaddingRight();
        int measuredHeight = contentHeight + getPaddingTop() + getPaddingBottom();

        if (widthMode == MeasureSpec.EXACTLY) {
            measuredWidth = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            measuredWidth = Math.min(measuredWidth, widthSize);
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            measuredHeight = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            measuredHeight = Math.min(measuredHeight, heightSize);
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int availableWidth = Math.max(0, right - left - getPaddingLeft() - getPaddingRight());
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;

            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            if (x > getPaddingLeft() && x + mHorizontalGap + childWidth > getPaddingLeft() + availableWidth) {
                x = getPaddingLeft();
                y += lineHeight + mVerticalGap;
                lineHeight = 0;
            }

            child.layout(x, y, x + childWidth, y + childHeight);
            x += childWidth + mHorizontalGap;
            lineHeight = Math.max(lineHeight, childHeight);
        }
    }
}
