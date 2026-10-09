package com.mine.geometry_node.client.ui.theme;

/** Visual defaults only. Canvas units, hit testing and gesture thresholds stay outside the theme. */
public final class UiTheme {
    private UiTheme() {}

    public static final class Surface {
        public static final int CANVAS = 0xFF181818;
        public static final int ROOT = 0xFF1D1D1D;
        public static final int PANEL = 0xFF252525;
        public static final int HEADER = 0xFF2D2D2D;
        public static final int SIDEBAR = 0xFF303030;
        public static final int NODE = 0xE6303030;
        private Surface() {}
    }

    public static final class Text {
        public static final int STRONG = 0xFFFFFFFF;
        public static final int NORMAL = 0xFFE6E6E6;
        public static final int SECONDARY = 0xFFCCCCCC;
        public static final int MUTED = 0xFFAAAAAA;
        public static final int HINT = 0xFF737B86;
        public static final int DISABLED = 0xFF999999;
        private Text() {}
    }

    public static final class Metrics {
        public static final float TEXT_COMPACT = 10.0f;
        public static final float TEXT_CONTROL = 10.5f;
        public static final float TEXT_SEARCH = 14.0f;
        public static final float RADIUS_SMALL = 2.0f;
        public static final float RADIUS_INPUT = 3.0f;
        public static final float BORDER = 1.0f;
        public static final float DISABLED_ALPHA = 0.48f;
        public static final float ICON_BUTTON_SIZE = 24.0f;
        public static final float ICON_SIZE = 14.0f;
        private Metrics() {}
    }

    public static final class Control {
        public static final int BORDER = 0xFF5C5C5C;
        public static final int FOCUS = 0xFF6FA2DD;
        public static final int ERROR = 0xFFE08080;
        public static final int INPUT = 0xFF202020;
        public static final int INPUT_BORDER = 0xFF484848;
        public static final int ICON = 0xFF3B3B3B;
        public static final int ICON_HOVER = 0xFF484848;
        public static final int ICON_PRESSED = 0xFF303030;
        public static final int ICON_BORDER = 0xFF444444;
        public static final int ICON_DISABLED_BORDER = 0xFF383838;
        public static final int CHECKED = 0xFF3D6EA8;
        public static final int CHECK_BORDER = 0xFF3A3A3A;
        private Control() {}
    }

    public record ButtonColors(int normal, int hover, int pressed, int text) {}

    public static final ButtonColors PRIMARY = new ButtonColors(0xFF2F7DDE, 0xFF3D8BEA, 0xFF2768BA, Text.STRONG);
    public static final ButtonColors SECONDARY = new ButtonColors(0xFF454545, 0xFF525252, 0xFF383838, 0xFFD8D8D8);
    public static final ButtonColors INLINE = new ButtonColors(0xFF30343B, 0xFF3A4049, 0xFF292D33, 0xFFBFC7D5);
    public static final ButtonColors DANGER = new ButtonColors(0xFF9A3D3D, 0xFFAC4A4A, 0xFF7C3030, Text.STRONG);
    public static final ButtonColors QUIET = new ButtonColors(0xFF4C6B4C, 0xFF5B7B5B, 0xFF3D583D, 0xFFE8F0E8);
}
