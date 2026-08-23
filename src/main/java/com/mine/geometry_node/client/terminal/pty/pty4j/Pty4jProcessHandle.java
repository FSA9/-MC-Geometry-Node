package com.mine.geometry_node.client.terminal.pty.pty4j;

import com.mine.geometry_node.client.terminal.TerminalSize;
import com.mine.geometry_node.client.terminal.pty.PtyProcessHandle;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

final class Pty4jProcessHandle implements PtyProcessHandle {
    private final PtyProcess process;
    private final WindowsJobObject job;
    private final InputStream stdout;
    private final OutputStream stdin;
    private final AtomicBoolean closed = new AtomicBoolean();

    Pty4jProcessHandle(PtyProcess process, WindowsJobObject job) {
        this.process = process;
        this.job = job;
        stdout = process.getInputStream();
        stdin = process.getOutputStream();
    }

    @Override public InputStream stdout() { return stdout; }
    @Override public OutputStream stdin() { return stdin; }

    @Override
    public CompletionStage<Integer> exitCode() {
        return process.onExit().thenApply(Process::exitValue);
    }

    @Override
    public void resize(TerminalSize size) {
        process.setWinSize(new WinSize(size.columns(), size.rows()));
    }

    @Override
    public void interrupt() throws IOException {
        synchronized (stdin) {
            stdin.write(3);
            stdin.flush();
        }
    }

    @Override
    public void terminate() {
        try {
            interrupt();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            closeQuietly(stdin);
            if (job != null) job.close();
            destroyDescendants();
        } finally {
            closeQuietly(stdout);
            destroyMainProcess();
        }
    }

    private void destroyDescendants() {
        try {
            List<ProcessHandle> descendants = process.descendants()
                    .sorted(Comparator.comparingInt(Pty4jProcessHandle::depth).reversed())
                    .toList();
            for (ProcessHandle child : descendants) {
                try { child.destroyForcibly(); } catch (RuntimeException ignored) { }
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void destroyMainProcess() {
        try {
            if (!process.isAlive()) return;
            if (job != null) process.toHandle().destroyForcibly();
            else process.destroyForcibly();
        } catch (RuntimeException ignored) {
        }
    }

    private static int depth(ProcessHandle handle) {
        int depth = 0;
        ProcessHandle current = handle;
        while ((current = current.parent().orElse(null)) != null && depth < 128) {
            depth++;
        }
        return depth;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
