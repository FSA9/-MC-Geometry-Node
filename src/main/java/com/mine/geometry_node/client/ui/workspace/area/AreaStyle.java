package com.mine.geometry_node.client.ui.workspace.area;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.graphics.drawable.ShapeDrawable;

final class AreaStyle {
    static final int COLOR_ROOT = UiThemes.EDITOR.surface().root();
    static final int COLOR_PLATE = UiThemes.EDITOR.surface().canvas();
    static final int COLOR_PANE = UiThemes.EDITOR.control().input();
    static final int COLOR_PANE_BORDER = UiThemes.EDITOR.border().subtle();
    static final int COLOR_ACTIVE_BORDER = UiThemes.EDITOR.border().focus();
    static final int COLOR_HEADER = UiThemes.EDITOR.surface().header();
    static final int COLOR_HEADER_ACTIVE = UiThemes.EDITOR.selection().subtle();
    static final int COLOR_CONTENT = UiThemes.EDITOR.surface().canvas();
    static final int COLOR_TEXT = UiThemes.EDITOR.text().normal();
    static final int COLOR_TEXT_MUTED = UiThemes.EDITOR.text().muted();
    static final int COLOR_ICON = UiThemes.EDITOR.text().secondary();
    static final int COLOR_ICON_SELECTED = UiThemes.EDITOR.text().strong();
    static final int COLOR_BUTTON_BG_HOVER = UiThemes.EDITOR.control().iconHover();
    static final int COLOR_BUTTON_BG_SELECTED = UiThemes.EDITOR.selection().fill();
    static final int COLOR_BUTTON_BORDER = UiThemes.EDITOR.border().strong();
    static final int COLOR_ACCENT = UiThemes.EDITOR.selection().outline();
    static final int COLOR_MENU_BG = UiThemes.EDITOR.surface().popup();
    static final int COLOR_MENU_BORDER = UiThemes.EDITOR.border().subtle();
    static final int COLOR_MENU_DIVIDER = UiThemes.EDITOR.border().divider();
    static final int COLOR_MENU_SECTION_TEXT = UiThemes.EDITOR.text().muted();
    static final int COLOR_MENU_SELECTED = UiThemes.EDITOR.selection().subtle();
    static final int COLOR_DIVIDER = UiThemes.EDITOR.border().divider();
    static final int COLOR_DIVIDER_DRAG = UiThemes.EDITOR.selection().outline();
    static final int COLOR_DIVIDER_DRAG_FILL = UiThemes.EDITOR.selection().marqueeFill();

    static final float ROOT_PADDING_DP = 4.0f;
    static final float PANE_GAP_DP = 2.0f;
    static final float PANE_RADIUS_DP = 5.0f;
    static final float HEADER_HEIGHT_DP = 27.0f;
    static final float HEADER_PADDING_X_DP = 6.0f;
    static final float HEADER_BUTTON_SIZE_DP = 21.0f;
    static final float HEADER_BUTTON_GAP_DP = 2.0f;
    static final float DIVIDER_HITBOX_DP = 8.0f;
    static final float DIVIDER_NORMAL_DP = 1.0f;
    static final float DIVIDER_DRAG_DP = 5.0f;

    private AreaStyle() {
    }

    static ShapeDrawable rect(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    static ShapeDrawable rounded(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = rect(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        }
        return drawable;
    }
}
