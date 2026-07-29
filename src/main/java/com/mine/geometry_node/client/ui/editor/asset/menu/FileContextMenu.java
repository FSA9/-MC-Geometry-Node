package com.mine.geometry_node.client.ui.editor.asset.menu;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import com.mine.geometry_node.client.ui.utils.UIUtils;

import java.util.function.Consumer;

public class FileContextMenu extends FrameLayout {
    private static final int MENU_WIDTH_DP = 160;
    private static final int MENU_ITEM_HEIGHT_DP = 26;
    private static final int COLOR_TEXT = 0xFFCCCCCC;
    private static final int COLOR_SHORTCUT_TEXT = 0xFF8B949E;
    private static final int COLOR_HOVER_BG = 0xFF44AAFF;
    private static final int COLOR_HOVER_TEXT = 0xFFFFFFFF;

    private final LinearLayout mContentLayout;
    private FileContextMenu mParentMenu;
    private FileContextMenu mChildMenu;
    private View mOpenSubMenuItem;
    private ViewGroup mHostParent;

    public FileContextMenu(Context context) {
        super(context);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        setOnClickListener(v -> dismissRoot());

        mContentLayout = new LinearLayout(context);
        mContentLayout.setOrientation(LinearLayout.VERTICAL);
        mContentLayout.setOnClickListener(v -> {});

        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(0xFF2D2D2D);
        bg.setCornerRadius(4);
        mContentLayout.setBackground(bg);
        mContentLayout.setPadding(4, 4, 4, 4);

        addView(mContentLayout);
    }

    public void addMenuItem(String text, Runnable action) {
        addMenuItem(text, null, action);
    }

