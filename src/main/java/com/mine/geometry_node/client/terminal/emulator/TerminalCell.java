package com.mine.geometry_node.client.terminal.emulator;

import java.util.Objects;

public record TerminalCell(String text, TerminalStyle style, int width) {
    public TerminalCell {
        text = Objects.requireNonNull(text, "text");
        style = Objects.requireNonNull(style, "style");
        if (width < 0 || width > 2) throw new IllegalArgumentException("cell width must be 0, 1, or 2");
    }

    public static TerminalCell blank(TerminalStyle style) { return new TerminalCell(" ", style, 1); }
    public static TerminalCell continuation(TerminalStyle style) { return new TerminalCell("", style, 0); }
}
