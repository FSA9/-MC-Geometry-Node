package com.mine.geometry_node.client.ai.mcp.client;

import com.google.gson.JsonObject;
import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.ai.mcp.McpCommandGateway;
import com.mine.geometry_node.client.ai.command.CommandInvocationContext;
import com.mine.geometry_node.client.ai.command.CommandRegistry;
import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ai.command.CommandSpec;
import com.mine.geometry_node.client.ai.graph.editor.BoundGraphTarget;
import com.mine.geometry_node.client.ai.graph.editor.GraphReadSnapshotCache;
import com.mine.geometry_node.client.ai.graph.editor.NodeCatalogQueryTarget;
import com.mine.geometry_node.client.ai.graph.editor.PreparedGraphQueryTarget;
import com.mine.geometry_node.client.ai.graph.editor.ViewportGraphTargetResolver;
import icyllis.modernui.core.Core;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Captures editor state on ModernUI and executes immutable graph reads on a bounded worker pool. */
public final class MinecraftClientMcpGateway implements McpCommandGateway {
    private final CommandRegistry registry;
    private final ViewportGraphTargetResolver targetResolver;
    private final NodeCatalogQueryTarget catalogTarget = new NodeCatalogQueryTarget();
    private final GraphReadSnapshotCache readSnapshots = new GraphReadSnapshotCache();
    private final ThreadPoolExecutor readExecutor = createReadExecutor();
    private final Set<CompletableFuture<CommandResult>> pendingReads = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public MinecraftClientMcpGateway(CommandRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.targetResolver = new ViewportGraphTargetResolver();
    }

