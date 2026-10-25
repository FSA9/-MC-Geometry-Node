package com.mine.geometry_node.client.ui.components.composite.menu;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.menu.UiPopupOverlay;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuPanelLayout;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuItems;
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

public class UiContextMenu extends UiPopupOverlay {
    private static final int MENU_WIDTH_DP = 160;
    private static final int MENU_ITEM_HEIGHT_DP = 26;
    private final UiTheme theme;

    private final LinearLayout mContentLayout;
    private final UiScrollView mPanel;
    private UiContextMenu mParentMenu;
    private UiContextMenu mChildMenu;
    private View mOpenSubMenuItem;
    private ViewGroup mHostParent;
    private View mOwner;
    private Runnable mOnDismissListener;
    private boolean mDismissed = true;

    public UiContextMenu(Context context) {
        this(context, UiThemes.EDITOR);
    }

    public UiContextMenu(Context context, UiTheme theme) {
        super(context);
        this.theme = java.util.Objects.requireNonNull(theme, "theme");
        mContentLayout = new LinearLayout(context);
        mContentLayout.setOrientation(LinearLayout.VERTICAL);
        mPanel = UiMenuPanelLayout.createPanel(context, mContentLayout, theme);
        // A scrolled-away branch must not keep a detached submenu at its old position.
        mPanel.setOnScrollChangeListener((view, x, y, oldX, oldY) -> dismissChildMenu());
        addView(mPanel, new FrameLayout.LayoutParams(-2, -2));
    }

    public void addMenuItem(String text, Runnable action) {
        addMenuItem(text, null, action);
    }

    /** A single-choice option, including styled labels supplied by rich-text fields. */
    public void addChoiceItem(CharSequence text, boolean selected, Runnable action) {
        TextView item = UiMenuItems.row(getContext(), "", selected, true, () -> {
            if (mDismissed || !rootMenu().isOpen()) return;
            action.run();
            dismissRoot();
        }, theme);
        item.setText(text);
        item.setOnHoverListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) dismissChildMenu();
            return false;
        });
        mContentLayout.addView(item, menuItemLayoutParams());
    }

    public void addMenuItem(String text, String shortcut, Runnable action) {
        LinearLayout row = createMenuRow();
        TextView label = createMenuText(text, theme.text().secondary(), Gravity.LEFT | Gravity.CENTER_VERTICAL, 12.0f);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        if (shortcut != null && !shortcut.isBlank()) {
            TextView shortcutView = createMenuText(shortcut, theme.text().muted(), Gravity.RIGHT | Gravity.CENTER_VERTICAL, 10.0f);
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

    public void addSubMenuItem(String text, Consumer<UiContextMenu> builder) {
        LinearLayout row = createMenuRow();
        TextView label = createMenuText(text, theme.text().secondary(), Gravity.LEFT | Gravity.CENTER_VERTICAL, 12.0f);
        TextView arrow = createMenuText("›", theme.text().secondary(), Gravity.RIGHT | Gravity.CENTER_VERTICAL, 13.0f);
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

    private void showSubMenu(View anchor, Consumer<UiContextMenu> builder) {
        UiContextMenu rootMenu = rootMenu();
        if (!anchor.isEnabled() || mDismissed || !rootMenu.isOpen() || rootMenu.mHostParent == null) return;
        if (mChildMenu != null && mOpenSubMenuItem == anchor) {
            return;
        }
        dismissChildMenu();
        anchor.setSelected(true);

        UiContextMenu childMenu = new UiContextMenu(getContext(), theme);
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
        UiSearchMenuLayout.addHeader(mContentLayout, text, false, theme);
    }

    public void addDivider() {
        View divider = new View(getContext());
        ShapeDrawable line = new ShapeDrawable(); line.setColor(theme.border().divider());
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
        showAt(x, y, parent, parent);
    }

    private void showAt(float x, float y, View owner, ViewGroup parent) {
        if (getParent() != null) dismiss();
        // UiPopupOverlay defers removal when called during layout.
        if (getParent() != null || parent == null || parent.isInLayout()
                || parent.getWidth() <= 0 || parent.getHeight() <= 0) return;
        mHostParent = parent;
        mOwner = owner;
        layoutPanel(mPanel, x, y, parent, x);
        mDismissed = false;
        if (!attachOverlay(owner, parent)) {
            mDismissed = true;
            mHostParent = null;
            mOwner = null;
        }
    }

    /** Anchor placement in the chosen host, including its transforms and scrolling. */
    public void showBelow(View anchor, ViewGroup host) {
        if (anchor == null || host == null) return;
        Matrix transform = new Matrix();
        anchor.transformMatrixToGlobal(transform);
        float[] point = {0, anchor.getHeight()};
        transform.mapPoint(point);
        if (!UIUtils.rootToLocal(host, point[0], point[1], new Matrix(), point)) return;
        showAt(point[0], point[1], anchor, host);
    }

    public void setOnDismissListener(Runnable listener) {
        mOnDismissListener = listener;
    }

    private void dismissRoot() {
        rootMenu().dismiss();
    }

    @Override
    public boolean isOpen() {
        // A never-attached standalone host sends no detach callback when its anchor is removed.
        return !mDismissed && super.isOpen() && mOwner != null
                && mOwner.getRootView() == getRootView();
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
        mOwner = null;
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
        for (UiContextMenu menu = this; menu != null; menu = menu.mChildMenu) {
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

    private void showPanelAt(UiContextMenu rootMenu, float x, float fallbackX, float y) {
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

    private UiContextMenu rootMenu() {
        UiContextMenu menu = this;
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
        LinearLayout row = new LinearLayout(getContext()) {
            @Override public boolean performClick() { return isEnabled() && super.performClick(); }
        };
        row.setOrientation(LinearLayout.HORIZONTAL);
        UiMenuItems.decorate(row, false, theme);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
        return row;
    }

    private TextView createMenuText(String text, int color, int gravity, float textSizeDp) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(UiMenuItems.textColors(color, theme));
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
