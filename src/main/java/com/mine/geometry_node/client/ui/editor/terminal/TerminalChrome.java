package com.mine.geometry_node.client.ui.editor.terminal;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import net.minecraft.network.chat.Component;

/** Terminal chrome only; command editing and ANSI styling retain their own protocols. */
final class TerminalChrome {
    private TerminalChrome() {}

    static UiActionButton action(Context context, String text) {
        UiActionButton button = UiActionButton.withPalette(context, text, palette(false),
                UiActionButton.Density.NORMAL, new UiActionButton.Style(0, 0, 0, UiMetrics.DISABLED_ALPHA));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        UIUtils.setLockedTextSize(button, 12);
        button.setSingleLine(true);
        // Toolbar actions must not steal the shell input sink or command draft focus.
        button.setFocusable(false);
        return button;
    }

    static UiActionButton.Palette palette(boolean selected) {
        return new UiActionButton.Palette(selected ? UiThemes.EDITOR.control().icon() : 0,
                UiThemes.EDITOR.control().iconHover(), UiThemes.EDITOR.control().iconPressed(),
                selected ? UiThemes.EDITOR.text().strong() : UiThemes.EDITOR.text().muted());
    }

    static String tr(String suffix) {
        return Component.translatable("geometry_node.terminal." + suffix).getString();
    }
}
