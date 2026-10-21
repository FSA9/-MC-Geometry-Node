package com.mine.geometry_node.client.ui.editor.terminal;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
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
    private int generation;
    private final float mTouchSlop;
    private final int[] mScreenLocation = new int[2];

    // 拖拽指示器
    private int mIndicatorX = -1;
    private final Paint mIndicatorPaint;
    private static final float INDICATOR_WIDTH = 2.0f;

    public TerminalTabBar(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setBackground(createColorDrawable(UiThemes.EDITOR.surface().panel()));
        setWillNotDraw(false);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mIndicatorPaint = new Paint();
        mIndicatorPaint.setColor(UiThemes.EDITOR.selection().outline());
    }

    public void setListener(TabListener listener) { mListener = listener; }

    View getTabView(int index) {
        return index >= 0 && index < mTabs.size() ? getChildAt(index) : null;
    }

    View getAddButtonView() {
        return getChildCount() > mTabs.size() ? getChildAt(mTabs.size()) : null;
    }

    public void rebuildTabs(List<String> titles, int selectedIndex) {
        generation++;
        mIndicatorX = -1;
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
            tabItem.setBackground(createColorDrawable(isActive ? UiThemes.EDITOR.surface().root() : UiThemes.EDITOR.surface().header()));

            TextView dragHandle = UIUtils.createLockedTextView(context, " ⋮⋮ ", 12f, UiThemes.EDITOR.text().hint());
            dragHandle.setPadding(UIUtils.dp2pxInt(6), 0, UIUtils.dp2pxInt(2), 0);
            dragHandle.setTooltipText(TerminalChrome.tr("tab.reorder"));
            dragHandle.setContentDescription(TerminalChrome.tr("tab.reorder"));
            tabItem.addView(dragHandle);

            TextView title = UIUtils.createLockedTextView(context, mTabs.get(i), 12f,
                    isActive ? UiThemes.EDITOR.text().strong() : UiThemes.EDITOR.text().muted());
            title.setSingleLine(true);
            title.setSelected(isActive);
            tabItem.addView(title);

            UiIconButton closeBtn = iconButton(context, SvgIconView.Icon.CLOSE, "tab.close");
            closeBtn.setEnabled(mTabs.size() > 1);
            closeBtn.setOnClickListener(v -> { if (mListener != null) mListener.onTabClosed(index); });
            tabItem.addView(closeBtn, new LayoutParams(UIUtils.dp2pxInt(24), ViewGroup.LayoutParams.MATCH_PARENT));

            title.setOnClickListener(v -> { if (mListener != null) mListener.onTabSelected(index); });

            attachDragListener(dragHandle, tabItem, index);

            addView(tabItem, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // 添加 '+' 按钮
        UiIconButton addBtn = iconButton(context, SvgIconView.Icon.PLUS, "tab.create");
        addBtn.setEnabled(mTabs.size() < 32);
        addBtn.setOnClickListener(v -> { if (mListener != null) mListener.onTabCreated(); });
        addView(addBtn, new LayoutParams(UIUtils.dp2pxInt(28), ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static UiIconButton iconButton(Context context, SvgIconView.Icon icon, String tooltip) {
        return new UiIconButton(context, new SvgIconView(context, icon, UiThemes.EDITOR.text().muted()),
                new UiIconButton.Style(0, 0, 0, UiThemes.EDITOR.control().iconHover(),
                        UiThemes.EDITOR.control().iconPressed(), 0, 0, 14, UiMetrics.DISABLED_ALPHA))
                .tooltip(TerminalChrome.tr(tooltip));
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
                    requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX[0];
                    if (!isDragging[0] && Math.abs(dx) > mTouchSlop) {
                        isDragging[0] = true;
                        tabLayout.setAlpha(0.4f);
                    }
                    if (isDragging[0]) {
                        getLocationOnScreen(mScreenLocation);
                        float dropX = event.getRawX() - mScreenLocation[0];
                        updateIndicatorPosition(dropX);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (isDragging[0]) {
                        calculateAndMoveTab(event.getRawX(), currentIndex);
                    }
                    isDragging[0] = false;
                    tabLayout.setAlpha(1.0f);
                    mIndicatorX = -1;
                    invalidate();
                    requestDisallowInterceptTouchEvent(false);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    isDragging[0] = false;
                    tabLayout.setAlpha(1.0f);
                    mIndicatorX = -1;
                    invalidate();
                    requestDisallowInterceptTouchEvent(false);
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
        getLocationOnScreen(mScreenLocation);
        float dropX = rawX - mScreenLocation[0];

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
            final int requestedGeneration = generation;
            final TabListener listener = mListener;
            post(() -> {
                if (requestedGeneration == generation && listener == mListener) {
                    listener.onTabMoved(currentIndex, finalTarget);
                }
            });
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        generation++;
        mIndicatorX = -1;
        super.onDetachedFromWindow();
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
