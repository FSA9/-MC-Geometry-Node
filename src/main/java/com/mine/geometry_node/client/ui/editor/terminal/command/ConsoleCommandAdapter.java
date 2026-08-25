package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.mine.geometry_node.client.ai.command.CliCommandParser;
import com.mine.geometry_node.client.ai.command.CommandInvocationContext;
import com.mine.geometry_node.client.ai.command.CommandRegistry;
import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ui.session.GraphSession;

import java.util.List;
import java.util.Objects;

/** Converts CLI text and structured results to the legacy terminal presentation API. */
public final class ConsoleCommandAdapter {
    private static final int SUCCESS_COLOR = 0xFF00AAFF;
    private static final int ERROR_COLOR = 0xFFFF4444;

    private final CommandRegistry registry;

    public interface Output {
        void onLog(String text, int color);
        void onClear();
    }

    public ConsoleCommandAdapter(CommandRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void executeLine(String line, GraphSession session, Output output) {
        if (line == null || line.trim().isEmpty()) return;
        Objects.requireNonNull(output, "output");
        CommandInvocationContext context = context(session);
        final CliCommandParser.ParsedInvocation invocation;
        try {
            invocation = CliCommandParser.parse(line, registry);
        } catch (CliCommandParser.ParseException exception) {
            String prefix = exception.code().equals("CLI_UNKNOWN_COMMAND") ? "语法错误: " : "";
            output.onLog("[Error] " + prefix + exception.getMessage(), ERROR_COLOR);
            return;
        }

        CommandResult result = registry.execute(invocation.spec(), invocation.arguments(), context);
        if (result.clientAction() == CommandResult.ClientAction.CLEAR_OUTPUT) {
            output.onClear();
            return;
        }
        if (!result.message().isEmpty()) {
            output.onLog((result.ok() ? "[Success] " : "[Error] ") + result.message(),
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
