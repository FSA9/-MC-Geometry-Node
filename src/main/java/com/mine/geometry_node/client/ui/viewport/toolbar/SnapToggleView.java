package com.mine.geometry_node.client.ui.viewport.toolbar;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;

final class SnapToggleView extends ViewportToolButton {
    private static final int COLOR_BG_OFF = 0xFF252A33;
    private static final int COLOR_BG_ON = 0xFF1F5D8F;
    private static final int COLOR_BORDER_OFF = 0xFF4B5565;
    private static final int COLOR_BORDER_ON = 0xFF84D2FF;
    private static final int COLOR_GRID_OFF = 0xFF8992A3;
    private static final int COLOR_GRID_ON = 0xFFFFFFFF;
    private static final int COLOR_POINT_OFF = 0xFF5D6674;
    private static final int COLOR_POINT_ON = 0xFFBFEAFF;
    private static final int COLOR_HIGHLIGHT = 0x33FFFFFF;

    private final Paint mPaint = new Paint();
    private final RectF mRect = new RectF();
    SnapToggleView(Context context, TooltipHost tooltipHost) {
        super(context, "吸附到网格\n开启后节点和图框会对齐网格", tooltipHost);
        mPaint.setAntiAlias(true);
    }

    void setSnapEnabled(boolean enabled) {
        setToolActive(enabled);
    }

    boolean isSnapEnabled() {
        return isToolActive();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        boolean active = isToolActive();
        boolean hovered = isToolHovered();
        float minSize = Math.min(w, h);
        float pad = Math.max(1.0f, minSize * 0.08f);
        float stroke = Math.max(1.0f, UIUtils.dp2px(1.0f));
        float left = pad;
        float top = pad;
        float right = w - pad;
        float bottom = h - pad;
        float radius = minSize * 0.22f;

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(active ? COLOR_BG_ON : COLOR_BG_OFF);
        mRect.set(left, top, right, bottom);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        if (active || hovered) {
            mPaint.setColor(COLOR_HIGHLIGHT);
            mRect.set(left + stroke, top + stroke, right - stroke, top + minSize * 0.38f);
            canvas.drawRoundRect(mRect, radius * 0.8f, radius * 0.8f, radius * 0.8f, radius * 0.8f, mPaint);
        }

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setColor(active ? COLOR_BORDER_ON : (hovered ? COLOR_GRID_OFF : COLOR_BORDER_OFF));
        mRect.set(left + stroke * 0.5f, top + stroke * 0.5f, right - stroke * 0.5f, bottom - stroke * 0.5f);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        mPaint.setColor(active ? COLOR_GRID_ON : COLOR_GRID_OFF);
        mPaint.setAlpha(active ? 230 : (hovered ? 190 : 145));
        mPaint.setStrokeWidth(Math.max(1.0f, minSize * 0.055f));
        float iconInset = minSize * 0.28f;
        float iconLeft = left + iconInset;
        float iconTop = top + iconInset;
        float iconRight = right - iconInset;
        float iconBottom = bottom - iconInset;
        float thirdW = (iconRight - iconLeft) / 3.0f;
        float thirdH = (iconBottom - iconTop) / 3.0f;
        for (int i = 1; i < 3; i++) {
            float x = iconLeft + thirdW * i;
            float y = iconTop + thirdH * i;
            canvas.drawLine(x, iconTop, x, iconBottom, mPaint);
            canvas.drawLine(iconLeft, y, iconRight, y, mPaint);
        }

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(active ? COLOR_POINT_ON : COLOR_POINT_OFF);
        mPaint.setAlpha(255);
        float pointSize = Math.max(1.0f, minSize * (active ? 0.16f : 0.12f));
        float cx = (iconLeft + iconRight) * 0.5f;
        float cy = (iconTop + iconBottom) * 0.5f;
        canvas.drawRect(cx - pointSize * 0.5f, cy - pointSize * 0.5f, cx + pointSize * 0.5f, cy + pointSize * 0.5f, mPaint);
        mPaint.setAlpha(255);
    }
}