    @Override
    public CompletionStage<CommandResult> execute(CommandSpec command, JsonObject arguments,
                                                   CommandInvocationContext.CancellationToken cancellation) {
        CompletableFuture<CommandResult> result = new CompletableFuture<>();
        if (closed.get()) {
            result.complete(closedResult());
            return result;
        }
        Runnable task = () -> {
            try {
                if (closed.get()) {
                    result.complete(closedResult());
                    return;
                }
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
                String sessionId = "";
                if (arguments.has("surface_ref")) {
                    if (!arguments.get("surface_ref").isJsonPrimitive()
                            || !arguments.getAsJsonPrimitive("surface_ref").isString()) {
                        result.complete(CommandResult.failure("ARGUMENT_INVALID", "surface_ref 必须是字符串，例如 V1"));
                        return;
                    }
                    surfaceRef = arguments.get("surface_ref").getAsString();
                }
                if (arguments.has("session_id")) {
                    if (!arguments.get("session_id").isJsonPrimitive()
                            || !arguments.getAsJsonPrimitive("session_id").isString()) {
                        result.complete(CommandResult.failure("ARGUMENT_INVALID", "session_id 必须是字符串"));
                        return;
                    }
                    sessionId = arguments.get("session_id").getAsString();
                }
                ViewportGraphTargetResolver.Resolution resolution = targetResolver.resolve(surfaceRef, sessionId);
                if (!resolution.ok()) {
                    result.complete(resolution.failure());
                    return;
                }
                BoundGraphTarget target = resolution.target();
                if (command.effect() == com.mine.geometry_node.client.ai.protocol.ToolContract.CommandEffect.GRAPH_WRITE) {
                    Thread.ofVirtual().name("geometry-node-graph-patch").start(
                            () -> {
                                if (closed.get()) {
                                    result.complete(closedResult());
                                    return;
                                }
                                CommandResult commandResult = cancellation.isCancelled()
                                        ? CommandResult.failure("CANCELLED", "工具调用已取消")
                                        : executeCommand(command, arguments, cancellation, target);
                                result.complete(closed.get() ? closedResult() : commandResult);
                            });
                    return;
                }
                PreparedGraphQueryTarget readTarget = target.prepareRead(readSnapshots);
                executeRead(command, arguments.deepCopy(), cancellation, target, readTarget, result);
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
        if (!closed.compareAndSet(false, true)) return;
        readExecutor.shutdownNow();
        readSnapshots.close();
        pendingReads.forEach(pending -> pending.complete(closedResult()));
        pendingReads.clear();
        Runnable cleanup = targetResolver::close;
        try {
            if (Core.isOnUiThread()) {
                cleanup.run();
            } else if (!Core.getUiHandlerAsync().post(cleanup)) {
                GeometryNode.LOGGER.warn("Could not enqueue MCP graph target cleanup because the UI queue is unavailable");
            }
        } catch (RuntimeException failure) {
            GeometryNode.LOGGER.warn("Could not enqueue MCP graph target cleanup", failure);
        }
    }

    private CommandResult executeCommand(CommandSpec command, JsonObject arguments,
                                         CommandInvocationContext.CancellationToken cancellation,
                                         CommandInvocationContext.CommandTarget target) {
        try {
            CommandInvocationContext context = new CommandInvocationContext(
                    CommandInvocationContext.CommandOrigin.AGENT, target, cancellation);
            return registry.execute(command, arguments, context);
        } catch (RuntimeException failure) {
            GeometryNode.LOGGER.error("Graph tool failed: command={}", command.name(), failure);
            return CommandResult.failure("COMMAND_INTERNAL_ERROR", "图工具执行失败");
        }
    }

    private void executeRead(CommandSpec command, JsonObject arguments,
                             CommandInvocationContext.CancellationToken cancellation,
                             BoundGraphTarget liveTarget, PreparedGraphQueryTarget readTarget,
                             CompletableFuture<CommandResult> result) {
        pendingReads.add(result);
        result.whenComplete((ignored, failure) -> pendingReads.remove(result));
        try {
            readExecutor.execute(() -> {
                CommandResult commandResult;
                if (closed.get()) {
                    commandResult = closedResult();
                } else if (cancellation.isCancelled()) {
                    commandResult = CommandResult.failure("CANCELLED", "工具调用已取消");
                } else {
                    commandResult = executeCommand(command, arguments, cancellation, readTarget);
                }
                completeReadOnUi(commandResult, cancellation, liveTarget, readTarget.revision(), result);
            });
        } catch (RejectedExecutionException failure) {
            pendingReads.remove(result);
            result.complete(closed.get() ? closedResult()
                    : CommandResult.failure("QUERY_BUSY", "只读图查询队列已满，请稍后重试"));
        }
    }

    private void completeReadOnUi(CommandResult commandResult,
                                  CommandInvocationContext.CancellationToken cancellation,
                                  BoundGraphTarget liveTarget,
                                  long capturedRevision,
                                  CompletableFuture<CommandResult> result) {
        Runnable completion = () -> {
            if (closed.get()) result.complete(closedResult());
            else if (cancellation.isCancelled()) {
                result.complete(CommandResult.failure("CANCELLED", "工具调用已取消"));
            } else if (!liveTarget.isTargetCurrent() || liveTarget.revision() != capturedRevision) {
                result.complete(CommandResult.failure("GRAPH_TARGET_STALE", "目标图已关闭、Scope 已改变或 revision 已过期"));
            } else {
                result.complete(commandResult);
            }
        };
        try {
            if (Core.isOnUiThread()) completion.run();
            else if (!Core.getUiHandlerAsync().post(completion)) {
                result.complete(CommandResult.failure("UI_UNAVAILABLE", "编辑器 UI 队列不可用"));
            }
        } catch (RuntimeException failure) {
            result.complete(CommandResult.failure("UI_UNAVAILABLE", "编辑器 UI 队列不可用"));
        }
    }

    private static ThreadPoolExecutor createReadExecutor() {
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64), runnable -> {
                    Thread thread = new Thread(runnable,
                            "geometry-node-graph-read-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static CommandResult closedResult() {
        return CommandResult.failure("MCP_RUN_CLOSED", "MCP 运行已关闭");
    }
}
