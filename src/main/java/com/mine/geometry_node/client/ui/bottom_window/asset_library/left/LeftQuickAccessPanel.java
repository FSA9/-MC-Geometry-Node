package com.mine.geometry_node.client.ui.bottom_window.asset_library.left;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetBrowserPanel;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;
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
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.io.File;
import java.util.List;

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;

public class LeftQuickAccessPanel extends ScrollView {

    private final AssetBrowserPanel mCoordinator;
    private final QuickAccessListLayout mLeftSidebar;
    private final float mTouchSlop;

    private static final float TITLE_BAR_HEIGHT = 40.0f;
    private static final float ROW_HEIGHT = 40.0f;
    private static final float DRAG_HANDLE_WIDTH = 24.0f;
    private static final float TEXT_SIZE_TITLE = 14.0f;
    private static final float TEXT_SIZE_PATH = 14.0f;
    private static final float TEXT_SIZE_HANDLE = 12.0f;
    private static final float TEXT_SIZE_NAV = 14.0f;

    public LeftQuickAccessPanel(Context context, AssetBrowserPanel coordinator) {
        super(context);
        mCoordinator = coordinator;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mLeftSidebar = new QuickAccessListLayout(context);
        mLeftSidebar.setOrientation(LinearLayout.VERTICAL);
        mLeftSidebar.setBackground(createColorDrawable(0xFF1E1E1E));

        addView(mLeftSidebar, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildSidebar();
    }

    public void buildSidebar() {
        Context context = getContext();
        mLeftSidebar.removeAllViews();

        // 快速访问
        TextView title = UIUtils.createLockedTextView(context, "快速访问", TEXT_SIZE_TITLE, 0xFF888888);
        title.setPadding(dp2pxInt(10), 0, dp2pxInt(10), 0);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(TITLE_BAR_HEIGHT));
        mLeftSidebar.addView(title, titleParams);

        for (String pathStr : ConfigManager.INSTANCE.getConfig().assetBrowser.quickAccessPaths) {
            mLeftSidebar.addView(createQuickAccessRow(context, pathStr));
        }
    }

    private LinearLayout createQuickAccessRow(Context context, String pathStr) {
        File file = new File(pathStr);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(createColorDrawable(0xFF2A2A2A));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(ROW_HEIGHT));
        rowParams.setMargins(0, 0, 0, dp2pxInt(2));
        row.setLayoutParams(rowParams);

        TextView dragHandle = UIUtils.createLockedTextView(context, " ⋮⋮ ", TEXT_SIZE_HANDLE, 0xFF666666);
        dragHandle.setGravity(Gravity.CENTER);
        row.addView(dragHandle, new LinearLayout.LayoutParams(dp2pxInt(DRAG_HANDLE_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));

        final float[] startY = {0};
        final boolean[] isDragging = {false};

        dragHandle.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    startY[0] = event.getRawY();
                    isDragging[0] = false;
                    this.requestDisallowInterceptTouchEvent(true);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dy = event.getRawY() - startY[0];
                    if (!isDragging[0] && Math.abs(dy) > mTouchSlop) {
                        isDragging[0] = true;
                        row.setAlpha(0.4f);
                    }
                    if (isDragging[0]) {
                        int[] loc = new int[2];
                        mLeftSidebar.getLocationOnScreen(loc);
                        float dropY = event.getRawY() - loc[1];

                        int indicatorY = mLeftSidebar.getHeight();
                        if (mLeftSidebar.getChildCount() > 1) {
                            View lastChild = mLeftSidebar.getChildAt(mLeftSidebar.getChildCount() - 1);
                            indicatorY = lastChild.getBottom();
                        }

                        for (int i = 1; i < mLeftSidebar.getChildCount(); i++) {
                            View child = mLeftSidebar.getChildAt(i);
                            float centerY = child.getTop() + child.getHeight() / 2f;
                            if (dropY < centerY) {
                                indicatorY = child.getTop();
                                break;
                            }
                        }
                        mLeftSidebar.updateIndicator(indicatorY);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging[0]) {
                        int[] loc = new int[2];
                        mLeftSidebar.getLocationOnScreen(loc);
                        float dropY = event.getRawY() - loc[1];

                        int targetIdx = mLeftSidebar.getChildCount() - 1;
                        for (int i = 1; i < mLeftSidebar.getChildCount(); i++) {
                            View child = mLeftSidebar.getChildAt(i);
                            if (dropY < child.getTop() + child.getHeight() / 2f) {
                                targetIdx = i - 1;
                                break;
                            }
                        }

                        List<String> list = ConfigManager.INSTANCE.getConfig().assetBrowser.quickAccessPaths;
                        int currentIndex = list.indexOf(pathStr);

                        if (targetIdx > currentIndex) {
                            targetIdx--;
                        }

                        if (targetIdx != currentIndex && targetIdx >= 0) {
                            String item = list.remove(currentIndex);
                            list.add(targetIdx, item);
                            ConfigManager.INSTANCE.save();
                            row.post(this::buildSidebar);
                        }
                    }
                    row.setAlpha(1.0f);
                    mLeftSidebar.hideIndicator();
                    this.requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return true;
        });

        String displayName = file.getName().isEmpty() ? file.getAbsolutePath() : file.getName();
        TextView btnPath = UIUtils.createLockedTextView(context, "📂 " + displayName, TEXT_SIZE_PATH, 0xFFDDDDDD);
        btnPath.setPadding(dp2pxInt(6), 0, dp2pxInt(15), 0);
        btnPath.setGravity(Gravity.CENTER_VERTICAL);
        btnPath.setOnClickListener(v -> mCoordinator.dispatchNavigateTo(file));

        row.addView(btnPath, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView btnDel = UIUtils.createLockedTextView(context, "－", TEXT_SIZE_NAV, 0xFFCC4444);
        btnDel.setGravity(Gravity.CENTER);
        btnDel.setBackground(createColorDrawable(0xFF3A3A3A));

        btnDel.setOnHoverListener((v, event) -> {
            btnDel.setBackground(createColorDrawable(event.getAction() == MotionEvent.ACTION_HOVER_ENTER ? 0xFF882222 : 0xFF3A3A3A));
            return true;
        });

        btnDel.setOnClickListener(v -> {
            ConfigManager.INSTANCE.getConfig().assetBrowser.quickAccessPaths.remove(pathStr);
            ConfigManager.INSTANCE.save();
            buildSidebar();
        });

        row.addView(btnDel, new LinearLayout.LayoutParams(dp2pxInt(ROW_HEIGHT), ViewGroup.LayoutParams.MATCH_PARENT));

        return row;
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static class QuickAccessListLayout extends LinearLayout {
        private int mIndicatorY = -1;
        private final Paint mIndicatorPaint;

        public QuickAccessListLayout(Context context) {
            super(context);
            setWillNotDraw(false);
            mIndicatorPaint = new Paint();
            mIndicatorPaint.setColor(0xFF00AAFF);
        }

        public void updateIndicator(int y) {
            if (mIndicatorY != y) {
                mIndicatorY = y;
                invalidate();
            }
        }

        public void hideIndicator() {
            if (mIndicatorY != -1) {
                mIndicatorY = -1;
                invalidate();
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (mIndicatorY >= 0) {
                int indicatorHalfHeight = dp2pxInt(2);
                int drawY = Math.max(indicatorHalfHeight, Math.min(getHeight() - indicatorHalfHeight, mIndicatorY));
                canvas.drawRect(0, drawY - indicatorHalfHeight, getWidth(), drawY + indicatorHalfHeight, mIndicatorPaint);
            }
        }
    }
}