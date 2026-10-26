package com.mine.geometry_node.client.integration.terminal;

import com.mine.geometry_node.client.feature.ai.command.CommandCatalog;
import com.mine.geometry_node.client.feature.ai.mcp.McpPowerShellRun;
import com.mine.geometry_node.client.integration.ai.mcp.MinecraftClientMcpGateway;
import com.mine.geometry_node.client.feature.terminal.TerminalMode;
import com.mine.geometry_node.client.feature.terminal.pty.pty4j.Pty4jProcessFactory;

import java.util.UUID;

/** Production composition of PowerShell, PTY transport and the editor MCP gateway. */
public final class McpTerminalRuntimeFactory implements TerminalRuntimeFactory {
    @Override
    public TerminalRuntime create(UUID id, String title, TerminalMode mode, String profileId) {
        return new TerminalRuntime(id, title, mode, profileId, new Pty4jProcessFactory(), (size, events) -> {
            var registry = CommandCatalog.registry();
            var gateway = new MinecraftClientMcpGateway(registry);
            try {
                var run = McpPowerShellRun.start(registry, gateway, events, size);
                return new TerminalRuntime.PreparedRun(run.launchSpec(), run);
            } catch (Exception failure) {
                gateway.close();
                throw failure;
            }
        }, task -> Thread.ofVirtual().name("geometry-node-mcp-powershell-start").start(task));
    }
}
