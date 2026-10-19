package com.mine.geometry_node.client.ui.editor.asset.menu;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.menu.UiPopupOverlay;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuPanelLayout;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuItems;
import com.mine.geometry_node.client.ui.components.composite.menu.UiSearchMenuLayout;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import icyllis.modernui.graphics.Matrix;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import com.mine.geometry_node.client.ui.utils.UIUtils;

import java.util.function.Consumer;

public class FileContextMenu extends UiPopupOverlay {
    private static final int MENU_WIDTH_DP = 160;
    private static final int MENU_ITEM_HEIGHT_DP = 26;
    private static final int COLOR_TEXT = UiTheme.Text.SECONDARY;
    private static final int COLOR_SHORTCUT_TEXT = UiTheme.Text.MUTED;

    private final LinearLayout mContentLayout;
    private final UiScrollView mPanel;
    private FileContextMenu mParentMenu;
    private FileContextMenu mChildMenu;
    private View mOpenSubMenuItem;
    private ViewGroup mHostParent;
    private Runnable mOnDismissListener;
    private boolean mDismissed = true;

    public FileContextMenu(Context context) {
        super(context);
        mContentLayout = new LinearLayout(context);
        mContentLayout.setOrientation(LinearLayout.VERTICAL);
        mPanel = UiMenuPanelLayout.createPanel(context, mContentLayout);
        // A scrolled-away branch must not keep a detached submenu at its old position.
        mPanel.setOnScrollChangeListener((view, x, y, oldX, oldY) -> dismissChildMenu());
        addView(mPanel, new FrameLayout.LayoutParams(-2, -2));
    }

    public void addMenuItem(String text, Runnable action) {
        addMenuItem(text, null, action);
    }

    public void addMenuItem(String text, String shortcut, Runnable action) {
        LinearLayout row = createMenuRow();
        TextView label = createMenuText(text, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12.0f);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        if (shortcut != null && !shortcut.isBlank()) {
            TextView shortcutView = createMenuText(shortcut, COLOR_SHORTCUT_TEXT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 10.0f);
            row.addView(shortcutView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        row.setOnClickListener(v -> {
            if (mDismissed || !rootMenu().isOpen()) return;
            action.run();
            dismissRoot();
        });

        row.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                dismissChildMenu();
            }
            return false;
        });

