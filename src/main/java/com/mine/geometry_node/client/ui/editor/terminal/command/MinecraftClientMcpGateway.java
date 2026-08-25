package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.google.gson.JsonObject;
import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.agent.mcp.McpCommandGateway;
import com.mine.geometry_node.client.ai.command.CommandInvocationContext;
import com.mine.geometry_node.client.ai.command.CommandRegistry;
import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ai.command.CommandSpec;
import icyllis.modernui.core.Core;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Marshals graph access onto the ModernUI thread that owns the editor model and views. */
public final class MinecraftClientMcpGateway implements McpCommandGateway {
    private final CommandRegistry registry;
    private final BoundGraphQueryTarget target;

    public MinecraftClientMcpGateway(CommandRegistry registry, BoundGraphQueryTarget target) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.target = Objects.requireNonNull(target, "target");
    }

    @Override
    public CompletionStage<CommandResult> execute(CommandSpec command, JsonObject arguments,
                                                   CommandInvocationContext.CancellationToken cancellation) {
        if (command.effect() == com.mine.geometry_node.client.ai.protocol.ToolContract.CommandEffect.GRAPH_WRITE) {
            return CompletableFuture.supplyAsync(() -> executeCommand(command, arguments, cancellation),
                    runnable -> Thread.ofVirtual().name("geometry-node-graph-patch").start(runnable));
        }
        CompletableFuture<CommandResult> result = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                CommandInvocationContext context = new CommandInvocationContext(
                        CommandInvocationContext.CommandOrigin.AGENT, target, cancellation);
                result.complete(registry.execute(command, arguments, context));
            } catch (RuntimeException failure) {
                GeometryNode.LOGGER.error("Read-only graph tool failed: command={}", command.name(), failure);
                result.complete(CommandResult.failure("COMMAND_INTERNAL_ERROR", "只读图查询执行失败"));
            }
        };
        try {
            if (Core.isOnUiThread()) {
                task.run();
            } else if (!Core.getUiHandlerAsync().post(task)) {
                result.complete(CommandResult.failure("UI_UNAVAILABLE", "编辑器 UI 队列不可用"));
            }
        } catch (RuntimeException failure) {
            result.complete(CommandResult.failure("UI_UNAVAILABLE", "编辑器 UI 队列不可用"));
        }
        return result;
    }

    private CommandResult executeCommand(CommandSpec command, JsonObject arguments,
                                         CommandInvocationContext.CancellationToken cancellation) {
        try {
            CommandInvocationContext context = new CommandInvocationContext(
                    CommandInvocationContext.CommandOrigin.AGENT, target, cancellation);
            return registry.execute(command, arguments, context);
        } catch (RuntimeException failure) {
            GeometryNode.LOGGER.error("Graph tool failed: command={}", command.name(), failure);
            return CommandResult.failure("COMMAND_INTERNAL_ERROR", "图工具执行失败");
        }
    }
}
