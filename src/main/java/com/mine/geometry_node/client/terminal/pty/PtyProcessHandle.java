package com.mine.geometry_node.client.terminal.pty;

import com.mine.geometry_node.client.terminal.TerminalSize;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletionStage;

/**
 * Platform PTY handle. {@link #terminate()} requests graceful termination; {@link #close()} must be
 * idempotent, non-throwing, force termination of the remaining process tree, and release handles.
 * A normally exited process must eventually complete {@link #exitCode()} and close {@link #stdout()}.
 */
public interface PtyProcessHandle extends AutoCloseable {
    InputStream stdout();

    OutputStream stdin();

    CompletionStage<Integer> exitCode();

    void resize(TerminalSize size) throws IOException;

    void interrupt() throws IOException;

    void terminate();

    @Override
    void close();
}
