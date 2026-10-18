package com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils;

import com.mine.geometry_node.client.ui.components.icon.VectorIconView;
import com.mine.geometry_node.client.ui.components.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.input.UiTextInput;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

/**
 * Stateless view factories and styling shared by graph-properties components.
 */
public final class GraphPropertiesUi {
    public static final int ACTION_SIZE_DP = 26;
    public static final int ACTION_BACKGROUND = 0xFF4A4A4A;
    public static final int ACTION_HOVER = 0xFF5A5A5A;
    public static final int ACTION_PRESSED = 0xFF3A3A3A;

    public static UiIconButton.Style actionStyle(float iconSizeDp) {
        return new UiIconButton.Style(3, 0, ACTION_BACKGROUND, ACTION_HOVER,
                ACTION_PRESSED, UiTheme.Surface.PANEL, 0, iconSizeDp, UiTheme.Metrics.DISABLED_ALPHA);
    }

    public static UiIconButton actionIcon(Context context, SvgIconView.Icon icon, String tooltipKey) {
        return new UiIconButton(context, new SvgIconView(context, icon, UiTheme.Text.NORMAL), actionStyle(14))
                .tooltip(tr(tooltipKey));
    }

    public static UiIconButton actionIcon(Context context, VectorIconView.Kind icon, String tooltipKey) {
        return new UiIconButton(context, new VectorIconView(context, icon, UiTheme.Text.NORMAL), actionStyle(14))
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
                UiTheme.Metrics.TEXT_CONTROL, 7.0f, UiTheme.Metrics.RADIUS_INPUT,
                UiTheme.Metrics.BORDER, textColor, hintColor, backgroundColor, borderColor));
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
                new UiActionButton.Style(UiTheme.Metrics.RADIUS_INPUT, 0, 0,
                        UiTheme.Metrics.DISABLED_ALPHA));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setSingleLine(true);
        button.setTooltipText(tooltip);
        return button;
    }

    public static FrameLayout iconButton(
            Context context,
            VectorIconView.Kind kind,
            String tooltip,
            int iconSizeDp,
            int iconColor,
            int normalColor,
            int hoverColor) {
        UiIconButton.Style style = new UiIconButton.Style(3.0f, 0.0f, normalColor,
                hoverColor, hoverColor, normalColor, 0, iconSizeDp, 0.5f);
        VectorIconView icon = new VectorIconView(context, kind, iconColor);
        return new UiIconButton(context, icon, style).tooltip(tooltip);
    }

    /** Two half-height controls share one square toolbar slot. */
    public static LinearLayout orderButtons(Context context, Runnable moveUp, Runnable moveDown,
                                            String upTooltipKey, String downTooltipKey) {
        LinearLayout order = new LinearLayout(context);
        order.setOrientation(LinearLayout.VERTICAL);
        UiIconButton up = new UiIconButton(context,
                new VectorIconView(context, VectorIconView.Kind.CHEVRON_UP, UiTheme.Text.NORMAL), actionStyle(8))
                .tooltip(tr(upTooltipKey));
        up.setOnClickListener(v -> moveUp.run());
        order.addView(up, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        View divider = new View(context);
        divider.setBackground(rect(ACTION_PRESSED, 0));
        order.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, UIUtils.dp2pxInt(1))));
        UiIconButton down = new UiIconButton(context,
                new VectorIconView(context, VectorIconView.Kind.CHEVRON_DOWN, UiTheme.Text.NORMAL), actionStyle(8))
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
