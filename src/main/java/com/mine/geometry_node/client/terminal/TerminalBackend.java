package com.mine.geometry_node.client.terminal;

import java.io.IOException;

/** Owns one interactive process channel. Implementations must make {@link #close()} idempotent and non-throwing. */
public interface TerminalBackend extends AutoCloseable {
    void start(TerminalBackendListener listener) throws IOException;

    void write(byte[] input) throws IOException;

    void resize(TerminalSize size) throws IOException;

    void interrupt() throws IOException;

    @Override
    void close();
}
