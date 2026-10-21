package com.mine.geometry_node.client.ui.editor.database;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.text.TextUtils;
import net.minecraft.network.chat.Component;

/** Small presentation helpers shared by the two database views. */
public final class DatabaseWidgets {
    private DatabaseWidgets() {}
    public static String tr(String key, Object... args) {
        return Component.translatable("geometry_node.database." + key, args).getString();
    }
    /** Project-scaled pixels. Do not rename to dp: View subclasses inherit a different density conversion. */
    public static int px(float value) { return UIUtils.dp2pxInt(value); }
    public static UiActionButton.Palette rowPalette(boolean selected, int base) {
        return UiTableStyle.rowPalette(selected, base);
    }
    public static ShapeDrawable background(int color) {
        ShapeDrawable drawable = new ShapeDrawable(); drawable.setColor(color); return drawable;
    }
    public static TextView label(Context context, String text, int color) {
        TextView view = UIUtils.createLockedTextView(context, text, 12, color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    public static UiActionButton button(Context context, String text, Runnable action) {
        return button(context, text, action, UiActionButton.Role.SECONDARY);
    }
    public static UiActionButton button(Context context, String text, Runnable action, UiActionButton.Role role) {
        UiActionButton view = new UiActionButton(context, text,
                role, UiActionButton.Density.COMPACT,
                new UiActionButton.Style(0, UiMetrics.BORDER, UiThemes.EDITOR.border().strong(),
                        UiMetrics.DISABLED_ALPHA));
        view.setMinHeight(0); view.setMinWidth(0);
        view.setPadding(px(9), 0, px(9), 0);
        UIUtils.setLockedTextSize(view, 12);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setOnClickListener(v -> action.run());
        return view;
    }
}
