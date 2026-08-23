package com.mine.geometry_node.client.terminal.pty.pty4j;

import com.mine.geometry_node.client.terminal.ProcessLaunchSpec;
import com.mine.geometry_node.client.terminal.pty.PtyProcessFactory;
import com.mine.geometry_node.client.terminal.pty.PtyProcessHandle;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import java.io.IOException;

/** Creates a real PTY and requests ConPTY when running on Windows. */
public final class Pty4jProcessFactory implements PtyProcessFactory {
    @Override
    public PtyProcessHandle start(ProcessLaunchSpec launchSpec) throws IOException {
        boolean windows = isWindows();
        WindowsJobObject job = windows ? WindowsJobObject.create() : null;
        PtyProcessBuilder builder = new PtyProcessBuilder(launchSpec.argv().toArray(String[]::new))
                .setEnvironment(launchSpec.environment())
                .setDirectory(launchSpec.workingDirectory().toString())
                .setInitialColumns(launchSpec.initialSize().columns())
                .setInitialRows(launchSpec.initialSize().rows())
                .setRedirectErrorStream(true)
                .setWindowsAnsiColorEnabled(true)
                .setUseWinConPty(windows)
                .setConPtyInheritCursor(false);
        if (job != null) builder.setWindowsSuspendedProcessCallback(job::assign);
        PtyProcess process = null;
        try {
            process = builder.start();
            if (job != null) job.requireAssigned();
            return new Pty4jProcessHandle(process, job);
        } catch (IOException | RuntimeException error) {
            if (job != null) job.close();
            if (process != null) forceClose(process);
            throw error;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    }

    private static void forceClose(PtyProcess process) {
        try { process.getOutputStream().close(); } catch (IOException | RuntimeException ignored) { }
        try { process.getInputStream().close(); } catch (IOException | RuntimeException ignored) { }
        try { process.destroyForcibly(); } catch (RuntimeException ignored) { }
    }
}
