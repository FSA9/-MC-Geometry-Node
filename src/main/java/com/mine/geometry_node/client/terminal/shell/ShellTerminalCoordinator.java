package com.mine.geometry_node.client.terminal.shell;

import com.mine.geometry_node.client.terminal.ProcessLaunchSpec;
import com.mine.geometry_node.client.terminal.ResourceOwnedTerminalBackend;
import com.mine.geometry_node.client.terminal.TerminalExit;
import com.mine.geometry_node.client.terminal.TerminalRunState;
import com.mine.geometry_node.client.terminal.TerminalSession;
import com.mine.geometry_node.client.terminal.TerminalSessionListener;
import com.mine.geometry_node.client.terminal.TerminalSize;
import com.mine.geometry_node.client.terminal.emulator.TerminalSnapshot;
import com.mine.geometry_node.client.terminal.emulator.VtTerminalEmulator;
import com.mine.geometry_node.client.terminal.input.TerminalInputEncoder;
import com.mine.geometry_node.client.terminal.input.TerminalKey;
import com.mine.geometry_node.client.terminal.pty.PtyProcessFactory;
import com.mine.geometry_node.client.terminal.pty.PtyTerminalBackend;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Bridges a Session to the VT model without exposing PTY text as a control protocol. */
public final class ShellTerminalCoordinator implements TerminalSessionListener {
    private static final int MAX_PENDING_INPUT_BYTES = TerminalInputEncoder.MAX_PASTE_BYTES + 12;
    private static final int MAX_PENDING_INPUT_ITEMS = 4_096;

    private final VtTerminalEmulator emulator;
    private final PtyProcessFactory processFactory;
    private final Object resizeLock = new Object();
    private final Object nativeOperationLock = new Object();
    private final Object inputQueueLock = new Object();
    private final AtomicReference<PendingResize> pendingNativeResize = new AtomicReference<>();
    private final AtomicBoolean resizeWorkerRunning = new AtomicBoolean();
    private final Deque<PendingInput> pendingInput = new ArrayDeque<>();
    private final AtomicBoolean inputWorkerRunning = new AtomicBoolean();
    private final AtomicLong runEpoch = new AtomicLong();
    private volatile TerminalSession session;
    private volatile ShellTerminalObserver observer = ShellTerminalObserver.NOOP;
    private TerminalSize currentSize;
    private int pendingInputBytes;

    public ShellTerminalCoordinator(TerminalSize initialSize, PtyProcessFactory processFactory) {
        emulator = new VtTerminalEmulator(initialSize);
        this.processFactory = Objects.requireNonNull(processFactory, "processFactory");
        currentSize = initialSize;
    }

    public void bind(TerminalSession session) {
        if (this.session != null) throw new IllegalStateException("Coordinator is already bound");
        this.session = Objects.requireNonNull(session, "session");
    }

    public void setObserver(ShellTerminalObserver observer) {
        this.observer = observer == null ? ShellTerminalObserver.NOOP : observer;
    }

    public TerminalSnapshot snapshot() { return emulator.snapshot(); }

    public long screenRevision() { return emulator.revision(); }

    public void start(PtyProcessFactory factory, ProcessLaunchSpec spec) throws IOException {
        startBackend(new PtyTerminalBackend(factory, spec));
    }

    public void startManaged(ProcessLaunchSpec spec, AutoCloseable runResource) throws IOException {
        Objects.requireNonNull(runResource, "runResource");
        startBackend(new ResourceOwnedTerminalBackend(new PtyTerminalBackend(processFactory, spec), runResource));
    }

    private void startBackend(com.mine.geometry_node.client.terminal.TerminalBackend backend) throws IOException {
        long newRunEpoch;
        synchronized (nativeOperationLock) {
            TerminalSession targetSession = requireSession();
            if (targetSession.state().hasActiveBackend()) {
                throw new IllegalStateException("Terminal backend is already active");
            }
            newRunEpoch = runEpoch.incrementAndGet();
            pendingNativeResize.set(null);
            synchronized (inputQueueLock) {
                pendingInput.clear();
                pendingInputBytes = 0;
            }
            emulator.reset();
            observer.onScreenChanged();
            try {
                targetSession.start(backend);
            } catch (IOException | RuntimeException failure) {
                backend.close();
                throw failure;
            }
            synchronized (resizeLock) {
                pendingNativeResize.set(new PendingResize(newRunEpoch, currentSize));
            }
        }
        scheduleNativeResize();
    }

    public void sendText(String text) { write(TerminalInputEncoder.text(text)); }

    public void sendKey(TerminalKey key) {
        write(TerminalInputEncoder.key(key, emulator.applicationCursorKeys()));
    }

    public void sendControl(char character) { write(TerminalInputEncoder.control(character)); }

    public boolean sendMouseWheel(boolean up, int column, int row) {
        if (!emulator.mouseTracking()) return false;
        write(TerminalInputEncoder.mouseWheel(up, column, row, emulator.sgrMouseMode()));
        return true;
    }

