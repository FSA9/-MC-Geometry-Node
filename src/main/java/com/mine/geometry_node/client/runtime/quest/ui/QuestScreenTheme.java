package com.mine.geometry_node.client.runtime.quest.ui;

import com.mine.geometry_node.client.runtime.dialogue.ui.DialogueHudTheme;

/** Quest journal surfaces retain the shared warm HUD palette with lighter translucent layers. */
final class QuestScreenTheme {
    static final int PANEL = DialogueHudTheme.PANEL;
    static final int SURFACE = DialogueHudTheme.SURFACE;
    static final int SURFACE_HOVER = withAlpha(DialogueHudTheme.SURFACE_HOVER, 0xA8);
    static final int DIVIDER = withAlpha(DialogueHudTheme.DIVIDER, 0x30);
    static final int TEXT_PRIMARY = DialogueHudTheme.TEXT_PRIMARY;
    static final int TEXT_MUTED = DialogueHudTheme.TEXT_MUTED;
    static final int WARNING = DialogueHudTheme.ACCENT;
    static final int BUTTON = withAlpha(DialogueHudTheme.BUTTON, 0xA0);
    static final int BUTTON_HOVER = withAlpha(DialogueHudTheme.BUTTON_HOVER, 0xC0);
    static final int SUCCESS = DialogueHudTheme.SUCCESS;
    static final int ERROR = DialogueHudTheme.ERROR;

    private QuestScreenTheme() {}

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
