package com.mine.geometry_node.client.terminal;

public record TerminalSize(int columns, int rows) {
    private static final int MAX_COLUMNS = 1_000;
    private static final int MAX_ROWS = 1_000;

    public TerminalSize {
        if (columns < 2 || columns > MAX_COLUMNS) {
            throw new IllegalArgumentException("columns must be between 2 and " + MAX_COLUMNS);
        }
        if (rows < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException("rows must be between 1 and " + MAX_ROWS);
        }
    }
}