    public void paste(String text) {
        try {
            write(TerminalInputEncoder.paste(text, emulator.bracketedPaste()));
        } catch (IllegalArgumentException error) {
            observer.onError(safeMessage(error));
        }
    }

    public void resize(TerminalSize size) {
        synchronized (resizeLock) {
            if (size.equals(currentSize)) return;
            currentSize = size;
            pendingNativeResize.set(new PendingResize(runEpoch.get(), size));
        }
        emulator.resize(size);
        observer.onScreenChanged();
        scheduleNativeResize();
    }

    public void interrupt() {
        enqueueInput(PendingInput.interrupt(runEpoch.get()));
    }

    public void stop() {
        cancelPendingOperations();
        requireSession().stop();
    }

    public void dispose() {
        cancelPendingOperations();
        observer = ShellTerminalObserver.NOOP;
    }

    @Override
    public void onOutput(byte[] bytes) {
        emulator.accept(bytes);
        byte[] replies = emulator.drainReplies();
        if (replies.length > 0) write(replies);
        observer.onScreenChanged();
    }

    @Override public void onStateChanged(TerminalRunState state) { observer.onStateChanged(state); }
    @Override public void onExited(TerminalExit exit) { observer.onExited(exit); }

    private void write(byte[] bytes) {
        if (bytes.length == 0) return;
        enqueueInput(PendingInput.bytes(runEpoch.get(), bytes));
    }

    private void enqueueInput(PendingInput input) {
        boolean rejected;
        synchronized (inputQueueLock) {
            PendingInput last = pendingInput.peekLast();
            if (input.interrupt() && last != null && last.interrupt() && last.runEpoch() == input.runEpoch()) return;
            rejected = pendingInput.size() >= MAX_PENDING_INPUT_ITEMS
                    || input.byteCount() > MAX_PENDING_INPUT_BYTES - pendingInputBytes;
            if (!rejected) {
                pendingInput.addLast(input);
                pendingInputBytes += input.byteCount();
            }
        }
        if (rejected) {
            observer.onError("Terminal input queue is full");
            return;
        }
        scheduleInputWorker();
    }

    private TerminalSession requireSession() {
        TerminalSession value = session;
        if (value == null) throw new IllegalStateException("Coordinator is not bound to a Session");
        return value;
    }

    private void scheduleNativeResize() {
        if (!resizeWorkerRunning.compareAndSet(false, true)) return;
        Thread.ofVirtual().name("geometry-node-pty-resize").start(this::drainNativeResize);
    }

    private void scheduleInputWorker() {
        if (!inputWorkerRunning.compareAndSet(false, true)) return;
        Thread.ofVirtual().name("geometry-node-pty-input").start(this::drainInput);
    }

    private void drainInput() {
        try {
            PendingInput input;
            while ((input = pollInput()) != null) {
                synchronized (nativeOperationLock) {
                    if (input.runEpoch() != runEpoch.get()) continue;
                    try {
                        if (input.interrupt()) requireSession().interrupt();
                        else requireSession().write(input.bytes());
                    } catch (IOException | RuntimeException error) {
                        if (input.runEpoch() == runEpoch.get()) observer.onError(safeMessage(error));
                    }
                }
            }
        } finally {
            inputWorkerRunning.set(false);
            synchronized (inputQueueLock) {
                if (!pendingInput.isEmpty()) scheduleInputWorker();
            }
        }
    }

    private PendingInput pollInput() {
        synchronized (inputQueueLock) {
            PendingInput input = pendingInput.pollFirst();
            if (input != null) pendingInputBytes -= input.byteCount();
            return input;
        }
    }

    private void drainNativeResize() {
        try {
            PendingResize resize;
            while ((resize = pendingNativeResize.getAndSet(null)) != null) {
                synchronized (nativeOperationLock) {
                    if (resize.runEpoch() != runEpoch.get()) continue;
                    try {
                        requireSession().resize(resize.size());
                    } catch (IOException | RuntimeException error) {
                        if (resize.runEpoch() == runEpoch.get()) observer.onError(safeMessage(error));
                    }
                }
            }
        } finally {
            resizeWorkerRunning.set(false);
            if (pendingNativeResize.get() != null) scheduleNativeResize();
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private void cancelPendingOperations() {
        runEpoch.incrementAndGet();
        pendingNativeResize.set(null);
        synchronized (inputQueueLock) {
            pendingInput.clear();
            pendingInputBytes = 0;
        }
    }

    private record PendingResize(long runEpoch, TerminalSize size) { }

    private record PendingInput(long runEpoch, byte[] bytes, boolean interrupt) {
        private PendingInput {
            bytes = bytes.clone();
        }

        private static PendingInput bytes(long runEpoch, byte[] bytes) {
            return new PendingInput(runEpoch, bytes, false);
        }

        private static PendingInput interrupt(long runEpoch) {
            return new PendingInput(runEpoch, new byte[0], true);
        }

        private int byteCount() { return bytes.length; }
    }
}
