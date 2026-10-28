package com.mine.geometry_node.client.ui.components.integration.graph.properties.utils;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

/**
 * Stateless view factories and styling shared by graph-properties components.
 */
public final class GraphPropertiesUi {
    public static final int ACTION_SIZE_DP = 26;
    public static final int ACTION_BACKGROUND = UiThemes.EDITOR.control().icon();
    public static final int ACTION_HOVER = UiThemes.EDITOR.control().iconHover();
    public static final int ACTION_PRESSED = UiThemes.EDITOR.control().iconPressed();

    public static UiIconButton.Style actionStyle(float iconSizeDp) {
        return UiIconButton.Style.forTheme(UiThemes.EDITOR).withSize(0, 0, iconSizeDp);
    }

    public static UiIconButton actionIcon(Context context, SvgIconView.Icon icon, String tooltipKey) {
        return new UiIconButton(context, new SvgIconView(context, icon, UiThemes.EDITOR.text().normal()), actionStyle(14))
                .tooltip(tr(tooltipKey));
    }

    public static LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(ACTION_SIZE_DP), UIUtils.dp2pxInt(ACTION_SIZE_DP));
        lp.leftMargin = UIUtils.dp2pxInt(4);
        return lp;
    }

    private GraphPropertiesUi() {
    }

    public static TextView label(Context context, String text, float sizeDp, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(0, UIUtils.dp2px(sizeDp));
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        return view;
    }

    public static EditText singleLineInput(
            Context context,
            String value,
            String hint,
            int textColor,
            int hintColor,
            int backgroundColor,
            int borderColor) {
        UiTextInput input = new UiTextInput(context, new UiTextInput.Style(
                UiMetrics.TEXT_CONTROL, 7.0f, UiMetrics.RADIUS_INPUT,
                UiMetrics.BORDER, textColor, hintColor, backgroundColor, borderColor));
        input.setSingleLine(true);
        input.setText(value != null ? value : "");
        input.setHint(hint);
        return input;
    }

    public static UiActionButton button(
            Context context,
            String text,
            String tooltip,
            int textColor,
            int normalColor,
            int hoverColor) {
        UiActionButton button = UiActionButton.withPalette(context, text,
                new UiActionButton.Palette(normalColor, hoverColor, hoverColor, textColor),
                UiActionButton.Density.COMPACT,
                new UiActionButton.Style(UiMetrics.RADIUS_INPUT, 0, 0,
                        UiMetrics.DISABLED_ALPHA));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setSingleLine(true);
        button.setTooltipText(tooltip);
        return button;
    }

    /** Two half-height controls share one square toolbar slot. */
    public static LinearLayout orderButtons(Context context, Runnable moveUp, Runnable moveDown,
                                            String upTooltipKey, String downTooltipKey) {
        LinearLayout order = new LinearLayout(context);
        order.setOrientation(LinearLayout.VERTICAL);
        UiIconButton up = new UiIconButton(context,
                new SvgIconView(context, SvgIconView.Icon.CHEVRON_UP, UiThemes.EDITOR.text().normal()), actionStyle(8))
                .tooltip(tr(upTooltipKey));
        up.setOnClickListener(v -> moveUp.run());
        order.addView(up, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        View divider = new View(context);
        divider.setBackground(rect(ACTION_PRESSED, 0));
        order.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, UIUtils.dp2pxInt(1))));
        UiIconButton down = new UiIconButton(context,
                new SvgIconView(context, SvgIconView.Icon.CHEVRON_DOWN, UiThemes.EDITOR.text().normal()), actionStyle(8))
                .tooltip(tr(downTooltipKey));
        down.setOnClickListener(v -> moveDown.run());
        order.addView(down, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        return order;
    }

    public static ShapeDrawable rect(int color, float radiusDp) {
        return rect(color, radiusDp, 0, 0);
    }

    public static ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        return drawable;
    }

    public static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