    public void addMenuItem(String text, String shortcut, Runnable action) {
        LinearLayout row = createMenuRow();
        TextView label = createMenuText(text, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12.0f);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView shortcutView = null;
        if (shortcut != null && !shortcut.isBlank()) {
            shortcutView = createMenuText(shortcut, COLOR_SHORTCUT_TEXT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 10.0f);
            row.addView(shortcutView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        TextView finalShortcutView = shortcutView;
        row.setOnClickListener(v -> {
            action.run();
            dismissRoot();
        });

        row.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                dismissChildMenu();
                setItemHovered(row, label, finalShortcutView, true);
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                setItemHovered(row, label, finalShortcutView, false);
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
                    setRowHovered(row, false);
                }
            }
            return false;
        });

        mContentLayout.addView(row, menuItemLayoutParams());
    }

    private void showSubMenu(View anchor, Consumer<FileContextMenu> builder) {
        FileContextMenu rootMenu = rootMenu();
        if (rootMenu.mHostParent == null) return;
        if (mChildMenu != null && mOpenSubMenuItem == anchor) {
            return;
        }
        dismissChildMenu();
        setRowHovered(anchor, true);

        FileContextMenu childMenu = new FileContextMenu(getContext());
        childMenu.mParentMenu = this;
        builder.accept(childMenu);

        int[] rootLoc = new int[2];
        int[] anchorLoc = new int[2];
        rootMenu.getLocationOnScreen(rootLoc);
        anchor.getLocationOnScreen(anchorLoc);
        float anchorLeft = anchorLoc[0] - rootLoc[0];
        float localX = anchorLeft + anchor.getWidth();
        float fallbackX = anchorLeft - UIUtils.dp2pxInt(MENU_WIDTH_DP);
        float localY = anchorLoc[1] - rootLoc[1];

        mChildMenu = childMenu;
        mOpenSubMenuItem = anchor;
        childMenu.showPanelAt(rootMenu, localX, fallbackX, localY);
    }

    public void addTitle(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(0xFF888888);
        tv.setPadding(UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(30), UIUtils.dp2pxInt(4));
        mContentLayout.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void addDivider() {
        View divider = new View(getContext());
        ShapeDrawable line = new ShapeDrawable(); line.setColor(0xFF111111);
        divider.setBackground(line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(1));
        lp.setMargins(0, 4, 0, 4);
        mContentLayout.addView(divider, lp);
    }

    public boolean hasItems() {
        return mContentLayout.getChildCount() > 0;
    }

    public void showAt(float x, float y, ViewGroup parent) {
        mHostParent = parent;
        parent.addView(this);
        layoutPanel(mContentLayout, x, y, parent);
    }

    private void dismissRoot() {
        rootMenu().dismiss();
    }

    public void dismiss() {
        dismissChildMenu();
        if (getParent() != null) {
            ((ViewGroup) getParent()).removeView(this);
        } else if (mContentLayout.getParent() instanceof ViewGroup) {
            ((ViewGroup) mContentLayout.getParent()).removeView(mContentLayout);
        }
    }

    private void showPanelAt(FileContextMenu rootMenu, float x, float fallbackX, float y) {
        mHostParent = rootMenu.mHostParent;
        if (mContentLayout.getParent() instanceof ViewGroup) {
            ((ViewGroup) mContentLayout.getParent()).removeView(mContentLayout);
        }
        rootMenu.addView(mContentLayout);
        layoutPanel(mContentLayout, x, y, rootMenu.mHostParent, fallbackX);
    }

    private void dismissChildMenu() {
        if (mChildMenu == null) return;
        mChildMenu.dismiss();
        mChildMenu = null;
        if (mOpenSubMenuItem != null) {
            setRowHovered(mOpenSubMenuItem, false);
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

    private void layoutPanel(LinearLayout panel, float x, float y, ViewGroup boundsParent) {
        layoutPanel(panel, x, y, boundsParent, x);
    }

    private void layoutPanel(LinearLayout panel, float x, float y, ViewGroup boundsParent, float fallbackX) {
        int widthSpec = MeasureSpec.makeMeasureSpec(UIUtils.dp2pxInt(MENU_WIDTH_DP), MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        panel.measure(widthSpec, heightSpec);

        int menuWidth = panel.getMeasuredWidth();
        int menuHeight = panel.getMeasuredHeight();

        if (menuWidth == 0) menuWidth = UIUtils.dp2pxInt(MENU_WIDTH_DP);
        if (menuHeight == 0) menuHeight = UIUtils.dp2pxInt(200);

        int parentWidth = boundsParent == null ? getWidth() : boundsParent.getWidth();
        int parentHeight = boundsParent == null ? getHeight() : boundsParent.getHeight();

        float finalX = x;
        float finalY = y;

        if (finalX + menuWidth > parentWidth && parentWidth > 0) {
            float fallbackCandidate = Math.max(0, fallbackX);
            if (fallbackCandidate + menuWidth <= parentWidth) {
                finalX = fallbackCandidate;
            } else {
                finalX = Math.max(0, parentWidth - menuWidth);
            }
        }

        if (finalY + menuHeight > parentHeight && parentHeight > 0) {
            finalY = Math.max(0, y - menuHeight);
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(menuWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.setMargins((int) finalX, (int) finalY, 0, 0);
        panel.setLayoutParams(lp);
    }

    private LinearLayout createMenuRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
        return row;
    }

    private TextView createMenuText(String text, int color, int gravity, float textSizeDp) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(0, UIUtils.dp2px(textSizeDp));
        tv.setSingleLine(true);
        tv.setGravity(gravity);
        return tv;
    }

    private LinearLayout.LayoutParams menuItemLayoutParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(MENU_ITEM_HEIGHT_DP));
    }

    private void setRowHovered(View row, boolean hovered) {
        if (!(row instanceof LinearLayout layout)) return;
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (child instanceof TextView textView) {
                textView.setTextColor(hovered ? COLOR_HOVER_TEXT : COLOR_TEXT);
            }
        }
        if (hovered) {
            ShapeDrawable hoverBg = new ShapeDrawable();
            hoverBg.setColor(COLOR_HOVER_BG);
            row.setBackground(hoverBg);
        } else {
            row.setBackground(null);
        }
    }

    private void setItemHovered(View row, TextView label, TextView shortcutView, boolean hovered) {
        row.setBackground(null);
        if (hovered) {
            ShapeDrawable hoverBg = new ShapeDrawable();
            hoverBg.setColor(COLOR_HOVER_BG);
            row.setBackground(hoverBg);
        }
        label.setTextColor(hovered ? COLOR_HOVER_TEXT : COLOR_TEXT);
        if (shortcutView != null) {
            shortcutView.setTextColor(hovered ? COLOR_HOVER_TEXT : COLOR_SHORTCUT_TEXT);
        }
    }
}
