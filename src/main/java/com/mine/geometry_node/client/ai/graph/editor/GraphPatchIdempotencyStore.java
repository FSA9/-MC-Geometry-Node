package com.mine.geometry_node.client.ai.graph.editor;

import com.mine.geometry_node.client.ai.command.CommandResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Run-scoped GraphPatch idempotency state shared by every dynamically resolved viewport target. */
final class GraphPatchIdempotencyStore {
    static final int MAX_KEYS = 1_024;

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    synchronized Reservation reserve(String key, String patchHash) {
        Entry existing = entries.get(key);
        if (existing != null) {
            return new Reservation(existing, false, existing.patchHash.equals(patchHash), false);
        }
        if (entries.size() >= MAX_KEYS) {
            return new Reservation(null, false, false, true);
        }
        Entry created = new Entry(patchHash);
        entries.put(key, created);
        return new Reservation(created, true, true, false);
    }

    void complete(Entry entry, CommandResult result) {
        entry.complete(result);
    }

    record Reservation(Entry entry, boolean owner, boolean hashMatches, boolean limitReached) {}

    enum State {
        PENDING,
        PLANNING,
        COMMITTING,
        UNKNOWN,
        COMPLETED
    }

    static final class Entry {
        private final String patchHash;
        private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
        private final CompletableFuture<CommandResult> result = new CompletableFuture<>();

        private Entry(String patchHash) {
            this.patchHash = patchHash;
        }

        String patchHash() {
            return patchHash;
        }

        State state() {
            return state.get();
        }

        CompletableFuture<CommandResult> result() {
            return result;
        }

        void beginPlanning() {
            if (!state.compareAndSet(State.PENDING, State.PLANNING)) {
                throw new IllegalStateException("GraphPatch transaction is already owned");
            }
        }

        void beginCommit() {
            if (!state.compareAndSet(State.PLANNING, State.COMMITTING)) {
                throw new IllegalStateException("GraphPatch transaction cannot enter commit from " + state.get());
            }
        }

        void markUnknown() {
            state.compareAndSet(State.COMMITTING, State.UNKNOWN);
        }

        private void complete(CommandResult value) {
            if (result.complete(value)) state.set(State.COMPLETED);
        }
    }
}
