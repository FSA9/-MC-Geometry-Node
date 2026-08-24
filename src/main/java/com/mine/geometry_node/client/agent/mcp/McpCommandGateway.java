package com.mine.geometry_node.client.agent.mcp;

import com.google.gson.JsonObject;
import com.mine.geometry_node.client.ai.command.CommandInvocationContext;
import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ai.command.CommandSpec;

import java.util.concurrent.CompletionStage;

/** Thread-bound adapter used by MCP without exposing a mutable graph to protocol threads. */
@FunctionalInterface
public interface McpCommandGateway {
    CompletionStage<CommandResult> execute(
            CommandSpec command,
            JsonObject arguments,
            CommandInvocationContext.CancellationToken cancellation);
}
