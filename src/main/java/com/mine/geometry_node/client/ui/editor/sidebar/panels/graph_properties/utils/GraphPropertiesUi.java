package com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils;

import com.mine.geometry_node.client.ui.common.VectorIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
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
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setText(value != null ? value : "");
        input.setHint(hint);
        input.setTextColor(textColor);
        input.setHintTextColor(hintColor);
        input.setTextSize(0, UIUtils.dp2px(10.5f));
        input.setPadding(UIUtils.dp2pxInt(7), 0, UIUtils.dp2pxInt(7), 0);
        input.setBackground(rect(backgroundColor, 3.0f, 1, borderColor));
        return input;
    }

    public static TextView button(
            Context context,
            String text,
            String tooltip,
            int textColor,
            int normalColor,
            int hoverColor) {
        TextView button = label(context, text, 10.0f, textColor);
        button.setGravity(Gravity.CENTER);
        button.setTooltipText(tooltip);
        styleButton(button, normalColor, hoverColor);
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
        FrameLayout button = new FrameLayout(context);
        button.setTooltipText(tooltip);
        styleButton(button, normalColor, hoverColor);
        VectorIconView icon = new VectorIconView(context, kind, iconColor);
        icon.setClickable(false);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                UIUtils.dp2pxInt(iconSizeDp),
                UIUtils.dp2pxInt(iconSizeDp));
        iconParams.gravity = Gravity.CENTER;
        button.addView(icon, iconParams);
        return button;
    }

    public static LinearLayout orderButtons(
            Context context,
            Runnable moveUp,
            Runnable moveDown,
            String upTooltip,
            String downTooltip,
            int iconColor,
            int normalColor,
            int hoverColor) {
        LinearLayout order = new LinearLayout(context);
        order.setOrientation(LinearLayout.VERTICAL);
        FrameLayout up = iconButton(
                context,
                VectorIconView.Kind.CHEVRON_UP,
                upTooltip,
                9,
                iconColor,
                normalColor,
                hoverColor);
        up.setOnClickListener(v -> moveUp.run());
        order.addView(up, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(14)));

        FrameLayout down = iconButton(
                context,
                VectorIconView.Kind.CHEVRON_DOWN,
                downTooltip,
                9,
                iconColor,
                normalColor,
                hoverColor);
        down.setOnClickListener(v -> moveDown.run());
        LinearLayout.LayoutParams downParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(14));
        downParams.topMargin = UIUtils.dp2pxInt(2);
        order.addView(down, downParams);
        return order;
    }

    public static void styleButton(View button, int normalColor, int hoverColor) {
        button.setBackground(rect(normalColor, 3.0f, 0, 0));
        button.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                v.setBackground(rect(hoverColor, 3.0f, 0, 0));
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                v.setBackground(rect(normalColor, 3.0f, 0, 0));
            }
            return false;
        });
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
