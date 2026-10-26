package com.mine.geometry_node.client.integration.terminal;

import com.mine.geometry_node.client.feature.terminal.TerminalMode;

import java.util.UUID;

/** Creates the runtime owned by one terminal tab. */
@FunctionalInterface
public interface TerminalRuntimeFactory {
    TerminalRuntime create(UUID id, String title, TerminalMode mode, String profileId);
}
