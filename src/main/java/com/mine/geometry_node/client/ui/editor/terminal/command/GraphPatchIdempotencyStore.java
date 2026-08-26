package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.mine.geometry_node.client.ai.command.CommandResult;

import java.util.LinkedHashMap;
import java.util.Map;

/** Run-scoped GraphPatch idempotency state shared by every dynamically resolved viewport target. */
final class GraphPatchIdempotencyStore {
    static final int MAX_KEYS = 1_024;

    private final Map<String, CompletedPatch> completed = new LinkedHashMap<>();

    CompletedPatch get(String key) {
        return completed.get(key);
    }

    int size() {
        return completed.size();
    }

    void put(String key, String patchHash, CommandResult result) {
        completed.put(key, new CompletedPatch(patchHash, result));
    }

    record CompletedPatch(String patchHash, CommandResult result) {}
}
