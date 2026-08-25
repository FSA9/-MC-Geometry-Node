package com.mine.geometry_node.client.terminal.emulator;

import java.util.Objects;

public record TerminalCell(String text, TerminalStyle style, int width) {
    private static final TerminalCell DEFAULT_BLANK = new TerminalCell(" ", TerminalStyle.DEFAULT, 1);
    private static final TerminalCell DEFAULT_CONTINUATION = new TerminalCell("", TerminalStyle.DEFAULT, 0);

    public TerminalCell {
        text = Objects.requireNonNull(text, "text");
        style = Objects.requireNonNull(style, "style");
        if (width < 0 || width > 2) throw new IllegalArgumentException("cell width must be 0, 1, or 2");
    }

    public static TerminalCell blank(TerminalStyle style) {
        return TerminalStyle.DEFAULT.equals(style) ? DEFAULT_BLANK : new TerminalCell(" ", style, 1);
    }

    public static TerminalCell continuation(TerminalStyle style) {
        return TerminalStyle.DEFAULT.equals(style)
                ? DEFAULT_CONTINUATION
                : new TerminalCell("", style, 0);
    }
}
