package com.mine.geometry_node.client.ui.editor.asset.schematic;

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
    private SchematicThumbnailCache.Subscription mSubscription;
    private boolean mMaterialRefreshScheduled;

    public SchematicThumbnailView(Context context, File file) {
        super(context);
        mFile = file;
        mPaint.setAntiAlias(true);
        setWillNotDraw(false);
    }

    public void preload() {
        ensureSubscription();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ensureSubscription();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mSubscription != null) {
            mSubscription.close();
            mSubscription = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        SchematicThumbnail thumbnail = ensureSubscription().thumbnail();
        if (thumbnail == null || !thumbnail.hasPreview()) {
            drawFallback(canvas);
            return;
        }

        drawThumbnail(canvas, thumbnail);
    }

    private SchematicThumbnailCache.Subscription ensureSubscription() {
        if (mSubscription == null) {
            mSubscription = SchematicThumbnailCache.subscribe(mFile, this);
        }
        return mSubscription;
    }

    private void drawThumbnail(Canvas canvas, SchematicThumbnail thumbnail) {
        float padding = UIUtils.dp2px(3.0f);
        boolean[] deferredMaterials = {false};
        SchematicThumbnailRenderer.render(thumbnail, getWidth(), getHeight(), padding,
                (state, color) -> SchematicThumbnailMaterialResolver.resolveBudgeted(state, color, deferredMaterials),
                (x0, y0, x1, y1, x2, y2, x3, y3, color) ->
                        drawQuad(canvas, x0, y0, x1, y1, x2, y2, x3, y3, color));

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

    public static void invalidate(File file) {
        SchematicThumbnailCache.invalidate(file);
    }

    public static void invalidateUnder(File file) {
        SchematicThumbnailCache.invalidateUnder(file);
    }

    public static void clearCache() {
        SchematicThumbnailCache.clear();
    }
}
