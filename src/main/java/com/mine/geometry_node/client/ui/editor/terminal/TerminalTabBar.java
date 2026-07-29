package com.mine.geometry_node.client.ui.editor.terminal;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class TerminalTabBar extends LinearLayout {

    public interface TabListener {
        void onTabSelected(int index);
        void onTabClosed(int index);
        void onTabCreated();
        void onTabMoved(int fromIndex, int toIndex);
    }

    private final List<String> mTabs = new ArrayList<>();
    private int mSelectedIndex = -1;
    private TabListener mListener;
    private final float mTouchSlop;

    // 拖拽指示器
    private int mIndicatorX = -1;
    private final Paint mIndicatorPaint;
    private static final float INDICATOR_WIDTH = 2.0f;

    public TerminalTabBar(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setBackground(createColorDrawable(0xFF252526));
        setWillNotDraw(false);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mIndicatorPaint = new Paint();
        mIndicatorPaint.setColor(0xFF00AAFF);
    }

    public void setListener(TabListener listener) { mListener = listener; }

    public void rebuildTabs(List<String> titles, int selectedIndex) {
        removeAllViews();
        mTabs.clear();
        mTabs.addAll(titles);
        mSelectedIndex = selectedIndex;

        Context context = getContext();
        for (int i = 0; i < mTabs.size(); i++) {
            final int index = i;
            boolean isActive = (i == mSelectedIndex);

            LinearLayout tabItem = new LinearLayout(context);
            tabItem.setOrientation(HORIZONTAL);
            tabItem.setGravity(Gravity.CENTER_VERTICAL);
            tabItem.setBackground(createColorDrawable(isActive ? 0xFF1E1E1E : 0xFF2D2D2D));

            TextView dragHandle = UIUtils.createLockedTextView(context, " ⋮⋮ ", 12f, 0xFF666666);
            dragHandle.setPadding(UIUtils.dp2pxInt(6), 0, UIUtils.dp2pxInt(2), 0);
            tabItem.addView(dragHandle);

            TextView title = UIUtils.createLockedTextView(context, mTabs.get(i), 12f, isActive ? 0xFFFFFFFF : 0xFFAAAAAA);
            tabItem.addView(title);

            TextView closeBtn = UIUtils.createLockedTextView(context, " ×", 12f, 0xFF888888);
            closeBtn.setPadding(0, 0, UIUtils.dp2pxInt(8), 0);
            closeBtn.setOnClickListener(v -> { if (mListener != null) mListener.onTabClosed(index); });
            tabItem.addView(closeBtn);

            title.setOnClickListener(v -> { if (mListener != null) mListener.onTabSelected(index); });

            attachDragListener(dragHandle, tabItem, index);

            addView(tabItem, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // 添加 '+' 按钮
        TextView addBtn = UIUtils.createLockedTextView(context, " ＋ ", 14f, 0xFFCCCCCC);
        addBtn.setGravity(Gravity.CENTER);
        addBtn.setOnClickListener(v -> { if (mListener != null) mListener.onTabCreated(); });
        addView(addBtn, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void attachDragListener(View handle, View tabLayout, int currentIndex) {
        final float[] startX = {0};
        final boolean[] isDragging = {false};

        handle.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    isDragging[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX[0];
                    if (!isDragging[0] && Math.abs(dx) > mTouchSlop) {
                        isDragging[0] = true;
                        tabLayout.setAlpha(0.4f);
                    }
                    if (isDragging[0]) {
                        int[] loc = new int[2];
                        getLocationOnScreen(loc);
                        float dropX = event.getRawX() - loc[0];
                        updateIndicatorPosition(dropX);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging[0]) {
                        calculateAndMoveTab(event.getRawX(), currentIndex);
                    }
                    tabLayout.setAlpha(1.0f);
                    mIndicatorX = -1;
                    invalidate();
                    break;
            }
            return true;
        });
    }

    private void updateIndicatorPosition(float dropX) {
        int targetX = getWidth();
        for (int i = 0; i < getChildCount() - 1; i++) { // 排除最后一个 '+' 按钮
            View child = getChildAt(i);
            if (dropX < child.getLeft() + child.getWidth() / 2f) {
                targetX = child.getLeft();
                break;
            }
        }
        if (mIndicatorX != targetX) {
            mIndicatorX = targetX;
            invalidate();
        }
    }

    private void calculateAndMoveTab(float rawX, int currentIndex) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        float dropX = rawX - loc[0];

        int targetIdx = getChildCount() - 1;
        for (int i = 0; i < getChildCount() - 1; i++) {
            View child = getChildAt(i);
            if (dropX < child.getLeft() + child.getWidth() / 2f) {
                targetIdx = i;
                break;
            }
        }
        if (targetIdx > currentIndex) targetIdx--;
        if (targetIdx != currentIndex && mListener != null) {
            final int finalTarget = targetIdx;
            post(() -> mListener.onTabMoved(currentIndex, finalTarget));
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mIndicatorX >= 0) {
            int half = UIUtils.dp2pxInt(INDICATOR_WIDTH);
            int drawX = Math.max(half, Math.min(getWidth() - half, mIndicatorX));
            canvas.drawRect(drawX - half, 0, drawX + half, getHeight(), mIndicatorPaint);
        }
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }
}