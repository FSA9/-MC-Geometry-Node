package com.mine.geometry_node.client.ui.bottom_window.asset_library.left;

import com.mine.geometry_node.client.ui.common.VectorIconView;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetBrowserPanel;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
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
    private String mSelectedKey = "";

    private static final float TITLE_BAR_HEIGHT = 40.0f;
    private static final float ROW_HEIGHT = 40.0f;
    private static final float DRAG_HANDLE_WIDTH = 24.0f;
    private static final float TEXT_SIZE_TITLE = 14.0f;
    private static final float TEXT_SIZE_PATH = 14.0f;
    private static final float TEXT_SIZE_HANDLE = 12.0f;
    private static final float TEXT_SIZE_NAV = 14.0f;
    private static final int COLOR_LOCAL_NORMAL = 0xFF2A2A2A;
    private static final int COLOR_LOCAL_HOVER = 0xFF343434;
    private static final int COLOR_LOCAL_PRESSED = 0xFF3F4D5B;
    private static final int COLOR_LOCAL_SELECTED = 0xFF33485E;
    private static final int COLOR_REMOTE_NORMAL = 0xFF263445;
    private static final int COLOR_REMOTE_HOVER = 0xFF2F4055;
    private static final int COLOR_REMOTE_PRESSED = 0xFF3C5874;
    private static final int COLOR_REMOTE_SELECTED = 0xFF36597A;

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

        mLeftSidebar.addView(createFavoritesRow(context));

        if (mCoordinator.canBrowseRemote()) {
            mLeftSidebar.addView(createRemoteServerRow(context));
        }

        for (String pathStr : ConfigManager.INSTANCE.getConfig().assetBrowser.quickAccessPaths) {
            mLeftSidebar.addView(createQuickAccessRow(context, pathStr));
        }
    }

    private LinearLayout createFavoritesRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        bindRowFeedback(row, "favorites", COLOR_LOCAL_NORMAL, COLOR_LOCAL_HOVER, COLOR_LOCAL_PRESSED, COLOR_LOCAL_SELECTED,
                () -> {
                    mSelectedKey = "favorites";
                    mCoordinator.dispatchNavigateToFavorites();
                    buildSidebar();
                });

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(ROW_HEIGHT));
        rowParams.setMargins(0, 0, 0, dp2pxInt(2));
        row.setLayoutParams(rowParams);

        TextView icon = UIUtils.createLockedTextView(context, " ★ ", TEXT_SIZE_HANDLE, 0xFFFFD166);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp2pxInt(DRAG_HANDLE_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView label = UIUtils.createLockedTextView(context, "我的收藏", TEXT_SIZE_PATH, 0xFFE6E6E6);
        label.setPadding(dp2pxInt(6), 0, dp2pxInt(15), 0);
        label.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        return row;
    }

    private LinearLayout createRemoteServerRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        bindRowFeedback(row, "remote", COLOR_REMOTE_NORMAL, COLOR_REMOTE_HOVER, COLOR_REMOTE_PRESSED, COLOR_REMOTE_SELECTED,
                () -> {
                    mSelectedKey = "remote";
                    mCoordinator.dispatchNavigateToRemoteRoot();
                    buildSidebar();
                });

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(ROW_HEIGHT));
        rowParams.setMargins(0, 0, 0, dp2pxInt(2));
        row.setLayoutParams(rowParams);

        VectorIconView icon = new VectorIconView(context, VectorIconView.Kind.CLOUD, 0xFF8FBFFF);
        row.addView(icon, new LinearLayout.LayoutParams(dp2pxInt(DRAG_HANDLE_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView btnPath = UIUtils.createLockedTextView(context, "远程服务器", TEXT_SIZE_PATH, 0xFFE6F1FF);
        btnPath.setPadding(dp2pxInt(6), 0, dp2pxInt(15), 0);
        btnPath.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(btnPath, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        return row;
    }

    private LinearLayout createQuickAccessRow(Context context, String pathStr) {
        File file = new File(pathStr);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        bindRowFeedback(row, "local:" + pathStr, COLOR_LOCAL_NORMAL, COLOR_LOCAL_HOVER, COLOR_LOCAL_PRESSED, COLOR_LOCAL_SELECTED,
                () -> {
                    mSelectedKey = "local:" + pathStr;
                    mCoordinator.dispatchNavigateTo(file);
                    buildSidebar();
                });

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

                        int firstQuickAccessChild = mCoordinator.canBrowseRemote() ? 3 : 2;
                        for (int i = firstQuickAccessChild; i < mLeftSidebar.getChildCount(); i++) {
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

                        int firstQuickAccessChild = mCoordinator.canBrowseRemote() ? 3 : 2;
                        List<String> currentPaths = ConfigManager.INSTANCE.getConfig().assetBrowser.quickAccessPaths;
                        int targetIdx = currentPaths.size() - 1;
                        for (int i = firstQuickAccessChild; i < mLeftSidebar.getChildCount(); i++) {
                            View child = mLeftSidebar.getChildAt(i);
                            if (dropY < child.getTop() + child.getHeight() / 2f) {
                                targetIdx = i - firstQuickAccessChild;
                                break;
                            }
                        }

                        int currentIndex = currentPaths.indexOf(pathStr);

                        if (targetIdx > currentIndex) {
                            targetIdx--;
                        }

                        if (targetIdx != currentIndex && targetIdx >= 0) {
                            int finalTargetIdx = targetIdx;
                            ConfigManager.INSTANCE.update(config -> {
                                List<String> list = config.assetBrowser.quickAccessPaths;
                                int index = list.indexOf(pathStr);
                                if (index < 0 || finalTargetIdx < 0 || finalTargetIdx >= list.size()) return;
                                String item = list.remove(index);
                                list.add(finalTargetIdx, item);
                            });
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

        VectorIconView folderIcon = new VectorIconView(context, VectorIconView.Kind.FOLDER, 0xFFDDAA00);
        row.addView(folderIcon, new LinearLayout.LayoutParams(dp2pxInt(DRAG_HANDLE_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));

        String displayName = file.getName().isEmpty() ? file.getAbsolutePath() : file.getName();
        TextView btnPath = UIUtils.createLockedTextView(context, displayName, TEXT_SIZE_PATH, 0xFFDDDDDD);
        btnPath.setPadding(dp2pxInt(6), 0, dp2pxInt(15), 0);
        btnPath.setGravity(Gravity.CENTER_VERTICAL);

        row.addView(btnPath, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView btnDel = UIUtils.createLockedTextView(context, "－", TEXT_SIZE_NAV, 0xFFCC4444);
        btnDel.setGravity(Gravity.CENTER);
        btnDel.setBackground(createColorDrawable(0xFF3A3A3A));

        btnDel.setOnHoverListener((v, event) -> {
            btnDel.setBackground(createColorDrawable(event.getAction() == MotionEvent.ACTION_HOVER_ENTER ? 0xFF882222 : 0xFF3A3A3A));
            return true;
        });
        btnDel.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                btnDel.setBackground(createColorDrawable(0xFFAA3333));
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (isInside(btnDel, event)) {
                    ConfigManager.INSTANCE.update(config -> config.assetBrowser.quickAccessPaths.remove(pathStr));
                    buildSidebar();
                } else {
                    btnDel.setBackground(createColorDrawable(0xFF3A3A3A));
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                btnDel.setBackground(createColorDrawable(0xFF3A3A3A));
                return true;
            }
            return true;
        });

        row.addView(btnDel, new LinearLayout.LayoutParams(dp2pxInt(ROW_HEIGHT), ViewGroup.LayoutParams.MATCH_PARENT));

        return row;
    }

    private void bindRowFeedback(LinearLayout row, String key, int normalColor, int hoverColor, int pressedColor, int selectedColor, Runnable action) {
        final boolean[] hovered = {false};
        final boolean[] pressed = {false};
        final boolean[] moved = {false};
        final float[] downX = {0};
        final float[] downY = {0};
        Runnable update = () -> {
            int color;
            if (pressed[0]) {
                color = pressedColor;
            } else if (key.equals(mSelectedKey)) {
                color = selectedColor;
            } else if (hovered[0]) {
                color = hoverColor;
            } else {
                color = normalColor;
            }
            row.setBackground(createColorDrawable(color));
        };
        update.run();

        row.setOnClickListener(v -> action.run());
        row.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                hovered[0] = true;
                update.run();
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                hovered[0] = false;
                pressed[0] = false;
                update.run();
            }
            return false;
        });

        row.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getRawX();
                    downY[0] = event.getRawY();
                    moved[0] = false;
                    pressed[0] = true;
                    update.run();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getRawX() - downX[0]) > mTouchSlop || Math.abs(event.getRawY() - downY[0]) > mTouchSlop) {
                        moved[0] = true;
                        pressed[0] = false;
                        update.run();
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                    pressed[0] = false;
                    update.run();
                    return false;
                case MotionEvent.ACTION_CANCEL:
                    pressed[0] = false;
                    moved[0] = false;
                    update.run();
                    return false;
                default:
                    return false;
            }
        });
    }

    private boolean isInside(View view, MotionEvent event) {
        return event.getX() >= 0 && event.getY() >= 0
                && event.getX() < view.getWidth()
                && event.getY() < view.getHeight();
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
