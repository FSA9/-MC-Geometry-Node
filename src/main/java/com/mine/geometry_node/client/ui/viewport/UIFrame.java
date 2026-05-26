package com.mine.geometry_node.client.ui.viewport;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.visual.FrameVisualAdapter;
import com.mine.geometry_node.core.node.FrameData;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.text.FontMetricsInt;
import icyllis.modernui.graphics.text.ShapedText;
import icyllis.modernui.text.TextDirectionHeuristics;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.TextShaper;

public class UIFrame implements FrameVisualAdapter {
    private final FrameData mFrameData;

    private float mLogicX = 0;
    private float mLogicY = 0;
    private float mLogicW = 0;
    private float mLogicH = 0;

    // 仅保留统一的标题栏高度 (逻辑单位 DP)
    public static final float FRAME_HEADER_H = 30f;

    private final Paint mPaint = new Paint();
    private final TextPaint mTitlePaint = new TextPaint();
    private final FontMetricsInt mTitleMetrics = new FontMetricsInt();
    private final RectF mTempRect = new RectF();
    private ShapedText mTitleText;
    private boolean mIsSelected = false;

    public UIFrame(FrameData frameData) {
        this.mFrameData = frameData;

        mPaint.setAntiAlias(true);
        mTitlePaint.setTextAntiAlias(true);
        mTitlePaint.setTextSize(UIUtils.dp2px(16f));
        updateTitleText();

        updateBounds();
    }

    @Override
    public void updateTitle() {
        updateTitleText();
    }

    @Override
    public FrameData getFrameData() {
        return mFrameData;
    }

    @Override
    public void setSelected(boolean selected) {
        if (mIsSelected != selected) {
            mIsSelected = selected;
        }
    }

    @Override
    public boolean isSelected() {
        return mIsSelected;
    }

    /**
     * 根据数据更新自己的位置和大小
     */
    @Override
    public void updateBounds() {
        mLogicX = mFrameData.uiPos[0];
        mLogicY = mFrameData.uiPos[1];

        float w = mFrameData.uiSize[0];
        float h = mFrameData.uiSize[1];
        mLogicW = w;
        mLogicH = h;
    }

    @Override
    public void drawFrame(Canvas canvas, ViewportCamera camera) {
        canvas.save();
        canvas.translate(camera.uiToScreenX(mLogicX), camera.uiToScreenY(mLogicY));
        canvas.scale(camera.getScale(), camera.getScale());
        drawFrameLocal(canvas, UIUtils.dp2px(mLogicW), UIUtils.dp2px(mLogicH));
        canvas.restore();
    }

    private void drawFrameLocal(Canvas canvas, float w, float h) {
        float scaledRadius = UIUtils.dp2px(4f);

        // 1. 画图框整体背景 (半透明)
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mFrameData.color);
        mPaint.setAlpha(80);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, scaledRadius, scaledRadius, mPaint);

        // 2. 画标题栏背景 (仅对顶部做圆角)
        float headerBottom = UIUtils.dp2px(FRAME_HEADER_H);
        mPaint.setColor(mFrameData.color);
        mPaint.setAlpha(180);
        mTempRect.set(0, 0, w, headerBottom);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, 0, 0, mPaint);

        // 3. 画标题文字
        if (mTitleText != null) {
            mTitlePaint.setColor(UIConstants.CLR_WHITE);
            mTitlePaint.setAlpha(255);
            mTitlePaint.getFontMetricsInt(mTitleMetrics);
            float titleX = (w - mTitleText.getAdvance()) / 2.0f;
            float titleBaseline = headerBottom / 2.0f - (mTitleMetrics.ascent + mTitleMetrics.descent) / 2.0f;
            canvas.drawShapedText(mTitleText, titleX, titleBaseline, mTitlePaint);
        }

        // 4. 画整体外部边框
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(UIUtils.dp2px(mIsSelected ? 2.5f : 1.5f));
        mPaint.setColor(mIsSelected ? UIConstants.CLR_WHITE : mFrameData.color);
        mPaint.setAlpha(255);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, scaledRadius, scaledRadius, mPaint);
    }

    /**
     * 判定逻辑坐标是否精准落在标题栏的矩形区域内
     */
    @Override
    public boolean hitTest(float uiX, float uiY) {
        float x = mFrameData.uiPos[0];
        float y = mFrameData.uiPos[1];
        float w = mFrameData.uiSize[0];

        return uiX >= x && uiX <= x + w && uiY >= y && uiY <= y + FRAME_HEADER_H;
    }

    @Override
    public void setPreviewPosition(float x, float y) {
        mLogicX = x;
        mLogicY = y;
    }

    @Override
    public void offsetPreviewPosition(float dx, float dy) {
        setPreviewPosition(mLogicX + dx, mLogicY + dy);
    }

    @Override
    public void setPreviewBounds(float x, float y, float w, float h) {
        mLogicW = w;
        mLogicH = h;
        setPreviewPosition(x, y);
    }

    @Override
    public float getUiX() {
        return mLogicX;
    }

    @Override
    public float getUiY() {
        return mLogicY;
    }

    @Override
    public float getVisualWidthDp() {
        return mLogicW;
    }

    @Override
    public float getVisualHeightDp() {
        return mLogicH;
    }

    @Override
    public void getLogicalBounds(RectF outRect) {
        outRect.set(mLogicX, mLogicY, mLogicX + mLogicW, mLogicY + mLogicH);
    }

    private void updateTitleText() {
        String title = mFrameData != null && mFrameData.title != null ? mFrameData.title : "";
        mTitleText = TextShaper.shapeText(title, 0, title.length(), TextDirectionHeuristics.FIRSTSTRONG_LTR, mTitlePaint);
    }
}
