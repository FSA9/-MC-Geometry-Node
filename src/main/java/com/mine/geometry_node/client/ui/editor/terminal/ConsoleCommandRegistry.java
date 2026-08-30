package com.mine.geometry_node.client.ui.editor.terminal;

import com.mine.geometry_node.client.ai.command.CommandCatalog;
import com.mine.geometry_node.client.ai.command.CommandRegistry;
import com.mine.geometry_node.client.ui.editor.terminal.command.ConsoleCommandAdapter;
import com.mine.geometry_node.client.ui.document.GraphSession;

import java.util.List;

/** Compatibility facade for the terminal UI. Command definitions live in the shared registry. */
public final class ConsoleCommandRegistry {
    private static final CommandRegistry REGISTRY = CommandCatalog.registry();
    private static final ConsoleCommandAdapter ADAPTER = new ConsoleCommandAdapter(REGISTRY);

    private ConsoleCommandRegistry() {}

    public interface LogCallback extends ConsoleCommandAdapter.Output {}

    public static void executeLine(String line, GraphSession session, LogCallback logger) {
        ADAPTER.executeLine(line, session, logger);
    }

    public static List<CommandRegistry.Suggestion> suggestions(String input, GraphSession session) {
        return ADAPTER.suggestions(input, session);
    }

    public static CommandRegistry registry() { return REGISTRY; }
}
