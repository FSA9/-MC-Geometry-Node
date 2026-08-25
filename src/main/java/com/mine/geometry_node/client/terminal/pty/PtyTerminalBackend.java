package com.mine.geometry_node.client.terminal.pty;

import com.mine.geometry_node.client.terminal.ProcessLaunchSpec;
import com.mine.geometry_node.client.terminal.TerminalBackend;
import com.mine.geometry_node.client.terminal.TerminalBackendListener;
import com.mine.geometry_node.client.terminal.TerminalExit;
import com.mine.geometry_node.client.terminal.TerminalSize;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PTY byte transport. It intentionally does not parse VT output; a terminal emulator consumes the
 * emitted bytes on a separate presentation boundary.
 */
public final class PtyTerminalBackend implements TerminalBackend {
    private static final int READ_BUFFER_SIZE = 8 * 1024;
    private static final long STOP_GRACE_MILLIS = 250;
    private static final long OUTPUT_DRAIN_TIMEOUT_MILLIS = 750;

    private final PtyProcessFactory processFactory;
    private final ProcessLaunchSpec launchSpec;
    private final Object inputLock = new Object();
    private final Object eventLock = new Object();
    private final AtomicReference<Lifecycle> lifecycle = new AtomicReference<>(Lifecycle.NEW);
    private final AtomicBoolean exitReported = new AtomicBoolean();
    private final AtomicBoolean outputFinished = new AtomicBoolean();
    private final AtomicReference<TerminalExit> pendingProcessExit = new AtomicReference<>();

    private volatile PtyProcessHandle process;
    private volatile TerminalBackendListener listener;

    public PtyTerminalBackend(PtyProcessFactory processFactory, ProcessLaunchSpec launchSpec) {
        this.processFactory = Objects.requireNonNull(processFactory, "processFactory");
        this.launchSpec = Objects.requireNonNull(launchSpec, "launchSpec");
    }

    @Override
    public void start(TerminalBackendListener listener) throws IOException {
        Objects.requireNonNull(listener, "listener");
        if (!lifecycle.compareAndSet(Lifecycle.NEW, Lifecycle.STARTING)) {
            throw new IllegalStateException("PTY backend can only be started once");
        }
        this.listener = listener;

        PtyProcessHandle startedProcess = null;
        try {
            startedProcess = Objects.requireNonNull(processFactory.start(launchSpec), "processFactory result");
            CompletionStage<Integer> exitCode = Objects.requireNonNull(
                    startedProcess.exitCode(), "process exitCode");
            process = startedProcess;
            if (!lifecycle.compareAndSet(Lifecycle.STARTING, Lifecycle.RUNNING)) {
                closeProcess(startedProcess);
                process = null;
                return;
            }
            emitStarted();
            Thread.ofVirtual().name("geometry-node-pty-output").start(this::readOutput);
            exitCode.whenComplete((code, error) -> {
                if (error != null) {
                    reportExit(new TerminalExit(null, TerminalExit.Reason.IO_FAILURE, safeMessage(error)));
                    return;
                }
                TerminalExit exit = new TerminalExit(code, TerminalExit.Reason.NORMAL, "PTY process exited");
                pendingProcessExit.set(exit);
                if (outputFinished.get()) {
                    reportExit(exit);
                } else {
                    Thread.ofVirtual().name("geometry-node-pty-drain-timeout").start(() -> {
                        try {
                            Thread.sleep(OUTPUT_DRAIN_TIMEOUT_MILLIS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        if (!outputFinished.get() && !exitReported.get()) {
                            PtyProcessHandle current = process;
                            if (current != null) closeProcess(current);
                            reportExit(exit);
                        }
                    });
                }
            });
        } catch (IOException | RuntimeException error) {
            lifecycle.set(Lifecycle.CLOSED);
            if (startedProcess != null) {
                closeProcess(startedProcess);
            }
            throw error;
        }
    }

    @Override
    public void write(byte[] input) throws IOException {
        Objects.requireNonNull(input, "input");
        PtyProcessHandle current = requireRunning();
        synchronized (inputLock) {
            OutputStream stream = current.stdin();
            stream.write(input);
            stream.flush();
        }
    }

    @Override
    public void resize(TerminalSize size) throws IOException {
        requireRunning().resize(Objects.requireNonNull(size, "size"));
    }

    @Override
    public void interrupt() throws IOException {
        PtyProcessHandle current = requireRunning();
        synchronized (inputLock) {
            current.interrupt();
        }
    }

    @Override
    public void close() {
        Lifecycle previous;
        while (true) {
            previous = lifecycle.get();
            if (previous == Lifecycle.CLOSING || previous == Lifecycle.CLOSED) return;
            Lifecycle target = previous == Lifecycle.NEW ? Lifecycle.CLOSED : Lifecycle.CLOSING;
            if (lifecycle.compareAndSet(previous, target)) break;
        }
        if (previous == Lifecycle.NEW) return;

        PtyProcessHandle current = process;
        if (current != null) {
            Thread.ofVirtual().name("geometry-node-pty-terminate").start(() -> {
                try {
                    current.terminate();
                } catch (RuntimeException ignored) {
                }
            });
            Thread.ofVirtual().name("geometry-node-pty-stop-grace").start(() -> {
                try {
                    Thread.sleep(STOP_GRACE_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                reportExit(TerminalExit.stopped());
            });
        } else {
            reportExit(TerminalExit.stopped());
        }
    }

    private void readOutput() {
        PtyProcessHandle current = process;
        if (current == null) {
            return;
        }
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        try (InputStream stream = current.stdout()) {
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                if (read > 0) {
                    emitOutput(Arrays.copyOf(buffer, read));
                }
            }
            outputFinished.set(true);
            TerminalExit exit = pendingProcessExit.get();
            if (exit != null) {
                reportExit(exit);
            }
        } catch (IOException | RuntimeException error) {
            TerminalExit processExit = pendingProcessExit.get();
            if (processExit != null) {
                outputFinished.set(true);
                reportExit(processExit);
            } else if (lifecycle.get() == Lifecycle.RUNNING) {
                reportExit(new TerminalExit(null, TerminalExit.Reason.IO_FAILURE, safeMessage(error)));
            }
        }
    }

    private PtyProcessHandle requireRunning() {
        PtyProcessHandle current = process;
        if (lifecycle.get() != Lifecycle.RUNNING || current == null) {
            throw new IllegalStateException("PTY backend is not running");
        }
        return current;
    }

    private void emitStarted() {
        synchronized (eventLock) {
            if (exitReported.get()) {
                return;
            }
            try {
                listener.onStarted();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void emitOutput(byte[] bytes) {
        synchronized (eventLock) {
            if (exitReported.get()) {
                return;
            }
            try {
                listener.onOutput(bytes);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void reportExit(TerminalExit exit) {
        if (!exitReported.compareAndSet(false, true)) {
            return;
        }
        lifecycle.set(Lifecycle.CLOSED);
        PtyProcessHandle current = process;
        process = null;
        if (current != null) {
            closeProcess(current);
        }
        synchronized (eventLock) {
            try {
                listener.onExited(exit);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static void closeProcess(PtyProcessHandle process) {
        try {
            process.close();
        } catch (RuntimeException ignored) {
        }
    }

    private enum Lifecycle {
        NEW,
        STARTING,
        RUNNING,
        CLOSING,
        CLOSED
    }
}
