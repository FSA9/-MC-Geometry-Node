package com.mine.geometry_node.client.ui.Viewport;

import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.HorizontalScrollView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 视口面板容器 (包含顶部的 Tab 栏 + 下方的 Viewport)
 */
public class ViewportPanel extends LinearLayout {

    private final HorizontalScrollView mTabScrollView;
    private final TabBarLayout mTabBar; // 替换为自定义的容器
    private final Viewport mViewport;
    private final float mTouchSlop;

    public ViewportPanel(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // 1. 顶部 Tab 栏配置
        mTabScrollView = new HorizontalScrollView(context);
        mTabScrollView.setHorizontalScrollBarEnabled(false);
        mTabScrollView.setBackground(createColorDrawable(0xFF1E1E1E));

        // 滚轮支持
        mTabScrollView.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_SCROLL) {
                float vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (vScroll != 0) {
                    mTabScrollView.scrollBy((int) (-vScroll * 80), 0);
                    return true;
                }
            }
            return false;
        });

        // 使用自定义的 TabBarLayout 以支持绘制插入光标
        mTabBar = new TabBarLayout(context);
        mTabScrollView.addView(mTabBar, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        addView(mTabScrollView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 30));

        // 2. 核心 Viewport
        mViewport = new Viewport(context);
        addView(mViewport, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        DocumentManager.INSTANCE.setOnTabChangedListener(() -> post(this::refreshTabs));
        refreshTabs();
    }

    private void refreshTabs() {
        mTabBar.removeAllViews();
        DocumentManager docMgr = DocumentManager.INSTANCE;
        mViewport.bindSession(docMgr.getActiveSession());

        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        for (int i = 0; i < docMgr.getSessions().size(); i++) {
            GraphSession session = docMgr.getSessions().get(i);
            mTabBar.addView(createTabView(session, i), tabParams);
        }
    }

    private View createTabView(GraphSession session, final int index) {
        DocumentManager docMgr = DocumentManager.INSTANCE;
        LinearLayout tabLayout = new LinearLayout(getContext());
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabLayout.setGravity(Gravity.CENTER_VERTICAL);

        boolean isActive = (session == docMgr.getActiveSession());
        tabLayout.setBackground(createColorDrawable(isActive ? 0xFF3C3C3C : 0xFF2A2A2A));

        // --- A. 专用拖拽手柄 ---
        TextView dragHandle = new TextView(getContext());
        dragHandle.setText(" ⋮⋮ ");
        dragHandle.setTextColor(0xFF666666);
        dragHandle.setTextSize(14);
        dragHandle.setPadding(8, 0, 4, 0);
        tabLayout.addView(dragHandle);

        // --- B. 标签内容区 ---
        LinearLayout contentArea = new LinearLayout(getContext());
        contentArea.setOrientation(LinearLayout.HORIZONTAL);
        contentArea.setGravity(Gravity.CENTER_VERTICAL);
        contentArea.setPadding(4, 0, 8, 0);

        TextView titleView = new TextView(getContext());
        titleView.setText((session.isDirty ? "* " : "") + session.tabName);
        titleView.setTextColor(isActive ? 0xFFFFFFFF : 0xFFAAAAAA);
        titleView.setTextSize(14);
        contentArea.addView(titleView);

        TextView closeBtn = new TextView(getContext());
        closeBtn.setText(" × ");
        closeBtn.setPadding(8, 0, 8, 0);
        closeBtn.setTextColor(isActive ? 0xFFCCCCCC : 0xFF888888);
        closeBtn.setOnClickListener(v -> {
            DocumentManager.INSTANCE.saveSession(session);
            v.post(() -> docMgr.closeSession(session));
        });
        contentArea.addView(closeBtn);

        tabLayout.addView(contentArea);

        // 1. 内容区点击切换
        contentArea.setOnClickListener(v -> docMgr.switchSession(session));

        // 2. 纯渲染拖拽逻辑
        final float[] startX = {0};
        final boolean[] isDragging = {false};

        dragHandle.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    isDragging[0] = false;
                    mTabScrollView.requestDisallowInterceptTouchEvent(true);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX[0];
                    if (!isDragging[0] && Math.abs(dx) > mTouchSlop) {
                        isDragging[0] = true;
                        tabLayout.setAlpha(0.4f);
                    }
                    if (isDragging[0]) {
                        // 动态计算光标位置并要求 TabBar 绘制
                        int[] tabBarLoc = new int[2];
                        mTabBar.getLocationOnScreen(tabBarLoc);
                        float dropX = event.getRawX() - tabBarLoc[0];

                        int indicatorX = mTabBar.getWidth(); // 默认末尾
                        for (int i = 0; i < mTabBar.getChildCount(); i++) {
                            View child = mTabBar.getChildAt(i);
                            float centerX = child.getLeft() + child.getWidth() / 2f;
                            if (dropX < centerX) {
                                indicatorX = child.getLeft();
                                break;
                            }
                        }
                        mTabBar.updateIndicator(indicatorX);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging[0]) {
                        calculateAndMoveTab(event.getRawX(), index);
                    }
                    tabLayout.setAlpha(1.0f);
                    mTabBar.hideIndicator(); // 隐藏指示线
                    mTabScrollView.requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return true;
        });

        return tabLayout;
    }

    private void calculateAndMoveTab(float rawX, int currentIndex) {
        DocumentManager docMgr = DocumentManager.INSTANCE;
        int[] tabBarLoc = new int[2];
        mTabBar.getLocationOnScreen(tabBarLoc);

        float dropX = rawX - tabBarLoc[0];
        int targetIdx = mTabBar.getChildCount();

        for (int i = 0; i < mTabBar.getChildCount(); i++) {
            View child = mTabBar.getChildAt(i);
            float centerX = child.getLeft() + child.getWidth() / 2f;
            if (dropX < centerX) {
                targetIdx = i;
                break;
            }
        }

        if (targetIdx > currentIndex) {
            targetIdx--;
        }

        if (targetIdx != currentIndex) {
            final int finalTarget = targetIdx;
            mTabBar.post(() -> docMgr.moveSession(currentIndex, finalTarget));
        }
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static class TabBarLayout extends LinearLayout {
        private int mIndicatorX = -1;
        private final Paint mIndicatorPaint;

        public TabBarLayout(Context context) {
            super(context);
            setOrientation(LinearLayout.HORIZONTAL);
            setWillNotDraw(false);

            mIndicatorPaint = new Paint();
            mIndicatorPaint.setColor(0xFF00AAFF);
        }

        public void updateIndicator(int x) {
            if (mIndicatorX != x) {
                mIndicatorX = x;
                invalidate(); // 仅触发脏矩形重绘，不触发重新布局
            }
        }

        public void hideIndicator() {
            if (mIndicatorX != -1) {
                mIndicatorX = -1;
                invalidate();
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);

            if (mIndicatorX >= 0) {
                int drawX = Math.max(2, Math.min(getWidth() - 2, mIndicatorX));
                canvas.drawRect(drawX - 2, 0, drawX + 2, getHeight(), mIndicatorPaint);
            }
        }
    }
}