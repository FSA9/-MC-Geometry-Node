package com.mine.geometry_node.client.ui.editor.datalibrary.presentation;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextUtils;
import net.minecraft.network.chat.Component;

/** Database translation vocabulary and compact action appearance; generic primitives stay in components. */
public final class DatabasePresentation {
    private DatabasePresentation() {}
    public static String tr(String key, Object... args) {
        return Component.translatable("geometry_node.database." + key, args).getString();
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
        view.setPadding(UIUtils.dp2pxInt(9), 0, UIUtils.dp2pxInt(9), 0);
        UIUtils.setLockedTextSize(view, 12);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setOnClickListener(v -> action.run());
        return view;
    }
}
