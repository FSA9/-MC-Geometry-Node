package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.google.gson.JsonObject;
import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.ai.mcp.McpCommandGateway;
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
    private final ViewportGraphTargetResolver targetResolver;
    private final NodeCatalogQueryTarget catalogTarget = new NodeCatalogQueryTarget();

    public MinecraftClientMcpGateway(CommandRegistry registry, GraphPatchApprovalPresenter approvals) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.targetResolver = new ViewportGraphTargetResolver(Objects.requireNonNull(approvals, "approvals"));
    }

    @Override
    public CompletionStage<CommandResult> execute(CommandSpec command, JsonObject arguments,
                                                   CommandInvocationContext.CancellationToken cancellation) {
        CompletableFuture<CommandResult> result = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                if (cancellation.isCancelled()) {
                    result.complete(CommandResult.failure("CANCELLED", "工具调用已取消"));
                    return;
                }
                if (!command.requiresGraph()) {
                    CommandInvocationContext context = new CommandInvocationContext(
                            CommandInvocationContext.CommandOrigin.AGENT, catalogTarget, cancellation);
                    result.complete(registry.execute(command, arguments, context));
                    return;
                }
                String surfaceRef = "";
                if (arguments.has("surface_ref")) {
                    if (!arguments.get("surface_ref").isJsonPrimitive()
                            || !arguments.getAsJsonPrimitive("surface_ref").isString()) {
                        result.complete(CommandResult.failure("ARGUMENT_INVALID", "surface_ref 必须是字符串，例如 V1"));
                        return;
                    }
                    surfaceRef = arguments.get("surface_ref").getAsString();
                }
                ViewportGraphTargetResolver.Resolution resolution = targetResolver.resolve(surfaceRef);
                if (!resolution.ok()) {
                    result.complete(resolution.failure());
                    return;
                }
                BoundGraphQueryTarget target = resolution.target();
                if (command.effect() == com.mine.geometry_node.client.ai.protocol.ToolContract.CommandEffect.GRAPH_WRITE) {
                    Thread.ofVirtual().name("geometry-node-graph-patch").start(
                            () -> result.complete(cancellation.isCancelled()
                                    ? CommandResult.failure("CANCELLED", "工具调用已取消")
                                    : executeCommand(command, arguments, cancellation, target)));
                    return;
                }
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

    @Override
    public void close() {
        targetResolver.close();
    }

    private CommandResult executeCommand(CommandSpec command, JsonObject arguments,
                                         CommandInvocationContext.CancellationToken cancellation,
                                         BoundGraphQueryTarget target) {
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
