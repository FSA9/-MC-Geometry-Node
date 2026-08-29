package com.mine.geometry_node.client.ui.editor.graph.frame;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.ViewportCamera;
import com.mine.geometry_node.core.node.document.FrameData;

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
    private static final float CORNER_RADIUS_DP = 6f;
    private static final float BODY_INSET_DP = 1.0f;
    private static final float HEADER_BOTTOM_LINE_DP = 1f;
    private static final int FRAME_BODY_BASE = 0xFF20242C;
    private static final int FRAME_HEADER_BASE = 0xFF262C36;
    private static final int FRAME_OUTLINE_BASE = 0xFF1A1E24;

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
        mTitlePaint.setTextSize(UIUtils.dp2px(13f));
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
        float scaledRadius = UIUtils.dp2px(CORNER_RADIUS_DP);
        float headerBottom = UIUtils.dp2px(FRAME_HEADER_H);
        int accentColor = ensureOpaque(mFrameData.color);
        int bodyColor = withAlpha(mixColor(FRAME_BODY_BASE, accentColor, mIsSelected ? 0.32f : 0.22f), mIsSelected ? 0xE8 : 0xD4);
        int headerColor = withAlpha(mixColor(FRAME_HEADER_BASE, accentColor, mIsSelected ? 0.78f : 0.64f), 0xF2);
        int headerTopColor = withAlpha(lighten(accentColor, mIsSelected ? 0.22f : 0.12f), 0xB8);
        int borderColor = withAlpha(lighten(accentColor, mIsSelected ? 0.38f : 0.18f), 0xF2);
        int innerBorderColor = withAlpha(mixColor(FRAME_OUTLINE_BASE, accentColor, 0.38f), 0x82);

        // 1. 主体带少量图框色，存在感比纯深色更明确，但仍让节点保持前景层级。
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(bodyColor);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, scaledRadius, scaledRadius, mPaint);

        // 2. 标题栏直接使用图框色的柔和版本，保证颜色识别度。
        float bodyInset = UIUtils.dp2px(BODY_INSET_DP);
        mPaint.setColor(headerColor);
        mTempRect.set(bodyInset, bodyInset, w - bodyInset, headerBottom);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, 0, 0, mPaint);

        mPaint.setColor(headerTopColor);
        mTempRect.set(bodyInset, bodyInset, w - bodyInset, bodyInset + UIUtils.dp2px(3.0f));
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, 0, 0, mPaint);

        mPaint.setColor(withAlpha(darken(accentColor, 0.42f), 0x92));
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(bodyInset, headerBottom - UIUtils.dp2px(HEADER_BOTTOM_LINE_DP), w - bodyInset, headerBottom, mPaint);

        // 3. 画标题文字，居中对齐。
        if (mTitleText != null) {
            mTitlePaint.setColor(UIConstants.CLR_WHITE);
            mTitlePaint.setAlpha(255);
            mTitlePaint.getFontMetricsInt(mTitleMetrics);
            float titleX = (w - mTitleText.getAdvance()) / 2.0f;
            float titleBaseline = headerBottom / 2.0f - (mTitleMetrics.ascent + mTitleMetrics.descent) / 2.0f;
            canvas.drawShapedText(mTitleText, titleX, titleBaseline, mTitlePaint);
        }

        // 4. 内外双线：普通状态更轻，选中状态更明确。
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(UIUtils.dp2px(1.0f));
        mPaint.setColor(innerBorderColor);
        mTempRect.set(bodyInset, bodyInset, w - bodyInset, h - bodyInset);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, scaledRadius, scaledRadius, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(UIUtils.dp2px(mIsSelected ? 2.4f : 1.4f));
        mPaint.setColor(mIsSelected ? UIConstants.CLR_WHITE : borderColor);
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

    private static int ensureOpaque(int color) {
        return color | 0xFF000000;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int lighten(int color, float amount) {
        float clamped = Math.max(0.0f, Math.min(1.0f, amount));
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, Math.round(r + (255 - r) * clamped));
        g = Math.min(255, Math.round(g + (255 - g) * clamped));
        b = Math.min(255, Math.round(b + (255 - b) * clamped));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, float amount) {
        float clamped = Math.max(0.0f, Math.min(1.0f, amount));
        int a = (color >>> 24) & 0xFF;
        int r = Math.round(((color >>> 16) & 0xFF) * (1.0f - clamped));
        int g = Math.round(((color >>> 8) & 0xFF) * (1.0f - clamped));
        int b = Math.round((color & 0xFF) * (1.0f - clamped));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int mixColor(int a, int b, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int oa = Math.round(aa + (ba - aa) * clamped);
        int or = Math.round(ar + (br - ar) * clamped);
        int og = Math.round(ag + (bg - ag) * clamped);
        int ob = Math.round(ab + (bb - ab) * clamped);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }
}
