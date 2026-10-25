package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.FrameLayout;

final class SettingsEditorStyle {
    static final int TEXT = UiThemes.EDITOR.text().normal();
    static final int MUTED = UiThemes.EDITOR.text().muted();
    static final int ACCENT = UiThemes.EDITOR.border().focus();
    static final int DANGER = UiThemes.EDITOR.status().error();
    static final int CONTROL = UiThemes.EDITOR.control().input();
    static final int CONTROL_HOVER = UiThemes.EDITOR.control().iconHover();
    static final int CONTROL_ERROR = UiThemes.EDITOR.status().errorBackground();
    static final int BORDER = UiThemes.EDITOR.control().inputBorder();
    static final int BORDER_ERROR = UiThemes.EDITOR.status().errorBorder();

    private SettingsEditorStyle() {}

    static ShapeDrawable rect(int color, float radiusDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeColor != 0) drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(1)), strokeColor);
        return drawable;
    }

    static UiActionButton actionButton(Context context, String text) {
        UiActionButton button = UiActionButton.withPalette(context, text,
                new UiActionButton.Palette(CONTROL, CONTROL_HOVER,
                        UiThemes.EDITOR.control().iconPressed(), TEXT),
                UiActionButton.Density.COMPACT,
                new UiActionButton.Style(3.0f, UiMetrics.BORDER, BORDER, UiMetrics.DISABLED_ALPHA));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        UIUtils.setLockedTextSize(button, 10.5f);
        button.setFocusableInTouchMode(true);
        return button;
    }

    static FrameLayout iconButton(Context context, SvgIconView.Icon iconType, String tooltip) {
        return UiIconButton.createThemed(context, UiThemes.EDITOR, iconType, tooltip);
    }
}
