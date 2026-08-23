package com.mine.geometry_node.client.terminal;

/** Backend callbacks may arrive off-thread; UI implementations must marshal work to the UI thread. */
public interface TerminalSessionListener {
    TerminalSessionListener NOOP = new TerminalSessionListener() {};

    default void onStateChanged(TerminalRunState state) {}

    default void onOutput(byte[] bytes) {}

    default void onExited(TerminalExit exit) {}
}
