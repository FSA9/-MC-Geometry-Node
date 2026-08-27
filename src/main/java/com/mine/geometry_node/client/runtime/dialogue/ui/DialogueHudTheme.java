package com.mine.geometry_node.client.runtime.dialogue.ui;

/**
 * Shared color tokens for the in-game dialogue and shop HUD.
 */
public final class DialogueHudTheme {
    public static final int OVERLAY_DIM = 0x78000000;
    public static final int PANEL = 0xF2111315;
    public static final int SURFACE = 0xD91A1D1F;
    public static final int SURFACE_HOVER = 0xF0222729;
    public static final int DIVIDER = 0x55D5B46A;
    public static final int TEXT_PRIMARY = 0xFFF0EEE7;
    public static final int TEXT_MUTED = 0xFF8F97A5;
    public static final int ACCENT = 0xFFD5B46A;
    public static final int ACCENT_HOVER = 0xFFE1C37C;
    public static final int ACCENT_PRESSED = 0xFFB79A55;
    public static final int BUTTON = 0xFF292D2F;
    public static final int BUTTON_HOVER = 0xFF353A3D;
    public static final int BUTTON_PRESSED = 0xFF202426;
    public static final int DISABLED = 0xFF202325;
    public static final int SUCCESS = 0xFF72C58D;
    public static final int ERROR = 0xFFEA7C73;
    public static final int WARNING = 0xFFE2B565;

    private DialogueHudTheme() {
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