        mContentLayout.addView(row, menuItemLayoutParams());
    }

    public void addSubMenuItem(String text, Consumer<FileContextMenu> builder) {
        LinearLayout row = createMenuRow();
        TextView label = createMenuText(text, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12.0f);
        TextView arrow = createMenuText("›", COLOR_TEXT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 13.0f);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        row.addView(arrow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Runnable showAction = () -> showSubMenu(row, builder);
        row.setOnClickListener(v -> showAction.run());
        row.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                showAction.run();
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                if (mChildMenu == null) {
                    row.setSelected(false);
                }
            }
            return false;
        });

        mContentLayout.addView(row, menuItemLayoutParams());
    }

    private void showSubMenu(View anchor, Consumer<FileContextMenu> builder) {
        FileContextMenu rootMenu = rootMenu();
        if (mDismissed || !rootMenu.isOpen() || rootMenu.mHostParent == null) return;
        if (mChildMenu != null && mOpenSubMenuItem == anchor) {
            return;
        }
        dismissChildMenu();
        anchor.setSelected(true);

        FileContextMenu childMenu = new FileContextMenu(getContext());
        childMenu.mParentMenu = this;
        builder.accept(childMenu);

        // Include ancestor scale and scrolling; screen-coordinate subtraction loses both.
        Matrix anchorMatrix = new Matrix();
        Matrix inverse = new Matrix();
        anchor.transformMatrixToGlobal(anchorMatrix);
        float[] point = {0, 0};
        anchorMatrix.mapPoint(point);
        if (!UIUtils.rootToLocal(rootMenu, point[0], point[1], inverse, point)) {
            anchor.setSelected(false);
            return;
        }
        float anchorLeft = point[0];
        float localY = point[1];
        point[0] = anchor.getWidth();
        point[1] = 0;
        anchorMatrix.mapPoint(point);
        if (!UIUtils.rootToLocal(rootMenu, point[0], point[1], inverse, point)) {
            anchor.setSelected(false);
            return;
        }
        float localX = point[0];
        float fallbackX = anchorLeft - Math.min(UIUtils.dp2pxInt(MENU_WIDTH_DP), rootMenu.getWidth());

        mChildMenu = childMenu;
        mOpenSubMenuItem = anchor;
        childMenu.showPanelAt(rootMenu, localX, fallbackX, localY);
    }

    public void addTitle(String text) {
        UiSearchMenuLayout.addHeader(mContentLayout, text, false);
    }

    public void addDivider() {
        View divider = new View(getContext());
        ShapeDrawable line = new ShapeDrawable(); line.setColor(UiTheme.Surface.ROOT);
        divider.setBackground(line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(1));
        lp.setMargins(0, UIUtils.dp2pxInt(4), 0, UIUtils.dp2pxInt(4));
        mContentLayout.addView(divider, lp);
    }

    public boolean hasItems() {
        return mContentLayout.getChildCount() > 0;
    }

    /** Shows at host-local coordinates after the host has usable layout bounds. */
    public void showAt(float x, float y, ViewGroup parent) {
        if (getParent() != null) dismiss();
        // UiPopupOverlay defers removal when called during layout.
        if (getParent() != null || parent == null || parent.isInLayout()
                || parent.getWidth() <= 0 || parent.getHeight() <= 0) return;
        mHostParent = parent;
        layoutPanel(mPanel, x, y, parent, x);
        mDismissed = false;
        if (!attachOverlay(parent, parent)) {
            mDismissed = true;
            mHostParent = null;
        }
    }

    public void setOnDismissListener(Runnable listener) {
        mOnDismissListener = listener;
    }

    private void dismissRoot() {
        rootMenu().dismiss();
    }

    @Override
    public boolean isOpen() {
        return !mDismissed && super.isOpen();
    }

    @Override
    protected void onDismissRequested() {
        if (mDismissed) return;
        mDismissed = true;
        dismissChildMenu();
        if (mParentMenu != null && mPanel.getParent() instanceof ViewGroup parent) {
            clearPanelTooltips(mPanel);
            mPanel.setVisibility(GONE);
            // Child panels share the root overlay, so its layout traversal is the boundary.
            if (parent.isInLayout()) mPanel.post(() -> {
                if (mPanel.getParent() == parent) parent.removeView(mPanel);
            });
            else parent.removeView(mPanel);
        }
        mHostParent = null;
        if (mOnDismissListener != null) {
            Runnable listener = mOnDismissListener;
            mOnDismissListener = null;
            listener.run();
        }
    }

    private static void clearPanelTooltips(View view) {
        CharSequence tooltip = view.getTooltipText();
        if (tooltip != null) {
            view.setTooltipText(null);
            view.setTooltipText(tooltip);
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) clearPanelTooltips(group.getChildAt(i));
        }
    }

    @Override
    protected boolean containsPopup(float localX, float localY) {
        for (FileContextMenu menu = this; menu != null; menu = menu.mChildMenu) {
            View panel = menu.mPanel;
            if (!menu.mDismissed && panel.getVisibility() == VISIBLE && panel.getParent() == this
                    && localX >= panel.getLeft() && localX < panel.getRight()
                    && localY >= panel.getTop() && localY < panel.getBottom()) return true;
        }
        return false;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (oldWidth > 0 && oldHeight > 0 && (width != oldWidth || height != oldHeight)) dismiss();
    }

    private void showPanelAt(FileContextMenu rootMenu, float x, float fallbackX, float y) {
        mHostParent = rootMenu.mHostParent;
        if (mPanel.getParent() instanceof ViewGroup parent) parent.removeView(mPanel);
        mDismissed = false;
        rootMenu.addView(mPanel);
        layoutPanel(mPanel, x, y, rootMenu.mHostParent, fallbackX);
    }

    private void dismissChildMenu() {
        if (mChildMenu == null) return;
        mChildMenu.dismiss();
        mChildMenu = null;
        if (mOpenSubMenuItem != null) {
            mOpenSubMenuItem.setSelected(false);
            mOpenSubMenuItem = null;
        }
    }

    private FileContextMenu rootMenu() {
        FileContextMenu menu = this;
        while (menu.mParentMenu != null) {
            menu = menu.mParentMenu;
        }
        return menu;
    }

    private void layoutPanel(View panel, float x, float y, ViewGroup boundsParent, float fallbackX) {
        int parentWidth = boundsParent.getWidth();
        int parentHeight = boundsParent.getHeight();
        FrameLayout.LayoutParams lp = UiMenuPanelLayout.measurePanel(panel,
                UIUtils.dp2pxInt(MENU_WIDTH_DP), parentWidth, parentHeight, 0);
        // Preserve the cascade's left-side fallback and the context menu's upward flip.
        if (x + lp.width > parentWidth) x = fallbackX;
        if (y + lp.height > parentHeight) y -= lp.height;
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = UiMenuPanelLayout.clampStart(x, lp.width, parentWidth);
        lp.topMargin = UiMenuPanelLayout.clampStart(y, lp.height, parentHeight);
        panel.setLayoutParams(lp);
    }

    private LinearLayout createMenuRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        UiMenuItems.decorate(row, false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
        return row;
    }

    private TextView createMenuText(String text, int color, int gravity, float textSizeDp) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(UiMenuItems.textColors(color));
        tv.setDuplicateParentStateEnabled(true);
        tv.setTextSize(0, UIUtils.dp2px(textSizeDp));
        tv.setSingleLine(true);
        tv.setGravity(gravity);
        return tv;
    }

    private LinearLayout.LayoutParams menuItemLayoutParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(MENU_ITEM_HEIGHT_DP));
    }

}
