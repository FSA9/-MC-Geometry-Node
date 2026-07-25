package com.mine.geometry_node.client.ui.area;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.graphics.drawable.ShapeDrawable;

final class AreaStyle {
    static final int COLOR_ROOT = UIConstants.CLR_BG_DARK_2;
    static final int COLOR_PLATE = 0xFF171717;
    static final int COLOR_PANE = 0xFF202020;
    static final int COLOR_PANE_BORDER = 0xFF101010;
    static final int COLOR_HEADER = 0xFF2A2A2A;
    static final int COLOR_HEADER_ACTIVE = 0xFF30343A;
    static final int COLOR_CONTENT = UIConstants.CLR_BG_DARK_1;
    static final int COLOR_TEXT = 0xFFE0E0E0;
    static final int COLOR_TEXT_MUTED = 0xFF9A9A9A;
    static final int COLOR_ICON = 0xFFB8C0CC;
    static final int COLOR_ICON_SELECTED = 0xFFFFFFFF;
    static final int COLOR_BUTTON_BG_HOVER = 0xFF3A4652;
    static final int COLOR_BUTTON_BG_SELECTED = 0xFF1F5D8F;
    static final int COLOR_BUTTON_BORDER = 0xFF526070;
    static final int COLOR_ACCENT = UIConstants.ViewPort.Selection.CLR_BORDER;
    static final int COLOR_MENU_BG = 0xFF2B2B2B;
    static final int COLOR_MENU_BORDER = 0xFF151515;
    static final int COLOR_MENU_DIVIDER = 0xFF171717;
    static final int COLOR_MENU_SECTION_TEXT = 0xFF777777;
    static final int COLOR_MENU_SELECTED = 0xFF30343A;
    static final int COLOR_DIVIDER = 0xFF070707;
    static final int COLOR_DIVIDER_DRAG = UIConstants.ViewPort.Selection.CLR_BORDER;
    static final int COLOR_DIVIDER_DRAG_FILL = 0x3344AAFF;

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
