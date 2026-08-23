package com.mine.geometry_node.client.terminal;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Thread-safe owner of one terminal tab's backend and run lifecycle. */
public final class TerminalSession implements AutoCloseable {
    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_PROFILE_ID_LENGTH = 128;

    private final Object lock = new Object();
    private final Object eventLock = new Object();
    private final UUID id;
    private final TerminalSessionListener listener;

    private String title;
    private String profileId;
    private TerminalMode mode;
    private TerminalRunState state = TerminalRunState.IDLE;
    private TerminalBackend backend;
    private UUID runId;
    private long controlRevision;
    private long runGeneration;
    private long eventSequence;
    private long lastDeliveredSequence = -1;

    public TerminalSession(String title) {
        this(UUID.randomUUID(), title, TerminalMode.COMMAND, "", TerminalSessionListener.NOOP);
    }

    public TerminalSession(
            UUID id,
            String title,
            TerminalMode mode,
            String profileId,
            TerminalSessionListener listener) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = requireText(title, "title", MAX_TITLE_LENGTH);
        this.mode = Objects.requireNonNull(mode, "mode");
        this.profileId = normalizeProfileId(profileId);
        this.listener = listener == null ? TerminalSessionListener.NOOP : listener;
    }

    public UUID id() {
        return id;
    }

    public String title() {
        synchronized (lock) {
            return title;
        }
    }

    public void setTitle(String title) {
        synchronized (lock) {
            requireNotDisposed();
            this.title = requireText(title, "title", MAX_TITLE_LENGTH);
        }
    }

    public TerminalMode mode() {
        synchronized (lock) {
            return mode;
        }
    }

    public void setMode(TerminalMode mode) {
        Objects.requireNonNull(mode, "mode");
        synchronized (lock) {
            requireConfigurable();
            this.mode = mode;
        }
    }

    public String profileId() {
        synchronized (lock) {
            return profileId;
        }
    }

    public void setProfileId(String profileId) {
        synchronized (lock) {
            requireConfigurable();
            this.profileId = normalizeProfileId(profileId);
        }
    }

    public TerminalRunState state() {
        synchronized (lock) {
            return state;
        }
    }

    public UUID start(TerminalBackend newBackend) throws IOException {
        Objects.requireNonNull(newBackend, "newBackend");
        UUID newRunId;
        long startRevision;
        long startGeneration;
        long startingEvent;
        synchronized (lock) {
            requireNotDisposed();
            if (mode == TerminalMode.COMMAND) {
                throw new IllegalStateException("COMMAND mode does not use a process backend");
            }
            if (state.hasActiveBackend()) {
                throw new IllegalStateException("Terminal backend is already active");
            }
            backend = newBackend;
            runId = UUID.randomUUID();
            newRunId = runId;
            startRevision = controlRevision;
            startGeneration = ++runGeneration;
            state = TerminalRunState.STARTING;
            startingEvent = ++eventSequence;
        }
        dispatchState(startingEvent, TerminalRunState.STARTING);

        try {
            newBackend.start(new SessionBackendListener(newBackend, newRunId));
            boolean cancelled;
            synchronized (lock) {
                cancelled = controlRevision != startRevision || runGeneration != startGeneration;
            }
            if (cancelled) {
                closeQuietly(newBackend);
                throw new IOException("Terminal session was stopped while the backend was starting");
            }
            return newRunId;
        } catch (IOException | RuntimeException error) {
            handleStartFailure(newBackend, newRunId, error);
            throw error;
        }
    }

    public void write(byte[] input) throws IOException {
        Objects.requireNonNull(input, "input");
        TerminalBackend current;
        synchronized (lock) {
            if (!state.acceptsInput() || backend == null) {
                throw new IllegalStateException("Terminal backend is not accepting input");
            }
            current = backend;
        }
        current.write(input.clone());
    }

    public void resize(TerminalSize size) throws IOException {
        Objects.requireNonNull(size, "size");
        TerminalBackend current;
        synchronized (lock) {
            requireNotDisposed();
            if (!state.hasActiveBackend() || backend == null) {
                return;
            }
            current = backend;
        }
        current.resize(size);
    }

    public void interrupt() throws IOException {
        TerminalBackend current;
        synchronized (lock) {
            if (state != TerminalRunState.RUNNING || backend == null) {
                return;
            }
            current = backend;
        }
        current.interrupt();
    }

    public void stop() {
        TerminalBackend current;
        UUID currentRunId;
        long stoppingEvent;
        synchronized (lock) {
            if (!state.hasActiveBackend() || backend == null) {
                return;
            }
            state = TerminalRunState.STOPPING;
            controlRevision++;
            current = backend;
            currentRunId = runId;
            stoppingEvent = ++eventSequence;
        }
        dispatchState(stoppingEvent, TerminalRunState.STOPPING);
        closeQuietly(current);
        handleExit(current, currentRunId, TerminalExit.stopped());
    }

    @Override
    public void close() {
        TerminalBackend current;
        long disposedEvent;
        synchronized (lock) {
            if (state == TerminalRunState.DISPOSED) {
                return;
            }
            current = backend;
            backend = null;
            runId = null;
            controlRevision++;
            state = TerminalRunState.DISPOSED;
            disposedEvent = ++eventSequence;
        }
        if (current != null) {
            closeQuietly(current);
        }
        dispatchState(disposedEvent, TerminalRunState.DISPOSED);
    }

    private void handleStarted(TerminalBackend source, UUID sourceRunId) {
        long runningEvent;
        synchronized (lock) {
            if (backend != source || !Objects.equals(runId, sourceRunId)
                    || state != TerminalRunState.STARTING) {
                return;
            }
            state = TerminalRunState.RUNNING;
            runningEvent = ++eventSequence;
        }
        dispatchState(runningEvent, TerminalRunState.RUNNING);
    }

    private void handleOutput(TerminalBackend source, UUID sourceRunId, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        long outputEvent;
        synchronized (lock) {
            if (backend != source || !Objects.equals(runId, sourceRunId)
                    || state == TerminalRunState.DISPOSED) {
                return;
            }
            outputEvent = eventSequence;
        }
        dispatchOutput(outputEvent, bytes.clone());
    }

    private void handleExit(TerminalBackend source, UUID sourceRunId, TerminalExit exit) {
        Objects.requireNonNull(exit, "exit");
        TerminalRunState terminalState;
        long exitEvent;
        synchronized (lock) {
            if (backend != source || !Objects.equals(runId, sourceRunId)
                    || state == TerminalRunState.DISPOSED) {
                return;
            }
            backend = null;
            runId = null;
            terminalState = exit.failed() ? TerminalRunState.FAILED : TerminalRunState.EXITED;
            state = terminalState;
            exitEvent = ++eventSequence;
        }
        dispatchExit(exitEvent, exit, terminalState);
    }

    private void handleStartFailure(TerminalBackend source, UUID sourceRunId, Throwable error) {
        TerminalExit exit = new TerminalExit(null, TerminalExit.Reason.START_FAILED, safeMessage(error));
        boolean ownedRun;
        long failureEvent = -1;
        synchronized (lock) {
            ownedRun = backend == source && Objects.equals(runId, sourceRunId)
                    && state != TerminalRunState.DISPOSED;
            if (ownedRun) {
                backend = null;
                runId = null;
                state = TerminalRunState.FAILED;
                failureEvent = ++eventSequence;
            }
        }
        closeQuietly(source);
        if (ownedRun) {
            dispatchExit(failureEvent, exit, TerminalRunState.FAILED);
        }
    }

    private void requireConfigurable() {
        requireNotDisposed();
        if (state.hasActiveBackend()) {
            throw new IllegalStateException("Stop the active backend before changing terminal configuration");
        }
    }

    private void requireNotDisposed() {
        if (state == TerminalRunState.DISPOSED) {
            throw new IllegalStateException("Terminal session is disposed");
        }
    }

    private static String normalizeProfileId(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return "";
        }
        return requireText(profileId, "profileId", MAX_PROFILE_ID_LENGTH);
    }

    private static String requireText(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1 to " + maxLength + " characters");
        }
        return normalized;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static void closeQuietly(TerminalBackend backend) {
        try {
            backend.close();
        } catch (RuntimeException ignored) {
        }
    }

    private void dispatchState(long sequence, TerminalRunState newState) {
        synchronized (eventLock) {
            if (sequence < lastDeliveredSequence) {
                return;
            }
            lastDeliveredSequence = sequence;
            try {
                listener.onStateChanged(newState);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void dispatchOutput(long sequence, byte[] bytes) {
        synchronized (eventLock) {
            if (sequence < lastDeliveredSequence) {
                return;
            }
            lastDeliveredSequence = sequence;
            try {
                listener.onOutput(bytes);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void dispatchExit(long sequence, TerminalExit exit, TerminalRunState terminalState) {
        synchronized (eventLock) {
            if (sequence < lastDeliveredSequence) {
                return;
            }
            lastDeliveredSequence = sequence;
            try {
                listener.onExited(exit);
            } catch (RuntimeException ignored) {
            }
            if (sequence < lastDeliveredSequence) {
                return;
            }
            try {
                listener.onStateChanged(terminalState);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private final class SessionBackendListener implements TerminalBackendListener {
        private final TerminalBackend source;
        private final UUID sourceRunId;

        private SessionBackendListener(TerminalBackend source, UUID sourceRunId) {
            this.source = source;
            this.sourceRunId = sourceRunId;
        }

        @Override
        public void onStarted() {
            handleStarted(source, sourceRunId);
        }

        @Override
        public void onOutput(byte[] bytes) {
            handleOutput(source, sourceRunId, bytes);
        }

        @Override
        public void onExited(TerminalExit exit) {
            handleExit(source, sourceRunId, exit);
        }
    }
}
