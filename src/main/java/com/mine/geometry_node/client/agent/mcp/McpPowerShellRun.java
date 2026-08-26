package com.mine.geometry_node.client.agent.mcp;

import com.mine.geometry_node.client.ai.command.CommandRegistry;
import com.mine.geometry_node.client.terminal.ProcessLaunchSpec;
import com.mine.geometry_node.client.terminal.TerminalSize;
import com.mine.geometry_node.client.terminal.shell.PowerShellProfile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one MCP registration and its trusted audit for the lifetime of a PowerShell process. */
public final class McpPowerShellRun implements AutoCloseable {
    private final McpHttpServer mcp;
    private final ProcessLaunchSpec launchSpec;
    private final McpRunAudit audit;
    private final AtomicBoolean closed = new AtomicBoolean();

    private McpPowerShellRun(McpHttpServer mcp, ProcessLaunchSpec launchSpec, McpRunAudit audit) {
        this.mcp = mcp;
        this.launchSpec = launchSpec;
        this.audit = audit;
    }

    public static McpPowerShellRun start(CommandRegistry registry, McpCommandGateway gateway,
                                         McpToolEventListener eventListener, TerminalSize size) throws IOException {
        Objects.requireNonNull(registry, "registry");
        McpRunAudit audit = new McpRunAudit();
        McpToolEventListener trustedListener = event -> {
            audit.onToolEvent(event);
            if (eventListener != null) eventListener.onToolEvent(event);
        };
        McpRequestDispatcher dispatcher = new McpRequestDispatcher(
                new McpToolCatalog(registry), gateway, trustedListener);
        McpHttpServer mcp = null;
        try {
            mcp = McpHttpServer.start(dispatcher);
            ProcessLaunchSpec launch = PowerShellProfile.create(size, Map.of(
                    McpHttpServer.URL_ENVIRONMENT, mcp.endpoint().toString(),
                    McpHttpServer.TOKEN_ENVIRONMENT, mcp.bearerToken()));
            return new McpPowerShellRun(mcp, launch, audit);
        } catch (IOException | RuntimeException failure) {
            if (mcp != null) mcp.close();
            else dispatcher.close();
            throw failure;
        }
    }

    public ProcessLaunchSpec launchSpec() {
        return launchSpec;
    }

    public List<McpToolEvent> auditSnapshot() {
        return audit.snapshot();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            mcp.close();
            audit.close();
        }
    }
}
