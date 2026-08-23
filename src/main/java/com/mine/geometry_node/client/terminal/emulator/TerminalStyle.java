package com.mine.geometry_node.client.terminal.emulator;

public record TerminalStyle(int foreground, int background, boolean bold, boolean underline, boolean inverse) {
    public static final int DEFAULT_FOREGROUND = 0xFFD4D4D4;
    public static final int DEFAULT_BACKGROUND = 0xFF1E1E1E;
    public static final TerminalStyle DEFAULT = new TerminalStyle(
            DEFAULT_FOREGROUND, DEFAULT_BACKGROUND, false, false, false);

    public int effectiveForeground() { return inverse ? background : foreground; }
    public int effectiveBackground() { return inverse ? foreground : background; }
}
