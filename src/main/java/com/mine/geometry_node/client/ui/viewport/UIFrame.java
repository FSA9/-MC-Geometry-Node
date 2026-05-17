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

    // 对应 GraphController 中的判定常量 (逻辑单位 DP)
    public static final float FRAME_PADDING_P = 20f;
    public static final float FRAME_HEADER_H1 = 30f;

    private final Paint mPaint = new Paint();
    private final RectF mTempRect = new RectF();
    private final TextView mTitleView;

    public UIFrame(Context context, FrameData frameData) {
        super(context);
        this.mFrameData = frameData;

        setWillNotDraw(false);
        mPaint.setAntiAlias(true);

        mTitleView = new TextView(context);
        mTitleView.setText(mFrameData.title);
        mTitleView.setTextColor(UIConstants.CLR_WHITE);
        // 借用你 UIConstants 里的常量，或者直接用 16
        mTitleView.setTextSize(16);

        // 把文字定位到左上角，留出 P 的内边距，并向下稍微偏移使其在 H1 标题栏内居中
        LayoutParams titleLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        titleLp.leftMargin = UIUtils.dp2pxInt(FRAME_PADDING_P);
        titleLp.topMargin = UIUtils.dp2pxInt(FRAME_PADDING_P + 5f);
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

    /**
     * 根据数据更新自己的位置和大小
     */
    public void updateBounds() {
        float x = mFrameData.uiPos[0];
        float y = mFrameData.uiPos[1];
        float w = mFrameData.uiSize[0];
        float h = mFrameData.uiSize[1];

        // 尺寸依然必须是 Pixel，需要 dp2px
        LayoutParams lp = new LayoutParams(UIUtils.dp2pxInt(w), UIUtils.dp2pxInt(h));
        setLayoutParams(lp);

        // 修改：平移位置直接使用逻辑坐标，去除 UIUtils.dp2px()
        setTranslationX(x);
        setTranslationY(y);

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float scaledRadius = UIUtils.dp2px(8f); // 图框圆角

        // 1. 画背景 (半透明)
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mFrameData.color);
        mPaint.setAlpha(80); // 约 30% 透明度，防止遮挡背景网格
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, scaledRadius, scaledRadius, mPaint);

        // 2. 画标题栏背景 (加深颜色)
        float headerBottom = UIUtils.dp2px(FRAME_PADDING_P + FRAME_HEADER_H1);
        mPaint.setColor(mFrameData.color);
        mPaint.setAlpha(180);
        mTempRect.set(0, 0, w, headerBottom);
        // 只给顶部加圆角
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, 0, 0, mPaint);

        // 3. 画边框
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(UIUtils.dp2px(2f));
        mPaint.setColor(mFrameData.color);
        mPaint.setAlpha(255);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, scaledRadius, scaledRadius, mPaint);
    }

    /**
     * 核心：外圈事件拦截逻辑
     * 如果点击的是中间的“镂空”区域，放行事件给底下的节点和 Viewport。
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        float lx = UIUtils.px2dp(ev.getX());
        float ly = UIUtils.px2dp(ev.getY());
        float w = UIUtils.px2dp(getWidth());
        float h = UIUtils.px2dp(getHeight());

        // 计算内部空洞区域 (去掉四周的 P 和顶部的 H1)
        boolean inHollow = lx > FRAME_PADDING_P && lx < (w - FRAME_PADDING_P) &&
                ly > (FRAME_PADDING_P + FRAME_HEADER_H1) && ly < (h - FRAME_PADDING_P);

        if (inHollow) {
            // 点击在图框内部，不拦截，交给底层(节点层)处理
            return false;
        }

        // 点击在边缘 P 或标题栏上，正常分发给自己
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 实时拖拽时，增量平移图框的 UI 位置（不修改底层数据，保证高帧率渲染）
     */
    public void offsetPosition(float dx, float dy) {
        setTranslationX(getTranslationX() + dx);
        setTranslationY(getTranslationY() + dy);
    }

    /**
     * 判断指定的 UI 逻辑坐标是否落在图框的“交互区”（边缘 P 和 标题栏 H1）
     */
    public boolean hitTest(float uiX, float uiY) {
        float x = mFrameData.uiPos[0];
        float y = mFrameData.uiPos[1];
        float w = mFrameData.uiSize[0];
        float h = mFrameData.uiSize[1];

        // 1. 先判断是否在整体矩形内
        if (uiX < x || uiX > x + w || uiY < y || uiY > y + h) {
            return false;
        }

        // 2. 扣除中间镂空区域
        float lx = uiX - x;
        float ly = uiY - y;
        boolean inHollow = lx > FRAME_PADDING_P && lx < (w - FRAME_PADDING_P) &&
                ly > (FRAME_PADDING_P + FRAME_HEADER_H1) && ly < (h - FRAME_PADDING_P);

        return !inHollow;
    }
}