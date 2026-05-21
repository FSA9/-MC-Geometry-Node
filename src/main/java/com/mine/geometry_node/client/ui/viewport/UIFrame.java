package com.mine.geometry_node.client.ui.viewport;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.FrameData;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

public class UIFrame extends FrameLayout {
    private final FrameData mFrameData;

    private float mLogicX = 0;
    private float mLogicY = 0;

    // 仅保留统一的标题栏高度 (逻辑单位 DP)
    public static final float FRAME_HEADER_H = 30f;

    private final Paint mPaint = new Paint();
    private final RectF mTempRect = new RectF();
    private final TextView mTitleView;
    private boolean mIsSelected = false;

    public UIFrame(Context context, FrameData frameData) {
        super(context);
        this.mFrameData = frameData;

        setWillNotDraw(false);
        mPaint.setAntiAlias(true);

        mTitleView = new TextView(context);
        mTitleView.setText(mFrameData.title);
        mTitleView.setTextColor(UIConstants.CLR_WHITE);
        mTitleView.setTextSize(16);
        mTitleView.setGravity(icyllis.modernui.view.Gravity.CENTER);

        LayoutParams titleLp = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(FRAME_HEADER_H)
        );
        addView(mTitleView, titleLp);

        updateBounds();
    }

    public void updateTitle() {
        if (mTitleView != null && mFrameData != null) {
            mTitleView.setText(mFrameData.title);
        }
    }

    public FrameData getFrameData() {
        return mFrameData;
    }

    public void setSelected(boolean selected) {
        if (mIsSelected != selected) {
            mIsSelected = selected;
            invalidate();
        }
    }

    public boolean isSelected() {
        return mIsSelected;
    }

    /**
     * 根据数据更新自己的位置和大小
     */
    public void updateBounds() {
        mLogicX = mFrameData.uiPos[0];
        mLogicY = mFrameData.uiPos[1];

        float w = mFrameData.uiSize[0];
        float h = mFrameData.uiSize[1];

        LayoutParams lp = new LayoutParams(UIUtils.dp2pxInt(w), UIUtils.dp2pxInt(h));
        lp.leftMargin = UIUtils.dp2pxInt(mLogicX);
        lp.topMargin = UIUtils.dp2pxInt(mLogicY);
        setLayoutParams(lp);

        super.setTranslationX(0);
        super.setTranslationY(0);

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
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

        // 3. 画整体外部边框
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(UIUtils.dp2px(mIsSelected ? 2.5f : 1.5f));
        mPaint.setColor(mIsSelected ? UIConstants.CLR_WHITE : mFrameData.color);
        mPaint.setAlpha(255);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, scaledRadius, scaledRadius, mPaint);
    }

    /**
     * 只有点击在标题栏内才拦截事件，点击下方主体区域直接放行给底下的节点和 Viewport。
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        float ly = UIUtils.px2dp(ev.getY());

        if (ly > FRAME_HEADER_H) {
            return false;
        }

        return super.dispatchTouchEvent(ev);
    }

    /**
     * 实时拖拽时，增量平移图框的 UI 位置
     */
    public void offsetPosition(float dx, float dy) {
        setTranslationX(getTranslationX() + dx);
        setTranslationY(getTranslationY() + dy);
    }

    /**
     * 判定逻辑坐标是否精准落在标题栏的矩形区域内
     */
    public boolean hitTest(float uiX, float uiY) {
        float x = mFrameData.uiPos[0];
        float y = mFrameData.uiPos[1];
        float w = mFrameData.uiSize[0];

        return uiX >= x && uiX <= x + w && uiY >= y && uiY <= y + FRAME_HEADER_H;
    }

    @Override
    public void setTranslationX(float translationX) {
        mLogicX = translationX; // 核心修复：只修改视觉变量，绝对不碰 mFrameData
        super.setTranslationX(0);
        updateMarginPos();
    }

    @Override
    public void setTranslationY(float translationY) {
        mLogicY = translationY; // 核心修复：只修改视觉变量，绝对不碰 mFrameData
        super.setTranslationY(0);
        updateMarginPos();
    }

    @Override
    public float getTranslationX() {
        return mLogicX;
    }

    @Override
    public float getTranslationY() {
        return mLogicY;
    }

    private void updateMarginPos() {
        icyllis.modernui.view.ViewGroup.LayoutParams params = getLayoutParams();
        if (params instanceof LayoutParams lp) {
            lp.leftMargin = UIUtils.dp2pxInt(mLogicX);
            lp.topMargin = UIUtils.dp2pxInt(mLogicY);
            setLayoutParams(lp);
        }
    }
}