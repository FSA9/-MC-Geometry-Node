package com.mine.geometry_node.client.integration.terminal;

import com.mine.geometry_node.client.ai.mcp.McpToolEventListener;
import com.mine.geometry_node.client.terminal.ProcessLaunchSpec;
import com.mine.geometry_node.client.terminal.TerminalMode;
import com.mine.geometry_node.client.terminal.TerminalSession;
import com.mine.geometry_node.client.terminal.TerminalSize;
import com.mine.geometry_node.client.terminal.pty.PtyProcessFactory;
import com.mine.geometry_node.client.terminal.shell.ShellTerminalCoordinator;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Owns a tab's process and control-plane resources, including asynchronous startup cancellation. */
public final class TerminalRuntime implements AutoCloseable {
    private final Object stateLock = new Object();
    // Serializes launches so an obsolete launch can never stop a newer process.
    private final Object launchLock = new Object();
    private final TerminalSession session;
    private final ShellTerminalCoordinator coordinator;
    private final RunFactory runFactory;
    private final Executor executor;
    private Attempt currentAttempt;
    private int controlOperations;
    private boolean closed;

    @FunctionalInterface
    public interface RunFactory {
        PreparedRun prepare(TerminalSize size, McpToolEventListener events) throws Exception;
    }

    /** Ownership transfers to the process backend once startup succeeds. */
    public static final class PreparedRun implements AutoCloseable {
        private final ProcessLaunchSpec launchSpec;
        private final AutoCloseable resource;
        private final AtomicBoolean closed = new AtomicBoolean();

        public PreparedRun(ProcessLaunchSpec launchSpec, AutoCloseable resource) {
            this.launchSpec = Objects.requireNonNull(launchSpec, "launchSpec");
            this.resource = Objects.requireNonNull(resource, "resource");
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            try {
                resource.close();
            } catch (Exception ignored) {
            }
        }
    }

    public TerminalRuntime(UUID id, String title, TerminalMode mode, String profileId,
                           PtyProcessFactory processes, RunFactory runs, Executor executor) {
        coordinator = new ShellTerminalCoordinator(new TerminalSize(80, 24), processes);
        session = new TerminalSession(id, title, mode, profileId, coordinator);
        coordinator.bind(session);
        runFactory = Objects.requireNonNull(runs, "runs");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public TerminalSession session() { return session; }
    public ShellTerminalCoordinator coordinator() { return coordinator; }

    public boolean isStartPending() {
        synchronized (stateLock) {
            return currentAttempt != null && currentAttempt.starting;
        }
    }

    public void start(TerminalSize size, McpToolEventListener events, Consumer<String> errors) {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(errors, "errors");
        Attempt attempt;
        synchronized (stateLock) {
            if (closed || controlOperations != 0 || session.state().hasActiveBackend() || isStartPending()) return;
            attempt = new Attempt();
            currentAttempt = attempt;
        }
        try {
            executor.execute(() -> launch(attempt, size, events, errors));
        } catch (RuntimeException failure) {
            reportFailure(attempt, failure, errors);
        }
    }

    public void stop() {
        PreparedRun cancelled;
        synchronized (stateLock) {
            controlOperations++;
            cancelled = cancelAttempt();
        }
        try {
            if (cancelled != null) cancelled.close();
            coordinator.stop();
        } finally {
            synchronized (stateLock) {
                controlOperations--;
            }
        }
    }

    @Override
    public void close() {
        PreparedRun cancelled;
        synchronized (stateLock) {
            if (closed) return;
            closed = true;
            cancelled = cancelAttempt();
        }
        if (cancelled != null) cancelled.close();
        coordinator.dispose();
        session.close();
    }

    private void launch(Attempt attempt, TerminalSize size, McpToolEventListener events,
                        Consumer<String> errors) {
        synchronized (launchLock) {
            if (!accepts(attempt)) return;
            PreparedRun run = null;
            try {
                run = runFactory.prepare(size, event -> {
                    if (accepts(attempt)) events.onToolEvent(event);
                });
                boolean accepted;
                synchronized (stateLock) {
                    accepted = accepts(attempt);
                    if (accepted) attempt.run = run;
                }
                if (!accepted) {
                    run.close();
                    return;
                }
                // Native startup must not hold stateLock: Stop/Close must also work during launch.
                coordinator.startManaged(run.launchSpec, run);
                synchronized (stateLock) {
                    accepted = accepts(attempt);
                    if (accepted) attempt.starting = false;
                }
                if (!accepted) coordinator.stop();
            } catch (Exception failure) {
                if (run != null) run.close();
                reportFailure(attempt, failure, errors);
            }
        }
    }

    private boolean accepts(Attempt attempt) {
        synchronized (stateLock) {
            return !closed && currentAttempt == attempt;
        }
    }

    private PreparedRun cancelAttempt() {
        Attempt cancelled = currentAttempt;
        currentAttempt = null;
        return cancelled == null ? null : cancelled.run;
    }

    private void reportFailure(Attempt attempt, Exception failure, Consumer<String> errors) {
        synchronized (stateLock) {
            if (!accepts(attempt)) return;
            attempt.starting = false;
        }
        String message = failure.getMessage();
        if (accepts(attempt)) {
            errors.accept(message == null || message.isBlank() ? failure.getClass().getSimpleName() : message);
        }
    }

    private static final class Attempt {
        private boolean starting = true;
        private PreparedRun run;
    }
}
