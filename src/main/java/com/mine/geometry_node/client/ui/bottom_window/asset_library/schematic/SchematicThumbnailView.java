package com.mine.geometry_node.client.ui.bottom_window.asset_library.schematic;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.BlendMode;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.View;

import java.io.File;

public final class SchematicThumbnailView extends View {
    private static final short[] QUAD_INDICES = {0, 1, 2, 0, 2, 3};

    private final File mFile;
    private final Paint mPaint = new Paint();
    private final RectF mRect = new RectF();
    private final float[] mQuadVertices = new float[8];
    private final int[] mQuadColors = new int[4];
    private boolean mMaterialRefreshScheduled;

    public SchematicThumbnailView(Context context, File file) {
        super(context);
        mFile = file;
        mPaint.setAntiAlias(true);
        setWillNotDraw(false);
    }

    public void preload() {
        SchematicThumbnailCache.preload(mFile, this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        preload();
    }

    @Override
    protected void onDetachedFromWindow() {
        SchematicThumbnailCache.unobserve(mFile, this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        SchematicThumbnail thumbnail = SchematicThumbnailCache.get(mFile, this);
        if (thumbnail == null || !thumbnail.hasPreview()) {
            drawFallback(canvas);
            return;
        }

        drawThumbnail(canvas, thumbnail);
    }

    private void drawThumbnail(Canvas canvas, SchematicThumbnail thumbnail) {
        int gridWidth = Math.max(1, thumbnail.gridWidth());
        int gridLength = Math.max(1, thumbnail.gridLength());
        float padding = UIUtils.dp2px(3.0f);
        float availableW = Math.max(1.0f, getWidth() - padding * 2.0f);
        float availableH = Math.max(1.0f, getHeight() - padding * 2.0f);
        float diagonal = Math.max(2.0f, gridWidth + gridLength);
        float maxLift = availableH * 0.30f;
        float tileW = Math.max(1.4f, Math.min(availableW * 1.85f / diagonal, Math.max(1.0f, availableH - maxLift) * 3.0f / diagonal));
        float tileH = tileW * 0.5f;
        float sideDepthBase = tileH * 0.88f;
        float maxSideDepth = sideDepthBase + maxLift * 0.46f;
        float isoHeight = (gridWidth + gridLength) * tileH * 0.5f + maxSideDepth;
        float originX = getWidth() * 0.5f + (gridLength - gridWidth) * tileW * 0.25f;
        float originY = Math.max(padding + maxLift, (getHeight() - isoHeight) * 0.5f + maxLift) + tileH;
        int maxY = Math.max(1, thumbnail.height() - 1);
        boolean[] deferredMaterials = {false};

        for (SchematicThumbnail.Column column : thumbnail.columns()) {
            float cx = originX + (column.x() - column.z()) * tileW * 0.5f;
            float baseY = originY + (column.x() + column.z()) * tileH * 0.5f;
            float heightRatio = Math.max(0.0f, Math.min(1.0f, column.y() / (float) maxY));
            float lift = maxLift * heightRatio;
            float sideDepth = sideDepthBase + lift * 0.46f;
            SchematicThumbnailMaterialResolver.MaterialColors colors =
                    SchematicThumbnailMaterialResolver.resolveBudgeted(column.state(), column.color(), deferredMaterials);
            drawColumn(canvas, cx, baseY - lift, tileW * 0.54f, tileH * 0.60f, sideDepth, colors, heightRatio);
        }

        if (deferredMaterials[0]) {
            scheduleMaterialRefresh();
        }

        if (thumbnail.incomplete()) {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(Math.max(1.0f, UIUtils.dp2px(1.0f)));
            mPaint.setColor(0xAAFFD166);
            float inset = UIUtils.dp2px(3.0f);
            mRect.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawRoundRect(mRect, UIUtils.dp2px(4.0f), mPaint);
        }
    }

    private void scheduleMaterialRefresh() {
        if (mMaterialRefreshScheduled) {
            return;
        }
        mMaterialRefreshScheduled = true;
        postDelayed(() -> {
            mMaterialRefreshScheduled = false;
            invalidate();
        }, 33L);
    }

    private void drawColumn(Canvas canvas,
                            float cx,
                            float cy,
                            float halfW,
                            float halfH,
                            float sideDepth,
                            SchematicThumbnailMaterialResolver.MaterialColors colors,
                            float heightRatio) {
        float topX = cx;
        float topY = cy - halfH;
        float rightX = cx + halfW;
        float rightY = cy;
        float bottomX = cx;
        float bottomY = cy + halfH;
        float leftX = cx - halfW;
        float leftY = cy;

        float lowerRightY = rightY + sideDepth;
        float lowerBottomY = bottomY + sideDepth;
        float lowerLeftY = leftY + sideDepth;

        int leftColor = shade(colors.left(), 0.58f + heightRatio * 0.08f);
        int rightColor = shade(colors.right(), 0.70f + heightRatio * 0.08f);
        int topColor = shade(colors.top(), 0.92f + heightRatio * 0.12f);

        drawQuad(canvas,
                leftX, leftY,
                bottomX, bottomY,
                bottomX, lowerBottomY,
                leftX, lowerLeftY,
                leftColor);
        drawQuad(canvas,
                bottomX, bottomY,
                rightX, rightY,
                rightX, lowerRightY,
                bottomX, lowerBottomY,
                rightColor);
        drawQuad(canvas,
                topX, topY,
                rightX, rightY,
                bottomX, bottomY,
                leftX, leftY,
                topColor);
    }

    private void drawQuad(Canvas canvas,
                          float x0, float y0,
                          float x1, float y1,
                          float x2, float y2,
                          float x3, float y3,
                          int color) {
        mQuadVertices[0] = x0;
        mQuadVertices[1] = y0;
        mQuadVertices[2] = x1;
        mQuadVertices[3] = y1;
        mQuadVertices[4] = x2;
        mQuadVertices[5] = y2;
        mQuadVertices[6] = x3;
        mQuadVertices[7] = y3;

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(color);
        mQuadColors[0] = color;
        mQuadColors[1] = color;
        mQuadColors[2] = color;
        mQuadColors[3] = color;
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, mQuadVertices.length,
                mQuadVertices, 0,
                null, 0,
                mQuadColors, 0,
                QUAD_INDICES, 0, QUAD_INDICES.length,
                BlendMode.SRC_OVER,
                mPaint);
    }

    private void drawFallback(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float half = Math.min(w, h) * 0.28f;

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(1.0f, half * 0.11f));
        mPaint.setColor(0xFF86B8FF);

        float left = cx - half;
        float right = cx + half;
        float top = cy - half * 0.6f;
        float bottom = cy + half * 0.6f;
        canvas.drawLine(cx, top - half * 0.32f, right, top + half * 0.18f, mPaint);
        canvas.drawLine(cx, top - half * 0.32f, left, top + half * 0.18f, mPaint);
        canvas.drawLine(left, top + half * 0.18f, cx, top + half * 0.72f, mPaint);
        canvas.drawLine(right, top + half * 0.18f, cx, top + half * 0.72f, mPaint);
        canvas.drawLine(left, bottom - half * 0.18f, cx, bottom + half * 0.32f, mPaint);
        canvas.drawLine(right, bottom - half * 0.18f, cx, bottom + half * 0.32f, mPaint);
        canvas.drawLine(left, top + half * 0.18f, left, bottom - half * 0.18f, mPaint);
        canvas.drawLine(right, top + half * 0.18f, right, bottom - half * 0.18f, mPaint);
        canvas.drawLine(cx, top + half * 0.72f, cx, bottom + half * 0.32f, mPaint);
    }

    private int shade(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int r = clamp((int) (((color >>> 16) & 0xFF) * factor));
        int g = clamp((int) (((color >>> 8) & 0xFF) * factor));
        int b = clamp((int) ((color & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
