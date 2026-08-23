package com.mine.geometry_node.client.terminal.shell;

import com.mine.geometry_node.client.terminal.TerminalExit;
import com.mine.geometry_node.client.terminal.TerminalRunState;

public interface ShellTerminalObserver {
    ShellTerminalObserver NOOP = new ShellTerminalObserver() {};
    default void onScreenChanged() {}
    default void onStateChanged(TerminalRunState state) {}
    default void onExited(TerminalExit exit) {}
    default void onError(String message) {}
}
