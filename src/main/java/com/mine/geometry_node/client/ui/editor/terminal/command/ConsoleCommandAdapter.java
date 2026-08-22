package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.mine.geometry_node.client.ai.command.CliCommandParser;
import com.mine.geometry_node.client.ai.command.CommandInvocationContext;
import com.mine.geometry_node.client.ai.command.CommandRegistry;
import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ui.editor.terminal.ConsoleCommandRegistry;
import com.mine.geometry_node.client.ui.session.GraphSession;

import java.util.List;

/** Converts CLI text and structured results to the legacy terminal presentation API. */
public final class ConsoleCommandAdapter {
    private static final int SUCCESS_COLOR = 0xFF00AAFF;
    private static final int ERROR_COLOR = 0xFFFF4444;

    private final CommandRegistry registry;

    public ConsoleCommandAdapter(CommandRegistry registry) {
        this.registry = registry;
    }

    public void executeLine(String line, GraphSession session, ConsoleCommandRegistry.LogCallback logger) {
        if (line == null || line.trim().isEmpty()) return;
        CommandInvocationContext context = context(session);
        final CliCommandParser.ParsedInvocation invocation;
        try {
            invocation = CliCommandParser.parse(line, registry);
        } catch (CliCommandParser.ParseException exception) {
            String prefix = exception.code().equals("CLI_UNKNOWN_COMMAND") ? "语法错误: " : "";
            logger.onLog("[Error] " + prefix + exception.getMessage(), ERROR_COLOR);
            return;
        }

        CommandResult result = registry.execute(invocation.spec(), invocation.arguments(), context);
        if (result.clientAction() == CommandResult.ClientAction.CLEAR_OUTPUT) {
            logger.onClear();
            return;
        }
        if (!result.message().isEmpty()) {
            logger.onLog((result.ok() ? "[Success] " : "[Error] ") + result.message(),
                    result.ok() ? SUCCESS_COLOR : ERROR_COLOR);
        }
    }

    public List<CommandRegistry.Suggestion> suggestions(String input, GraphSession session) {
        return registry.suggest(input, context(session));
    }

    public CommandRegistry registry() { return registry; }

    private static CommandInvocationContext context(GraphSession session) {
        return new CommandInvocationContext(CommandInvocationContext.CommandOrigin.CLI,
                new TerminalCommandTarget(session), CommandInvocationContext.CancellationToken.NONE);
    }
}
