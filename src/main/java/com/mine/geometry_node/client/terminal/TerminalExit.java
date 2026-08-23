package com.mine.geometry_node.client.terminal;

import java.util.Objects;

public record TerminalExit(Integer exitCode, Reason reason, String message) {
    public TerminalExit {
        Objects.requireNonNull(reason, "reason");
        message = message == null ? "" : message;
    }

    public static TerminalExit stopped() {
        return new TerminalExit(null, Reason.TERMINATED, "Terminal backend stopped");
    }

    public boolean failed() {
        return reason == Reason.START_FAILED || reason == Reason.IO_FAILURE;
    }

    public enum Reason {
        NORMAL,
        INTERRUPTED,
        TERMINATED,
        START_FAILED,
        IO_FAILURE
    }
}
