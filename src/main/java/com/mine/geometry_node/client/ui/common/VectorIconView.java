package com.mine.geometry_node.client.ui.common;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.View;

public class VectorIconView extends View {
    public enum Kind {
        FOLDER,
        FILE,
        CLOUD,
        CHEVRON_UP,
        CHEVRON_DOWN,
        SPLIT_HORIZONTAL,
        SPLIT_VERTICAL,
        CLOSE
    }

    private static final int DEFAULT_COLOR = 0xFFB8C0CC;

    private final Paint mPaint = new Paint();
    private final RectF mRect = new RectF();
    private Kind mKind;
    private int mColor;

    public VectorIconView(Context context, Kind kind, int color) {
        super(context);
        mKind = kind;
        mColor = color;
        mPaint.setAntiAlias(true);
        setWillNotDraw(false);
    }

    public void setKind(Kind kind) {
        if (mKind == kind) {
            return;
        }
        mKind = kind;
        invalidate();
    }

    public void setIconColor(int color) {
        int resolved = color == 0 ? DEFAULT_COLOR : color;
        if (mColor == resolved) {
            return;
        }
        mColor = resolved;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h);
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float half = size * 0.36f;

        mPaint.setAlpha(255);
        mPaint.setColor(mColor == 0 ? DEFAULT_COLOR : mColor);

        switch (mKind) {
            case FOLDER -> drawFolder(canvas, cx, cy, half);
            case FILE -> drawFile(canvas, cx, cy, half);
            case CLOUD -> drawCloud(canvas, cx, cy, half);
            case CHEVRON_UP -> drawChevronUp(canvas, cx, cy, half);
            case CHEVRON_DOWN -> drawChevronDown(canvas, cx, cy, half);
            case SPLIT_HORIZONTAL -> drawSplitHorizontal(canvas, cx, cy, half);
            case SPLIT_VERTICAL -> drawSplitVertical(canvas, cx, cy, half);
            case CLOSE -> drawClose(canvas, cx, cy, half);
        }
    }

    private void drawFolder(Canvas canvas, float cx, float cy, float half) {
        float left = cx - half;
        float top = cy - half * 0.55f;
        float right = cx + half;
        float bottom = cy + half * 0.62f;
        float tabTop = top - half * 0.25f;
        float tabRight = left + half * 0.82f;
        float radius = Math.max(2.0f, half * 0.16f);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAlpha(190);
        mRect.set(left, tabTop, tabRight, top + half * 0.18f);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);
        mPaint.setAlpha(255);
        mRect.set(left, top, right, bottom);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);
        mPaint.setColor(0x33000000);
        mRect.set(left + half * 0.1f, top + half * 0.28f, right - half * 0.1f, bottom - half * 0.12f);
        canvas.drawRoundRect(mRect, radius * 0.8f, radius * 0.8f, radius * 0.8f, radius * 0.8f, mPaint);
    }

    private void drawFile(Canvas canvas, float cx, float cy, float half) {
        float left = cx - half * 0.68f;
        float top = cy - half * 0.86f;
        float right = cx + half * 0.68f;
        float bottom = cy + half * 0.86f;
        float fold = half * 0.34f;
        float radius = Math.max(2.0f, half * 0.12f);

        mPaint.setStyle(Paint.Style.FILL);
        mRect.set(left, top, right, bottom);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        mPaint.setColor(0x6636455A);
        canvas.drawRect(right - fold, top, right, top + fold, mPaint);
        mPaint.setColor(0x66FFFFFF);
        mPaint.setStrokeWidth(Math.max(1.0f, UIUtils.dp2px(1.0f)));
        canvas.drawLine(right - fold, top, right - fold, top + fold, mPaint);
        canvas.drawLine(right - fold, top + fold, right, top + fold, mPaint);

        mPaint.setColor(0xAA1D2734);
        mPaint.setStrokeWidth(Math.max(1.0f, half * 0.08f));
        float lineLeft = left + half * 0.24f;
        float lineRight = right - half * 0.24f;
        canvas.drawLine(lineLeft, cy - half * 0.12f, lineRight, cy - half * 0.12f, mPaint);
        canvas.drawLine(lineLeft, cy + half * 0.22f, lineRight * 0.9f + lineLeft * 0.1f, cy + half * 0.22f, mPaint);
    }

    private void drawCloud(Canvas canvas, float cx, float cy, float half) {
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx - half * 0.42f, cy + half * 0.12f, half * 0.34f, mPaint);
        canvas.drawCircle(cx - half * 0.08f, cy - half * 0.12f, half * 0.44f, mPaint);
        canvas.drawCircle(cx + half * 0.38f, cy + half * 0.12f, half * 0.34f, mPaint);
        mRect.set(cx - half * 0.65f, cy + half * 0.04f, cx + half * 0.68f, cy + half * 0.44f);
        canvas.drawRoundRect(mRect, half * 0.18f, half * 0.18f, half * 0.18f, half * 0.18f, mPaint);
    }

    private void drawChevronDown(Canvas canvas, float cx, float cy, float half) {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(1.0f, half * 0.18f));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        float inset = half * 0.62f;
        canvas.drawLine(cx - inset, cy - half * 0.22f, cx, cy + half * 0.36f, mPaint);
        canvas.drawLine(cx, cy + half * 0.36f, cx + inset, cy - half * 0.22f, mPaint);
        mPaint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawChevronUp(Canvas canvas, float cx, float cy, float half) {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(1.0f, half * 0.18f));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        float inset = half * 0.62f;
        canvas.drawLine(cx - inset, cy + half * 0.22f, cx, cy - half * 0.36f, mPaint);
        canvas.drawLine(cx, cy - half * 0.36f, cx + inset, cy + half * 0.22f, mPaint);
        mPaint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawSplitHorizontal(Canvas canvas, float cx, float cy, float half) {
        float left = cx - half * 0.92f;
        float top = cy - half * 0.72f;
        float right = cx + half * 0.92f;
        float bottom = cy + half * 0.72f;
        float radius = Math.max(2.0f, half * 0.12f);
        float stroke = Math.max(1.0f, half * 0.09f);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setAlpha(210);
        mRect.set(left, top, right, bottom);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);
        canvas.drawLine(cx, top + stroke, cx, bottom - stroke, mPaint);
    }

    private void drawSplitVertical(Canvas canvas, float cx, float cy, float half) {
        float left = cx - half * 0.92f;
        float top = cy - half * 0.72f;
        float right = cx + half * 0.92f;
        float bottom = cy + half * 0.72f;
        float radius = Math.max(2.0f, half * 0.12f);
        float stroke = Math.max(1.0f, half * 0.09f);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setAlpha(210);
        mRect.set(left, top, right, bottom);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);
        canvas.drawLine(left + stroke, cy, right - stroke, cy, mPaint);
    }

    private void drawClose(Canvas canvas, float cx, float cy, float half) {
        float size = half * 0.62f;
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(1.2f, half * 0.12f));
        mPaint.setAlpha(220);
        canvas.drawLine(cx - size, cy - size, cx + size, cy + size, mPaint);
        canvas.drawLine(cx + size, cy - size, cx - size, cy + size, mPaint);
    }

}
