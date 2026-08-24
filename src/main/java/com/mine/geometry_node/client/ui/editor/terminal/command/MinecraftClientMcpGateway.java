package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.google.gson.JsonObject;
import com.mine.geometry_node.client.agent.mcp.McpCommandGateway;
import com.mine.geometry_node.client.ai.command.CommandInvocationContext;
import com.mine.geometry_node.client.ai.command.CommandRegistry;
import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ai.command.CommandSpec;
import net.minecraft.client.Minecraft;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Marshals every graph read onto the Minecraft client thread. */
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
        CompletableFuture<CommandResult> result = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                CommandInvocationContext context = new CommandInvocationContext(
                        CommandInvocationContext.CommandOrigin.AGENT, target, cancellation);
                result.complete(registry.execute(command, arguments, context));
            } catch (RuntimeException failure) {
                result.complete(CommandResult.failure("COMMAND_INTERNAL_ERROR", "只读图查询执行失败"));
            }
        };
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) task.run();
        else minecraft.execute(task);
        return result;
    }
}
