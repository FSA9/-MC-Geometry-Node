package com.mine.geometry_node.client.terminal;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Closes a run-scoped control-plane resource when its process exits or is stopped. */
public final class ResourceOwnedTerminalBackend implements TerminalBackend {
    private final TerminalBackend delegate;
    private final AutoCloseable resource;
    private final AtomicBoolean closed = new AtomicBoolean();

    public ResourceOwnedTerminalBackend(TerminalBackend delegate, AutoCloseable resource) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.resource = Objects.requireNonNull(resource, "resource");
    }

    @Override
    public void start(TerminalBackendListener listener) throws IOException {
        try {
            delegate.start(new TerminalBackendListener() {
                @Override public void onStarted() { listener.onStarted(); }
                @Override public void onOutput(byte[] bytes) { listener.onOutput(bytes); }
                @Override public void onExited(TerminalExit exit) {
                    closeResource();
                    listener.onExited(exit);
                }
            });
        } catch (IOException | RuntimeException failure) {
            closeResource();
            throw failure;
        }
    }

    @Override public void write(byte[] input) throws IOException { delegate.write(input); }
    @Override public void resize(TerminalSize size) throws IOException { delegate.resize(size); }
    @Override public void interrupt() throws IOException { delegate.interrupt(); }

    @Override
    public void close() {
        try {
            delegate.close();
        } finally {
            closeResource();
        }
    }

    private void closeResource() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            resource.close();
        } catch (Exception ignored) {
        }
    }
}
