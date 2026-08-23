package com.mine.geometry_node.client.terminal.pty;

import com.mine.geometry_node.client.terminal.ProcessLaunchSpec;

import java.io.IOException;

@FunctionalInterface
public interface PtyProcessFactory {
    /**
     * Creates and immediately publishes the handle. Implementations must not block after creating
     * an OS process while withholding its handle; startup cancellation cannot clean up such a process.
     */
    PtyProcessHandle start(ProcessLaunchSpec launchSpec) throws IOException;
}
