package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.components.common.UiIconButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.FrameLayout;

final class SettingsEditorStyle {
    static final int TEXT = 0xFFE3E3E3;
    static final int MUTED = 0xFF929292;
    static final int ACCENT = 0xFF6B6B6B;
    static final int DANGER = 0xFFE18A8A;
    static final int CONTROL = 0xFF242424;
    static final int CONTROL_HOVER = 0xFF3A3A3A;
    static final int CONTROL_ERROR = 0xFF5A3030;
    static final int BORDER = 0xFF4A4A4A;
    static final int BORDER_ERROR = 0xFFB85C5C;
    static final int ENABLED = 0xFF3F6D52;
    static final int DISABLED = 0xFF454545;

    private SettingsEditorStyle() {}

    static ShapeDrawable rect(int color, float radiusDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeColor != 0) drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(1)), strokeColor);
        return drawable;
    }

    static FrameLayout iconButton(Context context, SvgIconView.Icon iconType, String tooltip) {
        UiIconButton.Style style = new UiIconButton.Style(2.0f, 0.0f, CONTROL,
                CONTROL_HOVER, CONTROL_HOVER, CONTROL, 0, 14.0f, 0.5f);
        return new UiIconButton(context, new SvgIconView(context, iconType, MUTED), style)
                .tooltip(tooltip);
    }
}
