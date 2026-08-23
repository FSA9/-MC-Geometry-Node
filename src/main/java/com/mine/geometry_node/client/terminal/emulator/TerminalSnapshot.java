package com.mine.geometry_node.client.terminal.emulator;

import java.util.List;

public record TerminalSnapshot(
        int columns,
        int rows,
        List<List<TerminalCell>> lines,
        int cursorLine,
        int cursorColumn,
        boolean cursorVisible,
        boolean bracketedPaste,
        boolean applicationCursorKeys) {
    public TerminalSnapshot {
        lines = lines.stream().map(List::copyOf).toList();
    }
}
