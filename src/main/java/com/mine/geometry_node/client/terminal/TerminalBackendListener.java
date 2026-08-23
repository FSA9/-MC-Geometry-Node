package com.mine.geometry_node.client.terminal;

/**
 * Serialized backend events. A backend reports started at most once, exited exactly once, and no
 * output after exit. Callback failures must not be allowed to break backend resource cleanup.
 */
public interface TerminalBackendListener {
    void onStarted();

    /**
     * Bytes are terminal display data only and must never be interpreted as an Agent tool protocol.
     * Ownership remains with the caller; receivers must copy data they retain.
     */
    void onOutput(byte[] bytes);

    void onExited(TerminalExit exit);
}
